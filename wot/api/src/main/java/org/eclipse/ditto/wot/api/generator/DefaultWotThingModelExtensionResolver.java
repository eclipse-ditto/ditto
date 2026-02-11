/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.wot.api.generator;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.wot.api.provider.WotThingModelFetcher;
import org.eclipse.ditto.wot.model.BaseLink;
import org.eclipse.ditto.wot.model.IRI;
import org.eclipse.ditto.wot.model.Links;
import org.eclipse.ditto.wot.model.ThingModel;
import org.eclipse.ditto.wot.model.WotThingModelRefInvalidException;

/**
 * Default implementation of {@link WotThingModelExtensionResolver} which should be not Ditto specific.
 */
final class DefaultWotThingModelExtensionResolver implements WotThingModelExtensionResolver {

    private static final String TM_EXTENDS = "tm:extends";
    private static final String TM_REF = "tm:ref";

    private final WotThingModelFetcher thingModelFetcher;
    private final Executor executor;

    DefaultWotThingModelExtensionResolver(final WotThingModelFetcher thingModelFetcher, final Executor executor) {
        this.thingModelFetcher = checkNotNull(thingModelFetcher, "thingModelFetcher");
        this.executor = executor;
    }

    @Override
    public CompletionStage<ThingModel> resolveThingModelExtensions(final ThingModel thingModel,
            final DittoHeaders dittoHeaders) {
        final ThingModel.Builder tmBuilder = thingModel.toBuilder();
        return thingModel.getLinks()
                .map(links -> {
                            final List<CompletableFuture<ThingModel>> fetchedModelFutures = links.stream()
                                    .filter(baseLink -> baseLink.getRel().filter(TM_EXTENDS::equals).isPresent())
                                    .map(extendsLink -> thingModelFetcher.fetchThingModel(extendsLink.getHref(), dittoHeaders))
                                    .map(CompletionStage::toCompletableFuture)
                                    .toList();
                            final CompletionStage<Void> allModelFuture =
                                    CompletableFuture.allOf(fetchedModelFutures.toArray(new CompletableFuture[0]));
                            return allModelFuture
                                    .thenApplyAsync(aVoid -> fetchedModelFutures.stream()
                                            .map(CompletableFuture::join) // joining does not block anything here as "allOf" already guaranteed that all futures are ready
                                            .toList(), executor
                                    );
                        }
                )
                .map(extendedModelsFut -> extendedModelsFut.thenComposeAsync(extendedModels -> {
                    if (extendedModels.isEmpty()) {
                        return CompletableFuture.completedFuture(thingModel);
                    } else {
                        CompletionStage<ThingModel.Builder> currentStage =
                                resolveThingModelExtensions(extendedModels.getFirst(), dittoHeaders) // recurse!
                                        .thenApplyAsync(extendedModel ->
                                                mergeThingModelIntoBuilder().apply(tmBuilder, extendedModel), executor
                                        );
                        for (int i = 1; i < extendedModels.size(); i++) {
                            currentStage = currentStage.thenCombine(
                                    resolveThingModelExtensions(extendedModels.get(i), dittoHeaders),  // recurse!
                                    mergeThingModelIntoBuilder()
                            );
                        }
                        return currentStage.thenApplyAsync(ThingModel.Builder::build, executor);
                    }
                }, executor))
                .orElse(CompletableFuture.completedFuture(thingModel));
    }

    private BiFunction<ThingModel.Builder, ThingModel, ThingModel.Builder> mergeThingModelIntoBuilder() {
        return (builder, model) -> {
            final ThingModel newModel = builder.build();
            final JsonObject mergedTmObject = JsonFactory.mergeJsonValues(newModel, model).asObject();
            builder.removeAll();
            builder.setAll(mergedTmObject);
            mergeLinks(model.getLinks(), newModel.getLinks()).ifPresent(builder::setLinks);
            return builder;
        };
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private static Optional<Links> mergeLinks(final Optional<Links> oldOptionalLinks,
            final Optional<Links> newOptionalLinks)
    {
        return oldOptionalLinks
                .map(oldLinks -> filterOutTmExtendsLinkFromOldLinks(newOptionalLinks, oldLinks))
                .map(adjustedOldLinks ->
                        newOptionalLinks.stream()
                                .flatMap(newLinks -> Stream.concat(adjustedOldLinks, newLinks.stream()))
                )
                .map(Stream::toList)
                .map(Links::of);
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private static Stream<BaseLink<?>> filterOutTmExtendsLinkFromOldLinks(final Optional<Links> newOptionalLinks,
            final Links oldLinks)
    {
        return oldLinks.stream()
                .filter(oldLink -> {
                    if (isTmExtendsLink(oldLink)) {
                        return newOptionalLinks.filter(DefaultWotThingModelExtensionResolver::containsTmExtendsLink)
                                .isEmpty();
                    } else {
                        return true;
                    }
                });
    }

    private static boolean containsTmExtendsLink(final Links links) {
        return links.stream().anyMatch(DefaultWotThingModelExtensionResolver::isTmExtendsLink);
    }

    private static boolean isTmExtendsLink(final BaseLink<?> link) {
        return link.getRel().isPresent() && link.getRel().filter(TM_EXTENDS::equals).isPresent();
    }

    @Override
    public CompletionStage<ThingModel> resolveThingModelRefs(final ThingModel thingModel, final DittoHeaders dittoHeaders) {
        // Pass thingModel as the "rootThingModel" for local reference resolution
        return potentiallyResolveRefs(thingModel, thingModel, Set.of(), dittoHeaders)
                .thenApplyAsync(ThingModel::fromJson, executor);
    }

    private CompletionStage<JsonObject> potentiallyResolveRefs(final JsonObject jsonObject,
            final ThingModel rootThingModel,
            final Set<String> visitedLocalPointers,
            final DittoHeaders dittoHeaders) {
        final List<CompletableFuture<JsonField>> completionStages = jsonObject.stream()
                .map(field -> {
                    if (field.getValue().isObject() && field.getValue().asObject().contains(TM_REF)) {
                        return resolveRefs(field.getValue().asObject(), rootThingModel, visitedLocalPointers, dittoHeaders)
                                .thenApplyAsync(refs -> JsonField.newInstance(field.getKey(), refs),
                                        executor);
                    } else if (field.getValue().isObject()) {
                        return potentiallyResolveRefs(field.getValue().asObject(), rootThingModel, visitedLocalPointers, dittoHeaders)  // recurse!
                                .thenApplyAsync(refs -> JsonField.newInstance(field.getKey(), refs), executor);
                    } else {
                        return CompletableFuture.completedFuture(field);
                    }
                })
                .map(CompletionStage::toCompletableFuture)
                .toList();
        return CompletableFuture.allOf(completionStages.toArray(CompletableFuture[]::new))
                .thenApplyAsync(v -> completionStages.stream()
                                .map(CompletableFuture::join)
                                .collect(JsonCollectors.fieldsToObject()),
                        executor
                );
    }

    private CompletionStage<JsonValue> resolveRefs(final JsonObject objectWithTmRef,
            final ThingModel rootThingModel,
            final Set<String> visitedLocalPointers,
            final DittoHeaders dittoHeaders) {
        final String tmRef = objectWithTmRef.getValue(TM_REF)
                .filter(JsonValue::isString)
                .map(JsonValue::asString)
                .orElseThrow();

        // Check if this is a local reference (starts with #/)
        if (tmRef.startsWith("#/")) {
            // Local reference - resolve within the rootThingModel
            final String jsonPointer = tmRef.substring(1); // Remove leading #
            return resolveLocalRef(objectWithTmRef, rootThingModel, jsonPointer, visitedLocalPointers, dittoHeaders);
        }

        // External reference
        final String[] urlAndPointer = tmRef.split("#/", 2);
        if (urlAndPointer.length != 2) {
            throw WotThingModelRefInvalidException.newBuilder(tmRef).dittoHeaders(dittoHeaders).build();
        }
        return thingModelFetcher.fetchThingModel(IRI.of(urlAndPointer[0]), dittoHeaders)
                .thenApplyAsync(thingModel -> thingModel.getValue(JsonPointer.of(urlAndPointer[1])), executor)
                .thenComposeAsync(optJsonValue -> optJsonValue
                                .filter(JsonValue::isObject)
                                .map(JsonValue::asObject)
                                .map(CompletableFuture::completedStage)
                                .orElseGet(() -> CompletableFuture.completedFuture(null))
                        , executor
                )
                .thenComposeAsync(refObject -> {
                    if (refObject.contains(TM_REF)) {
                        return resolveRefs(refObject, rootThingModel, visitedLocalPointers, dittoHeaders) // recurse!
                                .thenApplyAsync(JsonValue::asObject, executor);
                    } else {
                        return potentiallyResolveRefs(refObject, rootThingModel, visitedLocalPointers, dittoHeaders); // recurse!
                    }
                }, executor)
                .thenApplyAsync(refObject -> JsonFactory.mergeJsonValues(objectWithTmRef.remove(TM_REF), refObject).asObject(), executor)
                .thenComposeAsync(mergedResult -> {
                    // Recursively resolve any remaining tm:ref references in the merged result
                    return potentiallyResolveRefs(mergedResult, rootThingModel, visitedLocalPointers, dittoHeaders)
                            .thenApplyAsync(JsonValue::asObject, executor);
                }, executor);
    }

    private CompletionStage<JsonValue> resolveLocalRef(final JsonObject objectWithTmRef,
            final ThingModel rootThingModel,
            final String jsonPointer,
            final Set<String> visitedLocalPointers,
            final DittoHeaders dittoHeaders) {

        // Check for circular reference
        if (visitedLocalPointers.contains(jsonPointer)) {
            throw WotThingModelRefInvalidException.newBuilder("#" + jsonPointer)
                    .dittoHeaders(dittoHeaders)
                    .description("Circular reference detected in local tm:ref.")
                    .build();
        }

        final Optional<JsonValue> optRefValue = rootThingModel.getValue(JsonPointer.of(jsonPointer));

        if (optRefValue.isEmpty()) {
            throw WotThingModelRefInvalidException.newBuilder("#" + jsonPointer)
                    .dittoHeaders(dittoHeaders)
                    .description("The local JSON pointer did not resolve to a value in the ThingModel.")
                    .build();
        }

        final JsonValue refValue = optRefValue.get();

        // Track visited pointer for circular reference detection
        final Set<String> newVisitedPointers = new HashSet<>(visitedLocalPointers);
        newVisitedPointers.add(jsonPointer);

        if (!refValue.isObject()) {
            // For non-object values, merge directly
            return CompletableFuture.completedFuture(
                    JsonFactory.mergeJsonValues(objectWithTmRef.remove(TM_REF), refValue));
        }

        final JsonObject refObject = refValue.asObject();

        // Check for nested tm:ref in the resolved value
        if (refObject.contains(TM_REF)) {
            return resolveRefs(refObject, rootThingModel, newVisitedPointers, dittoHeaders)
                    .thenApplyAsync(resolved ->
                            JsonFactory.mergeJsonValues(objectWithTmRef.remove(TM_REF), resolved).asObject(),
                            executor);
        }

        // Recursively resolve any nested references
        return potentiallyResolveRefs(refObject, rootThingModel, newVisitedPointers, dittoHeaders)
                .thenApplyAsync(resolved ->
                        JsonFactory.mergeJsonValues(objectWithTmRef.remove(TM_REF), resolved).asObject(),
                        executor);
    }
}
