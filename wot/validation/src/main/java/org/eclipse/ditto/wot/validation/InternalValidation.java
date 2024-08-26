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

import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.wot.model.Actions;
import org.eclipse.ditto.wot.model.DittoWotExtension;
import org.eclipse.ditto.wot.model.Event;
import org.eclipse.ditto.wot.model.Events;
import org.eclipse.ditto.wot.model.ObjectSchema;
import org.eclipse.ditto.wot.model.Properties;
import org.eclipse.ditto.wot.model.Property;
import org.eclipse.ditto.wot.model.SingleDataSchema;
import org.eclipse.ditto.wot.model.SingleUriAtContext;
import org.eclipse.ditto.wot.model.ThingModel;
import org.eclipse.ditto.wot.model.TmOptionalElement;

import com.networknt.schema.output.OutputUnit;

final class InternalValidation {

    private static final JsonSchemaTools JSON_SCHEMA_TOOLS = new JsonSchemaTools();
    private static final String PROPERTIES_PATH_PREFIX = "/properties/";

    private InternalValidation() {
        throw new AssertionError();
    }

    static CompletableFuture<Void> ensureOnlyDefinedProperties(final ThingModel thingModel,
            final Properties tdProperties,
            final JsonObject propertiesContainer,
            final String containerNamePlural,
            final boolean handleDittoCategory,
            final ValidationContext context
    ) {
        final Set<String> allDefinedPropertyKeys = tdProperties.keySet();
        final Set<String> allAvailablePropertiesKeys =
                propertiesContainer.getKeys().stream().map(JsonKey::toString)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        if (handleDittoCategory) {
            final Set<String> categories = determineDittoCategories(thingModel);
            categories.forEach(category ->
                    propertiesContainer.getValue(category)
                            .map(JsonValue::asObject)
                            .ifPresent(categoryObj ->
                                    allAvailablePropertiesKeys.addAll(
                                            categoryObj.getKeys().stream()
                                                    .map(JsonKey::toString)
                                                    .map(key -> category + "/" + key)
                                                    .collect(Collectors.toCollection(LinkedHashSet::new))
                                    )
                            )
            );
            tdProperties.forEach((propertyName, property) -> {
                final Optional<String> dittoCategory = determineDittoCategory(thingModel, property);
                final String categorizedPropertyName = dittoCategory
                        .map(c -> c + "/").orElse("")
                        .concat(propertyName);
                if (propertiesContainer.contains(JsonPointer.of(categorizedPropertyName))) {
                    allAvailablePropertiesKeys.remove(categorizedPropertyName);
                    dittoCategory.ifPresent(allAvailablePropertiesKeys::remove);
                } else if (dittoCategory.filter(propertiesContainer::contains).isPresent()) {
                    allAvailablePropertiesKeys.remove(dittoCategory.get());
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
                    .dittoHeaders(context.dittoHeaders())
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
            final ValidationContext context
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
            return CompletableFuture.failedFuture(exceptionBuilder.dittoHeaders(context.dittoHeaders()).build());
        }
        return success();
    }

    static CompletableFuture<Void> enforcePresenceOfRequiredPropertiesUponDeletion(
            final ThingModel thingModel,
            final JsonPointer resourcePath,
            final boolean handleDittoCategory,
            final Set<String> dittoCategories,
            final String pluralDescription,
            final String singularDescription,
            final ValidationContext context
    ) {
        if (resourcePath.getLevelCount() == 1) {
            // deleting all attributes/properties ..
            //  must be prevented if there is at least one non-optional TM model "property" defined
            final boolean containsRequiredProperties = thingModel.getProperties()
                    .map(properties -> extractRequiredTmProperties(properties, thingModel))
                    .map(map -> !map.isEmpty())
                    .orElse(false);
            if (containsRequiredProperties) {
                final WotThingModelPayloadValidationException.Builder exceptionBuilder =
                        WotThingModelPayloadValidationException
                                .newBuilder("Could not delete " + pluralDescription + ", " +
                                        "as there are some defined as non-optional in the model");
                return CompletableFuture.failedFuture(exceptionBuilder
                        .dittoHeaders(context.dittoHeaders())
                        .build());
            }
        } else {
            // deleting a specific attribute/property ..
            //  check if that is an optional one
            final JsonPointer resourcePointer = resourcePath.getSubPointer(1).orElseThrow();
            final Optional<PropertyWithCategory> propertyWithCategory = findPropertyBasedOnPath(thingModel,
                    thingModel.getProperties().orElse(Properties.of(Map.of())),
                    resourcePointer,
                    handleDittoCategory,
                    dittoCategories
            );

            final boolean isPropertyRequired;
            if (propertyWithCategory.isPresent() && (
                    (propertyWithCategory.get().category() != null && resourcePointer.getLevelCount() == 2) ||
                            (propertyWithCategory.get().category() == null && resourcePointer.getLevelCount() == 1)
            )) {
                // it is sufficient to only look at the WoT TM Property
                isPropertyRequired = propertyWithCategory
                        .filter(withCategory -> isTmPropertyRequired(withCategory.property(), thingModel))
                        .isPresent();
            } else {
                // we need to look into "required" of property schemas of type "object" to find out if the path is required
                isPropertyRequired = propertyWithCategory
                        .filter(withCategory ->
                                isPropertyRequired(withCategory.property(),
                                        withCategory.category(),
                                        resourcePointer,
                                        handleDittoCategory
                                )
                        ).isPresent();
            }
            if (isPropertyRequired) {
                final WotThingModelPayloadValidationException.Builder exceptionBuilder =
                        WotThingModelPayloadValidationException
                                .newBuilder("Could not delete " + singularDescription + " <" + resourcePointer + "> " +
                                        "as it is defined as non-optional in the model");
                return CompletableFuture.failedFuture(exceptionBuilder
                        .dittoHeaders(context.dittoHeaders())
                        .build());
            }
        }
        return success();
    }

    static boolean isPropertyRequired(final Property property,
            @Nullable final String category,
            final JsonPointer resourcePath,
            final boolean handleDittoCategory
    ) {
        if (handleDittoCategory) {
            return Optional.ofNullable(category)
                    .map(cat ->
                            resourcePath.getRoot().filter(root -> root.toString().equals(cat)).isPresent() &&
                                    checkResourceForRequired(resourcePath.getSubPointer(1).orElse(null), property,
                                            property.getPropertyName())
                    )
                    .orElseGet(() ->
                            checkResourceForRequired(resourcePath, property, property.getPropertyName())
                    );
        } else {
            return checkResourceForRequired(resourcePath, property, property.getPropertyName());
        }
    }

    private static boolean checkResourceForRequired(@Nullable final JsonPointer resourcePath,
            final Property property,
            final String key
    ) {
        if (null == resourcePath || resourcePath.isEmpty()) {
            return false;
        } else if (resourcePath.getLevelCount() == 1) {
            return resourcePath.getRoot().filter(prop -> prop.toString().equals(key)).isPresent();
        } else if (property.isObjectSchema()) {
            return checkResourceForRequired(resourcePath.getSubPointer(1).orElseThrow(), property.asObjectSchema());
        } else {
            // this should not happen, consider it an error?
            return false;
        }
    }

    private static boolean checkResourceForRequired(@Nullable final JsonPointer resourcePath,
            final ObjectSchema objectSchema
    ) {
        if (null == resourcePath || resourcePath.isEmpty()) {
            return false;
        } else {
            final Optional<String> rootKey = resourcePath.getRoot().map(JsonKey::toString);
            final boolean isRequired = rootKey
                    .map(key -> objectSchema.getRequired().contains(key))
                    .orElse(false);
            if (resourcePath.getLevelCount() > 1 && isRequired) {
                final SingleDataSchema subSchema = objectSchema.getProperties().get(rootKey.orElseThrow());
                // recurse!
                return checkResourceForRequired(resourcePath.getSubPointer(1).orElseThrow(), (ObjectSchema) subSchema);
            } else {
                return isRequired;
            }
        }
    }

    static Map<String, Property> filterNonProvidedRequiredProperties(final Properties tdProperties,
            final ThingModel thingModel,
            final JsonObject propertiesContainer,
            final boolean handleDittoCategory
    ) {
        final Map<String, Property> requiredProperties = extractRequiredTmProperties(tdProperties, thingModel);
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

    static Map<String, Property> extractRequiredTmProperties(final Properties tdProperties,
            final ThingModel thingModel
    ) {
        return thingModel.getTmOptional().map(tmOptionalElements -> {
            final Map<String, Property> allRequiredProperties = new LinkedHashMap<>(tdProperties);
            tmOptionalElements.stream()
                    .map(TmOptionalElement::toString)
                    .filter(el -> el.startsWith(PROPERTIES_PATH_PREFIX))
                    .map(el -> el.replace(PROPERTIES_PATH_PREFIX, ""))
                    .forEach(allRequiredProperties::remove);
            return allRequiredProperties;
        }).orElse(tdProperties);
    }

    static boolean isTmPropertyRequired(final Property property,
            final ThingModel thingModel
    ) {
        return thingModel.getTmOptional()
                .map(tmOptionalElements -> tmOptionalElements.stream()
                        .map(TmOptionalElement::toString)
                        .filter(el -> el.startsWith(PROPERTIES_PATH_PREFIX))
                        .map(el -> el.replace(PROPERTIES_PATH_PREFIX, ""))
                        .noneMatch(el -> property.getPropertyName().equals(el))
                ).orElse(false);
    }

    static CompletableFuture<Void> ensureOnlyDefinedActions(@Nullable final Actions actions,
            final String messageSubject,
            final String containerName,
            final ValidationContext context
    ) {
        final Set<String> allDefinedActionKeys = Optional.ofNullable(actions).map(Actions::keySet).orElseGet(Set::of);
        final boolean messageSubjectIsDefinedAsAction = allDefinedActionKeys.contains(messageSubject);
        if (!messageSubjectIsDefinedAsAction) {
            final var exceptionBuilder = WotThingModelPayloadValidationException
                    .newBuilder("The " + containerName + " message subject <" +
                            messageSubject + "> is not defined as known action in the model: " + allDefinedActionKeys
                    );
            return CompletableFuture.failedFuture(exceptionBuilder
                    .dittoHeaders(context.dittoHeaders())
                    .build());
        }
        return success();
    }

    static CompletableFuture<Void> ensureOnlyDefinedEvents(
            @Nullable final Events events,
            final String messageSubject,
            final String containerName,
            final ValidationContext context
    ) {
        final Set<String> allDefinedEventKeys = Optional.ofNullable(events).map(Events::keySet).orElseGet(Set::of);
        final boolean messageSubjectIsDefinedAsEvent = allDefinedEventKeys.contains(messageSubject);
        if (!messageSubjectIsDefinedAsEvent) {
            final var exceptionBuilder = WotThingModelPayloadValidationException
                    .newBuilder("The " + containerName + " message subject <" +
                            messageSubject + "> is not defined as known event in the model: " + allDefinedEventKeys
                    );
            return CompletableFuture.failedFuture(exceptionBuilder
                    .dittoHeaders(context.dittoHeaders())
                    .build());
        }
        return success();
    }

    static Set<String> determineDittoCategories(final ThingModel thingModel) {
        return determineDittoCategories(thingModel, thingModel.getProperties().orElse(Properties.of(Map.of())));
    }

    static Set<String> determineDittoCategories(final ThingModel thingModel, final Properties properties) {
        final Optional<String> dittoExtensionPrefix = thingModel.getAtContext()
                .determinePrefixFor(SingleUriAtContext.DITTO_WOT_EXTENSION);
        return dittoExtensionPrefix.stream().flatMap(prefix ->
                properties.values().stream().flatMap(jsonFields ->
                        jsonFields.getValue(prefix + ":" + DittoWotExtension.DITTO_WOT_EXTENSION_CATEGORY)
                                .filter(JsonValue::isString)
                                .map(JsonValue::asString)
                                .stream()
                )
        ).collect(Collectors.toCollection(LinkedHashSet::new));
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
            final ValidationContext context
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
                    context
            );
        } else {
            invalidProperties = determineInvalidProperties(tdProperties,
                    p -> propertiesContainer.getValue(p.getPropertyName()),
                    validateRequiredObjectFields,
                    context
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
                    .dittoHeaders(context.dittoHeaders())
                    .build());
        }
        return success();
    }

    static Map<Property, OutputUnit> determineInvalidProperties(final Properties tdProperties,
            final Function<Property, Optional<JsonValue>> propertyExtractor,
            final boolean validateRequiredObjectFields,
            final ValidationContext context
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
                                                context.dittoHeaders()
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
            final Set<String> dittoCategories,
            final ValidationContext context
    ) {
        return findPropertyBasedOnPath(thingModel, tdProperties, propertyPath, handleDittoCategory, dittoCategories)
                .map(propertyWithCategory -> {
                    final JsonValue valueToValidate;
                    if (propertyPath.getLevelCount() > 1) {
                        final int level;
                        if (handleDittoCategory && propertyWithCategory.category() != null) {
                            level = 2;
                        } else {
                            level = 1;
                        }
                        if (propertyPath.getLevelCount() > level) {
                            valueToValidate = JsonObject.newBuilder()
                                    .set(propertyPath.getSubPointer(level).orElseThrow(), propertyValue)
                                    .build();
                        } else {
                            valueToValidate = propertyValue;
                        }
                    } else {
                        valueToValidate = propertyValue;
                    }

                    if (handleDittoCategory) {
                        final Optional<String> dittoCategory = Optional.ofNullable(propertyWithCategory.category());
                        final JsonPointer thePropertyPath = dittoCategory
                                .flatMap(cat -> propertyPath.getSubPointer(1))
                                .orElse(propertyPath);
                        return validateSingleDataSchema(
                                propertyWithCategory.property(),
                                propertyDescription,
                                thePropertyPath,
                                validateRequiredObjectFields,
                                valueToValidate,
                                resourcePath,
                                context
                        );
                    } else {
                        return validateSingleDataSchema(
                                propertyWithCategory.property(),
                                propertyDescription,
                                propertyPath,
                                validateRequiredObjectFields,
                                valueToValidate,
                                resourcePath,
                                context
                        );
                    }
                }).orElseGet(InternalValidation::success);
    }

    private static Optional<PropertyWithCategory> findPropertyBasedOnPath(final ThingModel thingModel,
            final Properties tdProperties,
            final JsonPointer propertyPath,
            final boolean handleDittoCategory,
            final Set<String> dittoCategories
    ) {
        if (handleDittoCategory) {
            return dittoCategories.stream()
                    .filter(category -> propertyPath.getRoot().orElseThrow().toString().equals(category))
                    .filter(category -> propertyPath.getLevelCount() > 1)
                    .map(category ->
                            tdProperties.getProperty(propertyPath.get(1).orElseThrow().toString())
                                    .filter(p -> determineDittoCategory(thingModel, p)
                                            .filter(category::equals)
                                            .isPresent()
                                    )
                                    .map(p -> new PropertyWithCategory(p, category))
                    )
                    .findAny()
                    .orElseGet(() -> tdProperties.getProperty(propertyPath.getRoot().orElseThrow().toString())
                            .map(p -> new PropertyWithCategory(p, null)));
        } else {
            return tdProperties.getProperty(propertyPath.getRoot().orElseThrow().toString())
                    .map(p -> new PropertyWithCategory(p, null));
        }
    }

    static CompletableFuture<Void> enforceActionPayload(final ThingModel thingModel,
            final String messageSubject,
            @Nullable final JsonValue inputPayload,
            final JsonPointer resourcePath,
            final boolean isInput,
            final String validationFailedDescription,
            final ValidationContext context
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
                        context
                ))
                .orElseGet(InternalValidation::success);
    }

    static CompletableFuture<Void> enforceEventPayload(final ThingModel thingModel,
            final String messageSubject,
            @Nullable final JsonValue dataPayload,
            final JsonPointer resourcePath,
            final String validationFailedDescription,
            final ValidationContext context
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
                        context
                ))
                .orElseGet(InternalValidation::success);
    }

    static CompletableFuture<Void> validateSingleDataSchema(final SingleDataSchema dataSchema,
            final String validatedDescription,
            final JsonPointer pointerPath,
            final boolean validateRequiredObjectFields,
            @Nullable final JsonValue jsonValue,
            final JsonPointer resourcePath,
            final ValidationContext context
    ) {
        final OutputUnit validationOutput = JSON_SCHEMA_TOOLS.validateDittoJsonBasedOnDataSchema(
                dataSchema,
                pointerPath,
                validateRequiredObjectFields,
                jsonValue,
                context.dittoHeaders()
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
                    .dittoHeaders(context.dittoHeaders())
                    .build());
        }
        return success();
    }

    static <T> CompletableFuture<T> success() {
        return CompletableFuture.completedFuture(null);
    }

    record PropertyWithCategory(Property property, @Nullable String category) {}
}
