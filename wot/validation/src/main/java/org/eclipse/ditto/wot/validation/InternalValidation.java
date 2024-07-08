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
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.wot.model.Actions;
import org.eclipse.ditto.wot.model.DittoWotExtension;
import org.eclipse.ditto.wot.model.Event;
import org.eclipse.ditto.wot.model.Events;
import org.eclipse.ditto.wot.model.Properties;
import org.eclipse.ditto.wot.model.Property;
import org.eclipse.ditto.wot.model.SingleDataSchema;
import org.eclipse.ditto.wot.model.SingleUriAtContext;
import org.eclipse.ditto.wot.model.ThingModel;
import org.eclipse.ditto.wot.model.TmOptionalElement;

import com.networknt.schema.output.OutputUnit;

final class InternalValidation {

    private static final JsonSchemaTools JSON_SCHEMA_TOOLS = new JsonSchemaTools();

    private InternalValidation() {
        throw new AssertionError();
    }

    static CompletableFuture<Void> ensureOnlyDefinedProperties(final ThingModel thingModel,
            final Properties tdProperties,
            final JsonObject propertiesContainer,
            final String containerNamePlural,
            final boolean handleDittoCategory,
            final DittoHeaders dittoHeaders
    ) {
        final Set<String> allDefinedPropertyKeys = tdProperties.keySet();
        final Set<String> allAvailablePropertiesKeys =
                propertiesContainer.getKeys().stream().map(JsonKey::toString)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        if (handleDittoCategory) {
            tdProperties.forEach((propertyName, property) -> {
                final Optional<String> dittoCategory = determineDittoCategory(thingModel, property);
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
                            "JSON fields which were not defined in the model: " + allAvailablePropertiesKeys);
            return CompletableFuture.failedFuture(exceptionBuilder
                    .dittoHeaders(dittoHeaders)
                    .build());
        }
        return success();
    }

    static CompletableFuture<Void> ensureRequiredProperties(final ThingModel thingModel,
            final Properties tdProperties,
            final JsonObject propertiesContainer,
            final String containerNamePlural,
            final String containerName,
            final JsonPointer resourcePath,
            final boolean handleDittoCategory,
            final DittoHeaders dittoHeaders
    ) {
        final Map<String, Property> nonProvidedRequiredProperties =
                filterNonProvidedRequiredProperties(tdProperties, thingModel, propertiesContainer, handleDittoCategory);

        if (!nonProvidedRequiredProperties.isEmpty()) {
            final var exceptionBuilder = WotThingModelPayloadValidationException
                    .newBuilder("Required JSON fields were missing from the " + containerNamePlural);
            nonProvidedRequiredProperties.forEach((rpKey, requiredProperty) ->
                    {
                        JsonPointer fullPointer = resourcePath;
                        final Optional<String> dittoCategory = determineDittoCategory(thingModel, requiredProperty);
                        if (handleDittoCategory && dittoCategory.isPresent()) {
                            fullPointer = fullPointer.addLeaf(JsonKey.of(dittoCategory.get()));
                        }
                        fullPointer = fullPointer.addLeaf(JsonKey.of(rpKey));
                        exceptionBuilder.addValidationDetail(
                                fullPointer,
                                List.of(containerName + " <" + rpKey + "> is non optional and must be provided")
                        );
                    }
            );
            return CompletableFuture.failedFuture(exceptionBuilder.dittoHeaders(dittoHeaders).build());
        }
        return success();
    }

    static Map<String, Property> filterNonProvidedRequiredProperties(final Properties tdProperties,
            final ThingModel thingModel,
            final JsonObject propertiesContainer,
            final boolean handleDittoCategory
    ) {
        final Map<String, Property> requiredProperties = extractRequiredProperties(tdProperties, thingModel);
        final Map<String, Property> nonProvidedRequiredProperties = new LinkedHashMap<>(requiredProperties);
        if (handleDittoCategory) {
            requiredProperties.forEach((rpKey, requiredProperty) -> {
                final Optional<String> dittoCategory = determineDittoCategory(thingModel, requiredProperty);
                if (dittoCategory.isPresent()) {
                    propertiesContainer.getValue(dittoCategory.get())
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

    private static Map<String, Property> extractRequiredProperties(final Properties tdProperties,
            final ThingModel thingModel
    ) {
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

    static CompletableFuture<Void> ensureOnlyDefinedActions(@Nullable final Actions actions,
            final String messageSubject,
            final String containerName,
            final DittoHeaders dittoHeaders
    ) {
        final Set<String> allDefinedActionKeys = Optional.ofNullable(actions).map(Actions::keySet).orElseGet(Set::of);
        final boolean messageSubjectIsDefinedAsAction = allDefinedActionKeys.contains(messageSubject);
        if (!messageSubjectIsDefinedAsAction) {
            final var exceptionBuilder = WotThingModelPayloadValidationException
                    .newBuilder("The " + containerName + " message subject <" +
                            messageSubject + "> is not defined as known action in the model: " + allDefinedActionKeys
                    );
            return CompletableFuture.failedFuture(exceptionBuilder
                    .dittoHeaders(dittoHeaders)
                    .build());
        }
        return success();
    }

    static CompletableFuture<Void> ensureOnlyDefinedEvents(final DittoHeaders dittoHeaders,
            @Nullable final Events events,
            final String messageSubject,
            final String containerName
    ) {
        final Set<String> allDefinedEventKeys = Optional.ofNullable(events).map(Events::keySet).orElseGet(Set::of);
        final boolean messageSubjectIsDefinedAsEvent = allDefinedEventKeys.contains(messageSubject);
        if (!messageSubjectIsDefinedAsEvent) {
            final var exceptionBuilder = WotThingModelPayloadValidationException
                    .newBuilder("The " + containerName + " message subject <" +
                            messageSubject + "> is not defined as known event in the model: " + allDefinedEventKeys
                    );
            return CompletableFuture.failedFuture(exceptionBuilder
                    .dittoHeaders(dittoHeaders)
                    .build());
        }
        return success();
    }

    static Optional<String> determineDittoCategory(final ThingModel thingModel, final Property property) {
        final Optional<String> dittoExtensionPrefix = thingModel.getAtContext()
                .determinePrefixFor(SingleUriAtContext.DITTO_WOT_EXTENSION);
        return dittoExtensionPrefix.flatMap(prefix ->
                        property.getValue(prefix + ":" + DittoWotExtension.DITTO_WOT_EXTENSION_CATEGORY)
                )
                .filter(JsonValue::isString)
                .map(JsonValue::asString);
    }

    static CompletableFuture<Void> validateProperties(final ThingModel thingModel,
            final Properties tdProperties,
            final JsonObject propertiesContainer,
            final boolean validateRequiredObjectFields,
            final String containerNamePlural,
            final JsonPointer resourcePath,
            final boolean handleDittoCategory,
            final DittoHeaders dittoHeaders
    ) {
        final Map<Property, OutputUnit> invalidProperties;
        if (handleDittoCategory) {
            invalidProperties = determineInvalidProperties(tdProperties,
                    p -> propertiesContainer.getValue(
                            determineDittoCategory(thingModel, p)
                                    .map(c -> c + "/")
                                    .orElse("")
                                    .concat(p.getPropertyName())
                    ),
                    validateRequiredObjectFields,
                    dittoHeaders
            );
        } else {
            invalidProperties = determineInvalidProperties(tdProperties,
                    p -> propertiesContainer.getValue(p.getPropertyName()),
                    validateRequiredObjectFields,
                    dittoHeaders
            );
        }

        if (!invalidProperties.isEmpty()) {
            final var exceptionBuilder = WotThingModelPayloadValidationException
                    .newBuilder("The " + containerNamePlural + " contained validation errors, " +
                            "check the validation details.");
            invalidProperties.forEach((property, validationOutputUnit) -> {
                JsonPointer fullPointer = resourcePath;
                final Optional<String> dittoCategory = determineDittoCategory(thingModel, property);
                if (handleDittoCategory && dittoCategory.isPresent()) {
                    fullPointer = fullPointer.addLeaf(JsonKey.of(dittoCategory.get()));
                }
                fullPointer = fullPointer.addLeaf(JsonKey.of(property.getPropertyName()));
                exceptionBuilder.addValidationDetail(
                        fullPointer,
                        validationOutputUnit.getDetails().stream()
                                .map(ou -> ou.getInstanceLocation() + ": " + ou.getErrors())
                                .toList()
                );
            });
            return CompletableFuture.failedFuture(exceptionBuilder
                    .dittoHeaders(dittoHeaders)
                    .build());
        }
        return success();
    }

    static Map<Property, OutputUnit> determineInvalidProperties(final Properties tdProperties,
            final Function<Property, Optional<JsonValue>> propertyExtractor,
            final boolean validateRequiredObjectFields,
            final DittoHeaders dittoHeaders
    ) {
        return tdProperties.entrySet().stream()
                .flatMap(tdPropertyEntry ->
                        propertyExtractor.apply(tdPropertyEntry.getValue())
                                .map(propertyValue -> new AbstractMap.SimpleEntry<>(
                                        tdPropertyEntry.getValue(),
                                        JSON_SCHEMA_TOOLS.validateDittoJsonBasedOnDataSchema(
                                                tdPropertyEntry.getValue(),
                                                JsonPointer.empty(),
                                                validateRequiredObjectFields,
                                                propertyValue,
                                                dittoHeaders
                                        )
                                ))
                                .filter(entry -> !entry.getValue().isValid())
                                .stream()
                ).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (u, v) -> {
                    throw new IllegalStateException(String.format("Duplicate key %s", u));
                }, LinkedHashMap::new));
    }

    static CompletableFuture<Void> validateProperty(final ThingModel thingModel,
            final Properties tdProperties,
            final JsonPointer propertyPath,
            final boolean validateRequiredObjectFields,
            final JsonValue propertyValue,
            final String propertyDescription,
            final JsonPointer resourcePath,
            final boolean handleDittoCategory,
            final DittoHeaders dittoHeaders
    ) {
        final JsonValue valueToValidate;
        if (propertyPath.getLevelCount() > 1) {
            valueToValidate = JsonObject.newBuilder()
                    .set(propertyPath.getSubPointer(1).orElseThrow(), propertyValue)
                    .build();
        } else {
            valueToValidate = propertyValue;
        }

        return tdProperties.values()
                .stream()
                .filter(property -> {
                    if (handleDittoCategory) {
                        final JsonPointer thePropertyPath = determineDittoCategory(thingModel, property)
                                .flatMap(cat -> propertyPath.getSubPointer(1))
                                .orElse(propertyPath);
                        return property.getPropertyName().equals(thePropertyPath.getRoot().orElseThrow().toString());
                    } else {
                        return property.getPropertyName().equals(propertyPath.getRoot().orElseThrow().toString());
                    }
                })
                .findFirst()
                .map(property -> {
                    if (handleDittoCategory) {
                        final Optional<String> dittoCategory = determineDittoCategory(thingModel, property);
                        final JsonPointer thePropertyPath = dittoCategory
                                .flatMap(cat -> propertyPath.getSubPointer(1))
                                .orElse(propertyPath);
                        final JsonValue theValueToValidate = dittoCategory
                                .flatMap(cat -> valueToValidate.asObject().getValue(thePropertyPath))
                                .orElse(valueToValidate);
                        return validateSingleDataSchema(
                                property,
                                propertyDescription,
                                thePropertyPath,
                                validateRequiredObjectFields,
                                theValueToValidate,
                                resourcePath,
                                dittoHeaders
                        );
                    } else {
                        return validateSingleDataSchema(
                                property,
                                propertyDescription,
                                propertyPath,
                                validateRequiredObjectFields,
                                valueToValidate,
                                resourcePath,
                                dittoHeaders
                        );
                    }
                }).orElseGet(InternalValidation::success);
    }

    static CompletableFuture<Void> enforceActionPayload(final ThingModel thingModel,
            final String messageSubject,
            @Nullable final JsonValue inputPayload,
            final JsonPointer resourcePath,
            final boolean isInput,
            final String validationFailedDescription,
            final DittoHeaders dittoHeaders
    ) {
        return thingModel.getActions()
                .flatMap(action -> action.getAction(messageSubject))
                .flatMap(action -> isInput ? action.getInput() : action.getOutput())
                .map(schema -> validateSingleDataSchema(
                        schema,
                        validationFailedDescription,
                        JsonPointer.empty(),
                        true,
                        inputPayload,
                        resourcePath,
                        dittoHeaders
                ))
                .orElseGet(InternalValidation::success);
    }

    static CompletableFuture<Void> enforceEventPayload(final ThingModel thingModel,
            final String messageSubject,
            @Nullable final JsonValue dataPayload,
            final JsonPointer resourcePath,
            final String validationFailedDescription,
            final DittoHeaders dittoHeaders
    ) {
        return thingModel.getEvents()
                .flatMap(event -> event.getEvent(messageSubject))
                .flatMap(Event::getData)
                .map(schema -> validateSingleDataSchema(
                        schema,
                        validationFailedDescription,
                        JsonPointer.empty(),
                        true,
                        dataPayload,
                        resourcePath,
                        dittoHeaders
                ))
                .orElseGet(InternalValidation::success);
    }

    static CompletableFuture<Void> validateSingleDataSchema(final SingleDataSchema dataSchema,
            final String validatedDescription,
            final JsonPointer pointerPath,
            final boolean validateRequiredObjectFields,
            @Nullable final JsonValue jsonValue,
            final JsonPointer resourcePath,
            final DittoHeaders dittoHeaders
    ) {
        final OutputUnit validationOutput = JSON_SCHEMA_TOOLS.validateDittoJsonBasedOnDataSchema(
                dataSchema,
                pointerPath,
                validateRequiredObjectFields,
                jsonValue,
                dittoHeaders
        );

        if (!validationOutput.isValid()) {
            final var exceptionBuilder = WotThingModelPayloadValidationException
                    .newBuilder("The " + validatedDescription + " contained validation errors, " +
                            "check the validation details.");
            exceptionBuilder.addValidationDetail(
                    resourcePath,
                    validationOutput.getDetails().stream()
                            .map(ou -> ou.getInstanceLocation() + ": " + ou.getErrors())
                            .toList()
            );
            return CompletableFuture.failedFuture(exceptionBuilder
                    .dittoHeaders(dittoHeaders)
                    .build());
        }
        return success();
    }

    static <T> CompletableFuture<T> success() {
        return CompletableFuture.completedFuture(null);
    }
}
