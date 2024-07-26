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

import static org.eclipse.ditto.wot.validation.InternalValidation.determineDittoCategories;
import static org.eclipse.ditto.wot.validation.InternalValidation.determineDittoCategory;
import static org.eclipse.ditto.wot.validation.InternalValidation.enforceActionPayload;
import static org.eclipse.ditto.wot.validation.InternalValidation.enforceEventPayload;
import static org.eclipse.ditto.wot.validation.InternalValidation.enforcePresenceOfRequiredPropertiesUponDeletion;
import static org.eclipse.ditto.wot.validation.InternalValidation.ensureOnlyDefinedActions;
import static org.eclipse.ditto.wot.validation.InternalValidation.ensureOnlyDefinedEvents;
import static org.eclipse.ditto.wot.validation.InternalValidation.ensureOnlyDefinedProperties;
import static org.eclipse.ditto.wot.validation.InternalValidation.ensureRequiredProperties;
import static org.eclipse.ditto.wot.validation.InternalValidation.extractRequiredTmProperties;
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
import org.eclipse.ditto.wot.model.Properties;
import org.eclipse.ditto.wot.model.Property;
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
        final Set<String> categories = determineDittoCategories(featureThingModel);
        final boolean isCategoryUpdate = propertyPath.getLevelCount() == 1 &&
                categories.contains(propertyPath.getRoot().orElseThrow().toString());

        return featureThingModel.getProperties()
                .map(tdProperties -> {
                    final String containerNamePrefix = "Feature <" + featureId + ">'s " +
                            (desiredProperty ? "desired " : "");
                    final String containerNamePlural = containerNamePrefix + "properties";

                    final CompletableFuture<Void> ensureOnlyDefinedPropertiesStage =
                            enforceFeaturePropertyOnlyDefinedProperties(
                                    featureThingModel, propertyPath, propertyValue, forbidNonModeledProperties, context,
                                    tdProperties, isCategoryUpdate, containerNamePlural
                            );

                    final CompletableFuture<Void> validatePropertiesStage =
                            enforceFeaturePropertyValidateProperties(
                                    featureThingModel, propertyPath, propertyValue, desiredProperty, resourcePath,
                                    context, tdProperties, isCategoryUpdate, containerNamePrefix
                            );

                    return CompletableFuture.allOf(
                            ensureOnlyDefinedPropertiesStage,
                            validatePropertiesStage
                    );
                }).orElseGet(InternalValidation::success);
    }

    private static CompletableFuture<Void> enforceFeaturePropertyOnlyDefinedProperties(
            final ThingModel featureThingModel,
            final JsonPointer propertyPath,
            final JsonValue propertyValue,
            final boolean forbidNonModeledProperties,
            final ValidationContext context,
            final Properties tdProperties,
            final boolean isCategoryUpdate,
            final String containerNamePlural
    ) {
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
                        .setAll(propertyValue.isObject() ? propertyValue.asObject() : JsonObject.empty())
                        .build();
                return ensureOnlyDefinedProperties(featureThingModel,
                        propertiesInCategory,
                        featureProperties,
                        containerNamePlural,
                        false,
                        context
                );
            }
        } else {
            if (forbidNonModeledProperties) {
                final FeatureProperties featureProperties = FeatureProperties.newBuilder()
                        .set(propertyPath, propertyValue)
                        .build();
                return ensureOnlyDefinedProperties(featureThingModel,
                        tdProperties,
                        featureProperties,
                        containerNamePlural,
                        true,
                        context
                );
            }
        }
        return success();
    }

    private static CompletableFuture<Void> enforceFeaturePropertyValidateProperties(final ThingModel featureThingModel,
            final JsonPointer propertyPath,
            final JsonValue propertyValue,
            final boolean desiredProperty,
            final JsonPointer resourcePath,
            final ValidationContext context,
            final Properties tdProperties,
            final boolean isCategoryUpdate,
            final String containerNamePrefix
    ) {
        if (isCategoryUpdate) {
            final String dittoCategory = propertyPath.getRoot().orElseThrow().toString();
            if (!propertyValue.isObject()) {
                final WotThingModelPayloadValidationException.Builder exceptionBuilder =
                        WotThingModelPayloadValidationException
                                .newBuilder("Could not update Feature property category " +
                                        "<" + dittoCategory + "> as its value was not a JSON object");
                return CompletableFuture.failedFuture(exceptionBuilder
                        .dittoHeaders(context.dittoHeaders())
                        .build());
            }

            final List<Property> sameCategoryProperties = tdProperties.values().stream()
                    .filter(property ->
                            // gather all properties from the same category
                            determineDittoCategory(featureThingModel, property)
                                    .filter(cat -> cat.equals(dittoCategory))
                                    .isPresent()
                    )
                    .toList();

            if (!sameCategoryProperties.isEmpty() && propertyValue.isObject()) {
                return validatePropertyCategory(featureThingModel,
                        Properties.from(sameCategoryProperties),
                        propertyPath,
                        !desiredProperty,
                        propertyValue.asObject(),
                        containerNamePrefix + "property",
                        resourcePath,
                        context
                );
            } else {
                return validateProperty(featureThingModel,
                        tdProperties,
                        propertyPath,
                        !desiredProperty,
                        propertyValue,
                        containerNamePrefix + "property <" + propertyPath + ">",
                        resourcePath,
                        true,
                        determineDittoCategories(featureThingModel),
                        context
                );
            }
        } else {
            return validateProperty(featureThingModel,
                    tdProperties,
                    propertyPath,
                    !desiredProperty,
                    propertyValue,
                    containerNamePrefix + "property <" + propertyPath + ">",
                    resourcePath,
                    true,
                    determineDittoCategories(featureThingModel),
                    context
            );
        }
    }

    static CompletableFuture<Void> enforcePresenceOfRequiredPropertiesUponFeatureLevelDeletion(
            final ThingModel featureThingModel,
            final String featureId,
            final JsonPointer resourcePath,
            final ValidationContext context
    ) {
        final JsonPointer propertiesPath =
                resourcePath.getSubPointer(2).orElse(resourcePath); // cut /features/<featureId>

        final CompletableFuture<Void> firstStage;
        if (propertiesPath.getLevelCount() > 1) {
            firstStage = enforcePresenceOfRequiredPropertiesUponPropertyCategoryDeletion(featureThingModel,
                    featureId,
                    context,
                    propertiesPath
            );
        } else {
            firstStage = success();
        }

        return firstStage.thenCompose(unused ->
                enforcePresenceOfRequiredPropertiesUponDeletion(
                        featureThingModel,
                        propertiesPath,
                        true,
                        determineDittoCategories(featureThingModel),
                        "all Feature <" + featureId + "> properties",
                        "Feature <" + featureId + "> property",
                        context
                )
        );
    }

    private static CompletableFuture<Void> enforcePresenceOfRequiredPropertiesUponPropertyCategoryDeletion(
            final ThingModel featureThingModel,
            final String featureId,
            final ValidationContext context,
            final JsonPointer propertiesPath
    ) {
        final Set<String> categories = determineDittoCategories(featureThingModel);

        final String potentialCategory = propertiesPath.get(1).orElseThrow().toString();
        final boolean isCategoryUpdate = propertiesPath.getLevelCount() == 2 && categories.contains(potentialCategory);
        if (isCategoryUpdate) {
            // handle deleting whole category like "/properties/status" - must not be allowed if it contains required properties
            final boolean containsRequiredProperties = featureThingModel.getProperties()
                    .map(properties -> Properties.from(properties.values().stream()
                                    .filter(property -> determineDittoCategory(featureThingModel, property)
                                            .filter(potentialCategory::equals)
                                            .isPresent()
                                    )
                                    .toList()
                            )
                    )
                    .map(properties -> extractRequiredTmProperties(properties, featureThingModel))
                    .map(map -> !map.isEmpty())
                    .orElse(false);
            if (containsRequiredProperties) {
                final WotThingModelPayloadValidationException.Builder exceptionBuilder =
                        WotThingModelPayloadValidationException
                                .newBuilder("Could not delete Feature <" + featureId + "> properties " +
                                        "category <" + potentialCategory + "> as it contains non-optional properties");
                return CompletableFuture.failedFuture(exceptionBuilder
                        .dittoHeaders(context.dittoHeaders())
                        .build());
            }
        }
        return success();
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
                            resourcePath.addLeaf(JsonKey.of(rpKey)),
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
