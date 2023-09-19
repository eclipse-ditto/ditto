/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.wot.integration.generator;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.wot.integration.provider.WotThingModelFetcher;
import org.eclipse.ditto.wot.model.IRI;
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

    DefaultWotThingModelExtensionResolver(final WotThingModelFetcher thingModelFetcher,
            final Executor executor) {
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
                                resolveThingModelExtensions(extendedModels.get(0), dittoHeaders) // recurse!
                                        .thenApply(extendedModel ->
                                                mergeThingModelIntoBuilder().apply(tmBuilder, extendedModel)
                                        );
                        for (int i = 1; i < extendedModels.size(); i++) {
                            currentStage = currentStage.thenCombine(
                                    resolveThingModelExtensions(extendedModels.get(i), dittoHeaders),  // recurse!
                                    mergeThingModelIntoBuilder()
                            );
                        }
                        return currentStage.thenApply(ThingModel.Builder::build);
                    }
                }, executor))
                .orElse(CompletableFuture.completedFuture(thingModel));
    }

    private BiFunction<ThingModel.Builder, ThingModel, ThingModel.Builder> mergeThingModelIntoBuilder() {
        return (builder, model) -> {
            final JsonObject mergedTmObject = JsonFactory.mergeJsonValues(builder.build(), model).asObject();
            builder.removeAll();
            builder.setAll(mergedTmObject);
            return builder;
        };
    }

    @Override
    public CompletionStage<ThingModel> resolveThingModelRefs(final ThingModel thingModel, final DittoHeaders dittoHeaders) {
        return potentiallyResolveRefs(thingModel, dittoHeaders).thenApply(ThingModel::fromJson);
    }

    private CompletionStage<JsonObject> potentiallyResolveRefs(final JsonObject jsonObject,
            final DittoHeaders dittoHeaders) {
        final List<CompletableFuture<JsonField>> completionStages = jsonObject.stream()
                .map(field -> {
                    if (field.getValue().isObject() && field.getValue().asObject().contains(TM_REF)) {
                        return resolveRefs(field.getValue().asObject(), dittoHeaders)
                                .thenApply(refs -> JsonField.newInstance(field.getKey(), refs));
                    } else if (field.getValue().isObject()) {
                        return potentiallyResolveRefs(field.getValue().asObject(), dittoHeaders)  // recurse!
                                .thenApply(refs -> JsonField.newInstance(field.getKey(), refs));
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

    private CompletionStage<JsonValue> resolveRefs(final JsonObject objectWithTmRef, final DittoHeaders dittoHeaders) {
        final String tmRef = objectWithTmRef.getValue(TM_REF)
                .filter(JsonValue::isString)
                .map(JsonValue::asString)
                .orElseThrow();

        final String[] urlAndPointer = tmRef.split("#/", 2);
        if (urlAndPointer.length != 2) {
            throw WotThingModelRefInvalidException.newBuilder(tmRef).dittoHeaders(dittoHeaders).build();
        }
        return thingModelFetcher.fetchThingModel(IRI.of(urlAndPointer[0]), dittoHeaders)
                .thenApply(thingModel -> thingModel.getValue(JsonPointer.of(urlAndPointer[1])))
                .thenComposeAsync(optJsonValue -> optJsonValue
                                .filter(JsonValue::isObject)
                                .map(JsonValue::asObject)
                                .map(CompletableFuture::completedStage)
                                .orElseGet(() -> CompletableFuture.completedFuture(null))
                        , executor
                )
                .thenApply(refObject ->
                        JsonFactory.mergeJsonValues(objectWithTmRef.remove(TM_REF), refObject).asObject()
                );
    }
}
