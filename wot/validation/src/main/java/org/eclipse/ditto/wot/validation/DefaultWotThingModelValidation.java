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

import java.util.AbstractMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.Attributes;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.FeatureProperties;
import org.eclipse.ditto.things.model.Features;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.wot.model.Properties;
import org.eclipse.ditto.wot.model.Property;
import org.eclipse.ditto.wot.model.ThingModel;
import org.eclipse.ditto.wot.model.TmOptionalElement;
import org.eclipse.ditto.wot.validation.config.TmValidationConfig;

import com.networknt.schema.output.OutputUnit;

/**
 * Default implementation for WoT ThingModel based validation/enforcement.
 */
final class DefaultWotThingModelValidation implements WotThingModelValidation {

    private static final String ATTRIBUTES = "attributes";
    private static final String PROPERTIES = "properties";
    private static final String DESIRED_PROPERTIES = "desiredProperties";

    private static final String DITTO_CATEGORY = "ditto:category";

    private final TmValidationConfig validationConfig;
    private final JsonSchemaTools jsonSchemaTools;

    public DefaultWotThingModelValidation(final TmValidationConfig validationConfig) {
        this.validationConfig = validationConfig;
        jsonSchemaTools = new JsonSchemaTools();
    }

    @Override
    public CompletionStage<Void> validateThingAttributes(final ThingModel thingModel,
            final Thing thing,
            final DittoHeaders dittoHeaders) {

        if (validationConfig.getThingValidationConfig().isEnforceAttributes()) {
            return enforceThingAttributes(thingModel, thing, dittoHeaders);
        }
        return success();
    }

    private CompletableFuture<Void> enforceThingAttributes(final ThingModel thingModel,
            final Thing thing,
            final DittoHeaders dittoHeaders) {
        return thingModel.getProperties()
                .map(tdProperties -> {
                    final Attributes attributes =
                            thing.getAttributes().orElseGet(() -> Attributes.newBuilder().build());

                    final String containerNamePlural = "Thing's attributes";
                    final CompletableFuture<Void> ensureRequiredPropertiesStage =
                            ensureRequiredProperties(thingModel, dittoHeaders, tdProperties, attributes,
                                    containerNamePlural, "Thing's attribute",
                                    JsonPointer.of(ATTRIBUTES), false);

                    final CompletableFuture<Void> ensureOnlyDefinedPropertiesStage;
                    if (!validationConfig.getThingValidationConfig().isAllowNonModeledAttributes()) {
                        ensureOnlyDefinedPropertiesStage =
                                ensureOnlyDefinedProperties(dittoHeaders, tdProperties, attributes, containerNamePlural,
                                        false);
                    } else {
                        ensureOnlyDefinedPropertiesStage = success();
                    }

                    final CompletableFuture<Void> validatePropertiesStage =
                            getValidatePropertiesStage(dittoHeaders, tdProperties, attributes,
                                    containerNamePlural, JsonPointer.of(ATTRIBUTES), false);

                    return CompletableFuture.allOf(
                            ensureRequiredPropertiesStage,
                            ensureOnlyDefinedPropertiesStage,
                            validatePropertiesStage
                    );
                }).orElseGet(DefaultWotThingModelValidation::success);
    }

    private CompletableFuture<Void> ensureRequiredProperties(final ThingModel thingModel,
            final DittoHeaders dittoHeaders,
            final Properties tdProperties,
            final JsonObject propertiesContainer,
            final String containerNamePlural,
            final String containerName,
            final JsonPointer pointerPrefix,
            final boolean handleDittoCategory
    ) {

        final Map<String, Property> nonProvidedRequiredProperties =
                filterNonProvidedRequiredProperties(tdProperties, thingModel, propertiesContainer, handleDittoCategory);

        final CompletableFuture<Void> requiredPropertiesStage;
        if (!nonProvidedRequiredProperties.isEmpty()) {
            final var exceptionBuilder = WotThingModelPayloadValidationException
                    .newBuilder("Required properties were missing from the " + containerNamePlural);
            nonProvidedRequiredProperties.forEach((rpKey, requiredProperty) ->
                    {
                        JsonPointer fullPointer = pointerPrefix;
                        if (handleDittoCategory && requiredProperty.contains(DITTO_CATEGORY)) {
                            fullPointer = fullPointer.addLeaf(
                                    JsonKey.of(requiredProperty.getValue(DITTO_CATEGORY).orElseThrow().asString())
                            );
                        }
                        fullPointer = fullPointer.addLeaf(JsonKey.of(rpKey));
                        exceptionBuilder.addValidationDetail(
                                fullPointer,
                                List.of(containerName + " <" + rpKey + "> is non optional and must be present")
                        );
                    }
            );
            requiredPropertiesStage = CompletableFuture
                    .failedFuture(exceptionBuilder.dittoHeaders(dittoHeaders).build());
        } else {
            requiredPropertiesStage = success();
        }
        return requiredPropertiesStage;
    }

    private Map<String, Property> filterNonProvidedRequiredProperties(final Properties tdProperties,
            final ThingModel thingModel,
            final JsonObject propertiesContainer,
            final boolean handleDittoCategory
    ) {

        final Map<String, Property> requiredProperties = extractRequiredProperties(tdProperties, thingModel);
        final Map<String, Property> nonProvidedRequiredProperties = new LinkedHashMap<>(requiredProperties);
        if (handleDittoCategory) {
            requiredProperties.forEach((rpKey, requiredProperty) -> {
                final Optional<JsonValue> dittoCategory = requiredProperty.getValue(DITTO_CATEGORY);
                if (dittoCategory.isPresent()) {
                    propertiesContainer.getValue(dittoCategory.get().asString())
                            .filter(JsonValue::isObject)
                            .map(JsonValue::asObject)
                            .ifPresent(categorizedProperties -> categorizedProperties.getKeys().stream()
                                    .map(JsonKey::toString)
                                    .forEach(nonProvidedRequiredProperties::remove)
                            );
                } else {
                    propertiesContainer.getKeys().stream()
                            .map(JsonKey::toString)
                            .forEach(nonProvidedRequiredProperties::remove);
                }
            });
        } else {
            propertiesContainer.getKeys().stream()
                    .map(JsonKey::toString)
                    .forEach(nonProvidedRequiredProperties::remove);
        }
        return nonProvidedRequiredProperties;
    }

    private CompletableFuture<Void> ensureOnlyDefinedProperties(final DittoHeaders dittoHeaders,
            final Properties tdProperties,
            final JsonObject propertiesContainer,
            final String containerNamePlural,
            final boolean handleDittoCategory
    ) {

        final Set<String> allDefinedPropertyKeys = tdProperties.keySet();
        final Set<String> allAvailablePropertiesKeys =
                propertiesContainer.getKeys().stream().map(JsonKey::toString)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        if (handleDittoCategory) {
            tdProperties.forEach((propertyName, property) -> {
                final Optional<String> dittoCategory = property.getValue(DITTO_CATEGORY)
                        .filter(JsonValue::isString)
                        .map(JsonValue::asString);
                final String categorizedPropertyName = dittoCategory
                        .map(c -> c + "/").orElse("")
                        .concat(propertyName);
                if (propertiesContainer.contains(JsonPointer.of(categorizedPropertyName))) {
                    allAvailablePropertiesKeys.remove(propertyName);
                    dittoCategory.ifPresent(allAvailablePropertiesKeys::remove);
                }
            });
        } else {
            allAvailablePropertiesKeys.removeAll(allDefinedPropertyKeys);
        }

        if (!allAvailablePropertiesKeys.isEmpty()) {
            final var exceptionBuilder = WotThingModelPayloadValidationException
                    .newBuilder("The " + containerNamePlural + " contained " +
                            "keys which were not defined in the model: " + allAvailablePropertiesKeys);
            return CompletableFuture.failedFuture(exceptionBuilder
                    .dittoHeaders(dittoHeaders)
                    .build());
        }
        return success();
    }

    private CompletableFuture<Void> getValidatePropertiesStage(final DittoHeaders dittoHeaders,
            final Properties tdProperties,
            final JsonObject propertiesContainer,
            final String containerNamePlural,
            final JsonPointer pointerPrefix,
            final boolean handleDittoCategory
    ) {

        final CompletableFuture<Void> validatePropertiesStage;
        final Map<Property, OutputUnit> invalidProperties;
        if (handleDittoCategory) {
            invalidProperties = determineInvalidProperties(tdProperties,
                    p -> propertiesContainer.getValue(p.getValue(DITTO_CATEGORY)
                            .filter(JsonValue::isString)
                            .map(JsonValue::asString)
                            .map(c -> c + "/")
                            .orElse("")
                            .concat(p.getPropertyName())),
                    dittoHeaders
            );
        } else {
            invalidProperties = determineInvalidProperties(tdProperties,
                    p -> propertiesContainer.getValue(p.getPropertyName()),
                    dittoHeaders
            );
        }

        if (!invalidProperties.isEmpty()) {
            final var exceptionBuilder = WotThingModelPayloadValidationException
                    .newBuilder("The " + containerNamePlural + " contained validation errors, " +
                            "check the validation details.");
            invalidProperties.forEach((key, value) -> {
                JsonPointer fullPointer = pointerPrefix;
                if (handleDittoCategory && key.contains(DITTO_CATEGORY)) {
                    fullPointer = fullPointer.addLeaf(
                            JsonKey.of(key.getValue(DITTO_CATEGORY).orElseThrow().asString())
                    );
                }
                fullPointer = fullPointer.addLeaf(JsonKey.of(key.getPropertyName()));
                exceptionBuilder.addValidationDetail(
                        fullPointer,
                        value.getDetails().stream()
                                .map(ou -> ou.getInstanceLocation() + ": " + ou.getErrors())
                                .toList()
                );
            });
            validatePropertiesStage = CompletableFuture.failedFuture(exceptionBuilder
                    .dittoHeaders(dittoHeaders)
                    .build());
        } else {
            validatePropertiesStage = success();
        }
        return validatePropertiesStage;
    }

    private Map<Property, OutputUnit> determineInvalidProperties(final Properties tdProperties,
            final Function<Property, Optional<JsonValue>> propertyExtractor, final DittoHeaders dittoHeaders) {

        return tdProperties.entrySet().stream()
                .flatMap(tdPropertyEntry ->
                        propertyExtractor.apply(tdPropertyEntry.getValue())
                                .map(attributeValue -> new AbstractMap.SimpleEntry<>(
                                        tdPropertyEntry.getValue(),
                                        jsonSchemaTools.validateDittoJsonBasedOnDataSchema(
                                                tdPropertyEntry.getValue(),
                                                attributeValue,
                                                dittoHeaders
                                        )
                                ))
                                .filter(entry -> !entry.getValue().isValid())
                                .stream()
                ).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Override
    public CompletionStage<Void> validateFeaturesProperties(final Map<String, ThingModel> featureThingModels,
            final Features features,
            final DittoHeaders dittoHeaders) {

        // TODO TJ implement - this should collect errors of all invalid features of the thing in a combined exception!
        final CompletableFuture<List<Void>> enforcedPropertiesListFuture;
        if (validationConfig.getFeatureValidationConfig().isEnforceProperties()) {
            final List<CompletableFuture<Void>> enforcedPropertiesFutures = featureThingModels
                    .entrySet()
                    .stream()
                    .filter(entry -> features.getFeature(entry.getKey()).isPresent())
                    .map(entry ->
                            enforceFeatureProperties(entry.getValue(),
                                    features.getFeature(entry.getKey()).orElseThrow(), true, false, dittoHeaders)
                    )
                    .toList();
            enforcedPropertiesListFuture =
                    CompletableFuture.allOf(enforcedPropertiesFutures.toArray(new CompletableFuture[0]))
                            .thenApply(ignored -> enforcedPropertiesFutures.stream()
                                    .map(CompletableFuture::join)
                                    .toList()
                            );
        } else {
            enforcedPropertiesListFuture = CompletableFuture.completedFuture(null);
        }

        if (validationConfig.getFeatureValidationConfig().isEnforceDesiredProperties()) {
            final List<CompletableFuture<Void>> enforcedDesiredPropertiesFutures = featureThingModels
                    .entrySet()
                    .stream()
                    .map(entry ->
                            enforceFeatureProperties(entry.getValue(),
                                    features.getFeature(entry.getKey()).orElseThrow(), false, true, dittoHeaders)
                    )
                    .toList();
            return enforcedPropertiesListFuture.thenCompose(voidL ->
                    CompletableFuture.allOf(enforcedDesiredPropertiesFutures.toArray(new CompletableFuture[0]))
                            .thenApply(ignored -> enforcedDesiredPropertiesFutures.stream()
                                    .map(CompletableFuture::join)
                                    .toList()
                            )
            ).thenApply(voidL -> null);
        }
        return enforcedPropertiesListFuture.thenApply(voidL -> null);
    }

    @Override
    public CompletionStage<Void> validateFeatureProperties(final ThingModel featureThingModel,
            final Feature feature,
            final DittoHeaders dittoHeaders) {

        final CompletableFuture<Void> enforcedPropertiesFuture;
        if (validationConfig.getFeatureValidationConfig().isEnforceProperties()) {
            enforcedPropertiesFuture = enforceFeatureProperties(featureThingModel,
                    feature, true, false, dittoHeaders);
        } else {
            enforcedPropertiesFuture = success();
        }

        if (validationConfig.getFeatureValidationConfig().isEnforceDesiredProperties()) {
            return enforcedPropertiesFuture.thenCompose(aVoid ->
                    enforceFeatureProperties(featureThingModel,
                            feature, false, true, dittoHeaders)
            );
        }
        return enforcedPropertiesFuture;
    }

    private CompletableFuture<Void> enforceFeatureProperties(final ThingModel featureThingModel,
            final Feature feature,
            final boolean checkForRequiredProperties,
            final boolean desiredProperties,
            final DittoHeaders dittoHeaders
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
                    final String path = desiredProperties ? DESIRED_PROPERTIES : PROPERTIES;

                    final CompletableFuture<Void> ensureRequiredPropertiesStage;
                    if (checkForRequiredProperties) {
                        ensureRequiredPropertiesStage = ensureRequiredProperties(featureThingModel, dittoHeaders,
                                tdProperties, featureProperties, containerNamePlural,
                                containerNamePrefix + "property", JsonPointer.of(path), true);
                    } else {
                        ensureRequiredPropertiesStage = success();
                    }


                    final CompletableFuture<Void> ensureOnlyDefinedPropertiesStage;
                    if (!validationConfig.getThingValidationConfig().isAllowNonModeledAttributes()) {
                        ensureOnlyDefinedPropertiesStage =
                                ensureOnlyDefinedProperties(dittoHeaders, tdProperties, featureProperties,
                                        containerNamePlural, true);
                    } else {
                        ensureOnlyDefinedPropertiesStage = CompletableFuture.completedFuture(null);
                    }

                    final CompletableFuture<Void> validatePropertiesStage =
                            getValidatePropertiesStage(dittoHeaders, tdProperties, featureProperties,
                                    containerNamePlural, JsonPointer.of(path), true);

                    return CompletableFuture.allOf(
                            ensureRequiredPropertiesStage,
                            ensureOnlyDefinedPropertiesStage,
                            validatePropertiesStage
                    );
                }).orElseGet(DefaultWotThingModelValidation::success);
    }

    private static CompletableFuture<Void> success() {
        return CompletableFuture.completedFuture(null);
    }

    private Map<String, Property> extractRequiredProperties(final Properties tdProperties,
            final ThingModel thingModel) {
        return thingModel.getTmOptional().map(tmOptionalElements -> {
            final Map<String, Property> allRequiredProperties = new LinkedHashMap<>(tdProperties);
            tmOptionalElements.stream()
                    .map(TmOptionalElement::toString)
                    .filter(el -> el.startsWith("/properties/"))
                    .map(el -> el.replace("/properties/", ""))
                    .forEach(allRequiredProperties::remove);
            return allRequiredProperties;
        }).orElseGet(LinkedHashMap::new);
    }
}
