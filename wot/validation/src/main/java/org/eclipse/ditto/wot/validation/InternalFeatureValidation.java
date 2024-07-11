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
package org.eclipse.ditto.wot.validation;

import static org.eclipse.ditto.wot.validation.InternalValidation.determineDittoCategory;
import static org.eclipse.ditto.wot.validation.InternalValidation.enforceActionPayload;
import static org.eclipse.ditto.wot.validation.InternalValidation.enforceEventPayload;
import static org.eclipse.ditto.wot.validation.InternalValidation.ensureOnlyDefinedActions;
import static org.eclipse.ditto.wot.validation.InternalValidation.ensureOnlyDefinedEvents;
import static org.eclipse.ditto.wot.validation.InternalValidation.ensureOnlyDefinedProperties;
import static org.eclipse.ditto.wot.validation.InternalValidation.ensureRequiredProperties;
import static org.eclipse.ditto.wot.validation.InternalValidation.filterNonProvidedRequiredProperties;
import static org.eclipse.ditto.wot.validation.InternalValidation.success;
import static org.eclipse.ditto.wot.validation.InternalValidation.validateProperties;
import static org.eclipse.ditto.wot.validation.InternalValidation.validateProperty;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.FeatureProperties;
import org.eclipse.ditto.things.model.Features;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.wot.model.DittoWotExtension;
import org.eclipse.ditto.wot.model.Properties;
import org.eclipse.ditto.wot.model.Property;
import org.eclipse.ditto.wot.model.SingleUriAtContext;
import org.eclipse.ditto.wot.model.ThingModel;

final class InternalFeatureValidation {

    private InternalFeatureValidation() {
        throw new AssertionError();
    }

    static CompletableFuture<Void> forbidNonModeledFeatures(@Nullable final Features features,
            final Set<String> definedFeatureIds,
            final ValidationContext context
    ) {

        final Set<String> extraFeatureIds = Optional.ofNullable(features)
                .map(Features::stream)
                .orElseGet(Stream::empty)
                .map(Feature::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        extraFeatureIds.removeAll(definedFeatureIds);
        if (!extraFeatureIds.isEmpty()) {
            final var exceptionBuilder = WotThingModelPayloadValidationException
                    .newBuilder("Attempting to update the Thing with feature(s) are were not " +
                            "defined in the model: " + extraFeatureIds);
            return CompletableFuture.failedFuture(exceptionBuilder
                    .dittoHeaders(context.dittoHeaders())
                    .build());
        }
        return success();
    }

    static CompletableFuture<Void> enforcePresenceOfModeledFeatures(@Nullable final Features features,
            final Set<String> definedFeatureIds,
            final ValidationContext context
    ) {
        final Set<String> existingFeatures = Optional.ofNullable(features)
                .map(Features::stream)
                .orElseGet(Stream::empty)
                .map(Feature::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (!existingFeatures.containsAll(definedFeatureIds)) {
            final Set<String> missingFeatureIds = new LinkedHashSet<>(definedFeatureIds);
            missingFeatureIds.removeAll(existingFeatures);
            final var exceptionBuilder = WotThingModelPayloadValidationException
                    .newBuilder("Attempting to update the Thing with missing in the model " +
                            "defined features: " + missingFeatureIds);
            return CompletableFuture.failedFuture(exceptionBuilder
                    .dittoHeaders(context.dittoHeaders())
                    .build());
        }
        return success();
    }

    static CompletableFuture<List<Void>> enforceFeaturePropertiesInAllSubmodels(
            final Map<String, ThingModel> featureThingModels,
            final Features features,
            final boolean desiredProperties,
            final boolean forbidNonModeledProperties,
            final JsonPointer resourcePath,
            final ValidationContext context
    ) {
        final CompletableFuture<List<Void>> enforcedPropertiesListFuture;
        final List<CompletableFuture<Void>> enforcedPropertiesFutures = featureThingModels
                .entrySet()
                .stream()
                .filter(entry -> features.getFeature(entry.getKey()).isPresent())
                .map(entry ->
                        enforceFeatureProperties(entry.getValue(),
                                features.getFeature(entry.getKey()).orElseThrow(),
                                desiredProperties,
                                forbidNonModeledProperties,
                                resourcePath.append(Thing.JsonFields.FEATURES.getPointer())
                                        .addLeaf(JsonKey.of(entry.getKey()))
                                        .append(desiredProperties ?
                                                Feature.JsonFields.DESIRED_PROPERTIES.getPointer() :
                                                Feature.JsonFields.PROPERTIES.getPointer()
                                        ),
                                context
                        )
                )
                .toList();
        enforcedPropertiesListFuture =
                CompletableFuture.allOf(enforcedPropertiesFutures.toArray(new CompletableFuture[0]))
                        .thenApply(ignored -> enforcedPropertiesFutures.stream()
                                .map(CompletableFuture::join)
                                .toList()
                        );
        return enforcedPropertiesListFuture;
    }

    static CompletableFuture<Void> enforceFeatureProperties(final ThingModel featureThingModel,
            final Feature feature,
            final boolean desiredProperties,
            final boolean forbidNonModeledProperties,
            final JsonPointer resourcePath,
            final ValidationContext context
    ) {
        return featureThingModel.getProperties()
                .map(tdProperties -> {
                    final FeatureProperties featureProperties;
                    if (desiredProperties) {
                        featureProperties = feature.getDesiredProperties()
                                .orElseGet(() -> FeatureProperties.newBuilder().build());
                    } else {
                        featureProperties = feature.getProperties()
                                .orElseGet(() -> FeatureProperties.newBuilder().build());
                    }

                    final String containerNamePrefix = "Feature <" + feature.getId() + ">'s " +
                            (desiredProperties ? "desired " : "");
                    final String containerNamePlural = containerNamePrefix + "properties";

                    final CompletableFuture<Void> ensureRequiredPropertiesStage;
                    if (!desiredProperties) {
                        ensureRequiredPropertiesStage = ensureRequiredProperties(featureThingModel,
                                tdProperties,
                                featureProperties,
                                containerNamePlural,
                                containerNamePrefix + "property",
                                resourcePath,
                                true,
                                context
                        );
                    } else {
                        ensureRequiredPropertiesStage = success();
                    }

                    final CompletableFuture<Void> ensureOnlyDefinedPropertiesStage;
                    if (forbidNonModeledProperties) {
                        ensureOnlyDefinedPropertiesStage = ensureOnlyDefinedProperties(featureThingModel,
                                tdProperties,
                                featureProperties,
                                containerNamePlural,
                                true,
                                context
                        );
                    } else {
                        ensureOnlyDefinedPropertiesStage = success();
                    }

                    final CompletableFuture<Void> validatePropertiesStage = validateProperties(featureThingModel,
                            tdProperties,
                            featureProperties,
                            !desiredProperties,
                            containerNamePlural,
                            resourcePath,
                            true,
                            context
                    );

                    return CompletableFuture.allOf(
                            ensureRequiredPropertiesStage,
                            ensureOnlyDefinedPropertiesStage,
                            validatePropertiesStage
                    );
                }).orElseGet(InternalValidation::success);
    }

    static CompletableFuture<Void> enforceFeatureProperty(final ThingModel featureThingModel,
            final String featureId,
            final JsonPointer propertyPath,
            final JsonValue propertyValue,
            final boolean desiredProperty,
            final boolean forbidNonModeledProperties,
            final JsonPointer resourcePath,
            final ValidationContext context
    ) {
        // TODO TJ split?!
        final Set<String> categories = determineDittoCategories(featureThingModel,
                featureThingModel.getProperties().orElse(Properties.of(Map.of()))
        );
        final boolean isCategoryUpdate = propertyPath.getLevelCount() == 1 &&
                categories.contains(propertyPath.getRoot().orElseThrow().toString());

        return featureThingModel.getProperties()
                .map(tdProperties -> {
                    final String containerNamePrefix = "Feature <" + featureId + ">'s " +
                            (desiredProperty ? "desired " : "");
                    final String containerNamePlural = containerNamePrefix + "properties";

                    final CompletableFuture<Void> ensureOnlyDefinedPropertiesStage;
                    if (isCategoryUpdate) {
                        final String dittoCategory = propertyPath.getRoot().orElseThrow().toString();
                        if (forbidNonModeledProperties) {
                            final Properties propertiesInCategory = Properties.from(tdProperties.values().stream()
                                    .filter(property ->
                                            determineDittoCategory(featureThingModel, property)
                                                    .filter(cat -> cat.equals(dittoCategory))
                                                    .isPresent()
                                    )
                                    .toList());
                            final FeatureProperties featureProperties = FeatureProperties.newBuilder()
                                    .setAll(propertyValue.asObject())
                                    .build();
                            ensureOnlyDefinedPropertiesStage = ensureOnlyDefinedProperties(featureThingModel,
                                    propertiesInCategory,
                                    featureProperties,
                                    containerNamePlural,
                                    false,
                                    context
                            );
                        } else {
                            ensureOnlyDefinedPropertiesStage = success();
                        }
                    } else {
                        if (forbidNonModeledProperties) {
                            final FeatureProperties featureProperties = FeatureProperties.newBuilder()
                                    .set(propertyPath, propertyValue)
                                    .build();
                            ensureOnlyDefinedPropertiesStage = ensureOnlyDefinedProperties(featureThingModel,
                                    tdProperties,
                                    featureProperties,
                                    containerNamePlural,
                                    true,
                                    context
                            );
                        } else {
                            ensureOnlyDefinedPropertiesStage = success();
                        }
                    }

                    final CompletableFuture<Void> validatePropertiesStage;
                    if (isCategoryUpdate) {
                        final String dittoCategory = propertyPath.getRoot().orElseThrow().toString();
                        final List<Property> sameCategoryProperties = tdProperties.values().stream()
                                .filter(property ->
                                        // gather all properties from the same category
                                        determineDittoCategory(featureThingModel, property)
                                                .filter(cat -> cat.equals(dittoCategory))
                                                .isPresent()
                                )
                                .toList();

                        if (!sameCategoryProperties.isEmpty() && propertyValue.isObject()) {
                            validatePropertiesStage = validatePropertyCategory(featureThingModel,
                                    Properties.from(sameCategoryProperties),
                                    propertyPath,
                                    !desiredProperty,
                                    propertyValue.asObject(),
                                    containerNamePrefix + "property",
                                    resourcePath,
                                    context
                            );
                        } else {
                            validatePropertiesStage = validateProperty(featureThingModel,
                                    tdProperties,
                                    propertyPath,
                                    !desiredProperty,
                                    propertyValue,
                                    containerNamePrefix + "property <" + propertyPath + ">",
                                    resourcePath,
                                    true,
                                    context
                            );
                        }
                    } else {
                        validatePropertiesStage = validateProperty(featureThingModel,
                                tdProperties,
                                propertyPath,
                                !desiredProperty,
                                propertyValue,
                                containerNamePrefix + "property <" + propertyPath + ">",
                                resourcePath,
                                true,
                                context
                        );
                    }

                    return CompletableFuture.allOf(
                            ensureOnlyDefinedPropertiesStage,
                            validatePropertiesStage
                    );
                }).orElseGet(InternalValidation::success);
    }

    private static Set<String> determineDittoCategories(final ThingModel thingModel, final Properties properties) {
        final Optional<String> dittoExtensionPrefix = thingModel.getAtContext()
                .determinePrefixFor(SingleUriAtContext.DITTO_WOT_EXTENSION);
        return dittoExtensionPrefix.stream().flatMap(prefix ->
                properties.values().stream().flatMap(jsonFields ->
                        jsonFields.getValue(prefix + ":" + DittoWotExtension.DITTO_WOT_EXTENSION_CATEGORY)
                                .filter(JsonValue::isString)
                                .map(JsonValue::asString)
                                .stream()
                )
        ).collect(Collectors.toSet());
    }

    static CompletableFuture<Void> enforceFeatureActionPayload(final String featureId,
            final ThingModel featureThingModel,
            final String messageSubject,
            @Nullable final JsonValue inputPayload,
            final boolean forbidNonModeledInboxMessages,
            final JsonPointer resourcePath,
            final boolean isInput,
            final ValidationContext context
    ) {
        final CompletableFuture<Void> firstStage;
        if (forbidNonModeledInboxMessages) {
            firstStage = ensureOnlyDefinedActions(featureThingModel.getActions().orElse(null),
                    messageSubject,
                    "Feature <" + featureId + ">'s",
                    context
            );
        } else {
            firstStage = success();
        }
        return firstStage.thenCompose(unused ->
                enforceActionPayload(featureThingModel, messageSubject, inputPayload, resourcePath, isInput,
                        "Feature <" + featureId + ">'s action <" + messageSubject + "> " +
                                (isInput ? "input" : "output"),
                        context
                )
        );
    }

    static CompletableFuture<Void> enforceFeatureEventPayload(final String featureId,
            final ThingModel featureThingModel,
            final String messageSubject,
            @Nullable final JsonValue payload,
            final boolean forbidNonModeledOutboxMessages,
            final JsonPointer resourcePath,
            final ValidationContext context
    ) {
        final CompletableFuture<Void> firstStage;
        if (forbidNonModeledOutboxMessages) {
            firstStage = ensureOnlyDefinedEvents(
                    featureThingModel.getEvents().orElse(null),
                    messageSubject,
                    "Feature <" + featureId + ">'s",
                    context
            );
        } else {
            firstStage = success();
        }
        return firstStage.thenCompose(unused ->
                enforceEventPayload(featureThingModel, messageSubject, payload, resourcePath,
                        "Feature <" + featureId + ">'s event <" + messageSubject + "> data",
                        context
                )
        );
    }

    static CompletableFuture<Void> validatePropertyCategory(final ThingModel featureThingModel,
            final Properties categoryProperties,
            final JsonPointer propertyPath,
            final boolean validateRequiredObjectFields,
            final JsonObject categoryObject,
            final String propertyDescription,
            final JsonPointer resourcePath,
            final ValidationContext context
    ) {
        final Map<String, Property> nonProvidedRequiredProperties =
                filterNonProvidedRequiredProperties(categoryProperties, featureThingModel, categoryObject, false);
        final JsonKey category = propertyPath.getRoot().orElseThrow();
        final String propertyCategoryDescription = propertyDescription + " category <" + category + ">";
        if (validateRequiredObjectFields && !nonProvidedRequiredProperties.isEmpty()) {
            final var exceptionBuilder = WotThingModelPayloadValidationException
                    .newBuilder("Required JSON fields were missing from the " + propertyCategoryDescription);
            nonProvidedRequiredProperties.forEach((rpKey, requiredProperty) ->
                    exceptionBuilder.addValidationDetail(
                            propertyPath.addLeaf(JsonKey.of(rpKey)),
                            List.of(propertyDescription + " category <" + category +
                                    ">'s <" + rpKey + "> is non optional and must be provided")
                    )
            );
            return CompletableFuture
                    .failedFuture(exceptionBuilder.dittoHeaders(context.dittoHeaders()).build());
        }

        return validateProperties(
                featureThingModel,
                categoryProperties,
                categoryObject,
                validateRequiredObjectFields,
                propertyCategoryDescription,
                resourcePath,
                false,
                context
        );
    }

}
