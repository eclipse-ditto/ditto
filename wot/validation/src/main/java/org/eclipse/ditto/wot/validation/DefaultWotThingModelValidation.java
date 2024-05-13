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
import org.eclipse.ditto.things.model.Features;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.wot.model.Properties;
import org.eclipse.ditto.wot.model.ThingModel;
import org.eclipse.ditto.wot.model.TmOptionalElement;
import org.eclipse.ditto.wot.validation.config.TmValidationConfig;

import com.networknt.schema.output.OutputUnit;

final class DefaultWotThingModelValidation implements WotThingModelValidation {

    private static final String ATTRIBUTES = "attributes";

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

                    final CompletableFuture<Void> ensureRequiredPropertiesStage =
                            ensureRequiredProperties(thingModel, dittoHeaders, tdProperties, attributes,
                                    ATTRIBUTES, JsonPointer.of(ATTRIBUTES));

                    final CompletableFuture<Void> ensureOnlyDefinedPropertiesStage;
                    if (!validationConfig.getThingValidationConfig().isAllowNonModeledAttributes()) {
                        ensureOnlyDefinedPropertiesStage =
                                ensureOnlyDefinedProperties(dittoHeaders, tdProperties, attributes, ATTRIBUTES);
                    } else {
                        ensureOnlyDefinedPropertiesStage = CompletableFuture.completedFuture(null);
                    }

                    final CompletableFuture<Void> validatePropertiesStage =
                            getValidatePropertiesStage(dittoHeaders, tdProperties, attributes,
                                    ATTRIBUTES, JsonPointer.of(ATTRIBUTES));

                    return CompletableFuture.allOf(
                            ensureRequiredPropertiesStage,
                            ensureOnlyDefinedPropertiesStage,
                            validatePropertiesStage
                    );
                }).orElseGet(DefaultWotThingModelValidation::success);
    }

    private CompletableFuture<Void> ensureRequiredProperties(final ThingModel thingModel,
            final DittoHeaders dittoHeaders, final Properties tdProperties, final JsonObject propertiesContainer,
            final String containerName, final JsonPointer pointerPrefix) {

        final Set<String> requiredProperties = extractRequiredProperties(tdProperties, thingModel);
        propertiesContainer.getKeys().stream().map(JsonKey::toString).forEach(requiredProperties::remove);
        final CompletableFuture<Void> requiredPropertiesStage;
        if (!requiredProperties.isEmpty()) {
            final var exceptionBuilder = WotThingModelPayloadValidationException
                    .newBuilder("Required properties were missing from the Thing's " + containerName);
            requiredProperties.forEach(rp ->
                    exceptionBuilder.addValidationDetail(
                            pointerPrefix.addLeaf(JsonKey.of(rp)),
                            List.of(containerName + " <" + rp + "> is non optional and must be present")
                    )
            );
            requiredPropertiesStage = CompletableFuture
                    .failedFuture(exceptionBuilder.dittoHeaders(dittoHeaders).build());
        } else {
            requiredPropertiesStage = success();
        }
        return requiredPropertiesStage;
    }

    private CompletableFuture<Void> ensureOnlyDefinedProperties(final DittoHeaders dittoHeaders,
            final Properties tdProperties, final JsonObject propertiesContainer, final String containerName) {
        final CompletableFuture<Void> ensureOnlyDefinedPropertiesStage;
        if (!validationConfig.getThingValidationConfig().isAllowNonModeledAttributes()) {
            final Set<String> allDefinedPropertyKeys = tdProperties.keySet();
            final Set<String> allAvailablePropertiesKeys =
                    propertiesContainer.getKeys().stream().map(JsonKey::toString)
                            .collect(Collectors.toCollection(LinkedHashSet::new));
            allAvailablePropertiesKeys.removeAll(allDefinedPropertyKeys);
            if (!allAvailablePropertiesKeys.isEmpty()) {
                final var exceptionBuilder = WotThingModelPayloadValidationException
                        .newBuilder("The Thing's " + containerName + " contained " + containerName +
                                " keys which were not defined in the model: " + allAvailablePropertiesKeys);
                ensureOnlyDefinedPropertiesStage = CompletableFuture.failedFuture(exceptionBuilder
                        .dittoHeaders(dittoHeaders)
                        .build());
            } else {
                ensureOnlyDefinedPropertiesStage = success();
            }
        } else {
            ensureOnlyDefinedPropertiesStage = success();
        }
        return ensureOnlyDefinedPropertiesStage;
    }

    private CompletableFuture<Void> getValidatePropertiesStage(final DittoHeaders dittoHeaders,
            final Properties tdProperties, final JsonObject propertiesContainer, final String containerName,
            final JsonPointer pointerPrefix) {

        final CompletableFuture<Void> validatePropertiesStage;
        final Map<String, OutputUnit> invalidProperties =
                determineInvalidProperties(tdProperties, propertiesContainer::getValue, dittoHeaders);
        if (!invalidProperties.isEmpty()) {
            final var exceptionBuilder = WotThingModelPayloadValidationException
                    .newBuilder("The Thing's " + containerName + " contained validation errors, " +
                            "check the validation details.");
            invalidProperties.forEach((key, value) -> exceptionBuilder.addValidationDetail(
                    pointerPrefix.addLeaf(JsonKey.of(key)),
                    value.getDetails().stream()
                            .map(ou -> ou.getInstanceLocation() + ": " + ou.getErrors())
                            .toList()
            ));
            validatePropertiesStage = CompletableFuture.failedFuture(exceptionBuilder
                    .dittoHeaders(dittoHeaders)
                    .build());
        } else {
            validatePropertiesStage = success();
        }
        return validatePropertiesStage;
    }

    private Map<String, OutputUnit> determineInvalidProperties(final Properties tdProperties,
            final Function<String, Optional<JsonValue>> propertyExtractor, final DittoHeaders dittoHeaders) {

        return tdProperties.entrySet().stream()
                .flatMap(tdPropertyEntry ->
                        propertyExtractor.apply(tdPropertyEntry.getKey())
                                .map(attributeValue -> new AbstractMap.SimpleEntry<>(
                                        tdPropertyEntry.getKey(),
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
    public CompletionStage<Void> validateFeatures(final Map<String, ThingModel> featureModels,
            final Features features,
            final DittoHeaders dittoHeaders) {
        // TODO TJ implement - this should collect errors of all invalid features of the thing in a combined exception!
        return success();
    }

    @Override
    public CompletionStage<Void> validateFeature(final ThingModel thingModel,
            final Feature feature,
            final DittoHeaders dittoHeaders) {
        // TODO TJ implement
        return success();
    }

    private CompletionStage<Void> enforceThingFeatures(final ThingModel thingModel,
            final Thing thing,
            final DittoHeaders dittoHeaders) {


        return null;
    }

    private static CompletableFuture<Void> success() {
        return CompletableFuture.completedFuture(null);
    }

    private Set<String> extractRequiredProperties(final Properties tdProperties, final ThingModel thingModel) {
        return thingModel.getTmOptional().map(tmOptionalElements -> {
            final Set<String> allDefinedProperties = tdProperties.keySet();
            final Set<String> allOptionalProperties = tmOptionalElements.stream()
                    .map(TmOptionalElement::toString)
                    .filter(el -> el.startsWith("/properties/"))
                    .map(el -> el.replace("/properties/", ""))
                    .collect(Collectors.toSet());
            final Set<String> allRequiredProperties = new LinkedHashSet<>(allDefinedProperties);
            allRequiredProperties.removeAll(allOptionalProperties);
            return allRequiredProperties;
        }).orElseGet(LinkedHashSet::new);
    }
}
