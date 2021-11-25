/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.model;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.Validator;
import org.eclipse.ditto.base.model.entity.validation.NoControlCharactersNoSlashesValidator;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonKeyInvalidException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;

/**
 * Representation of one Feature within Ditto.
 */
@Immutable
final class ImmutableFeature implements Feature {

    private final String featureId;
    @Nullable private final FeatureDefinition definition;
    @Nullable private final FeatureProperties properties;
    @Nullable private final FeatureProperties desiredProperties;

    private ImmutableFeature(final String featureId,
            @Nullable final FeatureDefinition definition,
            @Nullable final FeatureProperties properties,
            @Nullable final FeatureProperties desiredProperties) {

        this.featureId = checkNotNull(featureId, "ID of the Feature");
        this.definition = definition;
        this.properties = properties;
        this.desiredProperties = desiredProperties;
    }

    /**
     * Creates a new Feature with a specified ID.
     *
     * @param featureId the ID.
     * @return the new Feature.
     * @throws NullPointerException if {@code featureId} is {@code null}.
     * @throws JsonKeyInvalidException if {@code featureId} was not valid according to pattern
     * {@link org.eclipse.ditto.base.model.entity.id.RegexPatterns#NO_CONTROL_CHARS_NO_SLASHES_PATTERN}.
     */
    public static ImmutableFeature of(final String featureId) {
        return of(featureId, null, null);
    }

    /**
     * Creates a new Feature with a specified ID and properties.
     *
     * @param featureId the ID.
     * @param properties the properties. Can also be {@code null}.
     * @return the new Feature.
     * @throws NullPointerException if {@code featureId} is {@code null}.
     * @throws JsonKeyInvalidException if {@code featureId} was not valid according to pattern
     * {@link org.eclipse.ditto.base.model.entity.id.RegexPatterns#NO_CONTROL_CHARS_NO_SLASHES_PATTERN}.
     */
    public static ImmutableFeature of(final String featureId, @Nullable final FeatureProperties properties) {
        return of(featureId, null, properties);
    }

    /**
     * Creates a new Feature with a specified ID, Definition and properties.
     *
     * @param featureId the ID.
     * @param definition the Definition or {@code null}.
     * @param properties the properties or {@code null}.
     * @return the new Feature.
     * @throws NullPointerException if {@code featureId} is {@code null}.
     * @throws JsonKeyInvalidException if {@code featureId} was not valid according to pattern
     * {@link org.eclipse.ditto.base.model.entity.id.RegexPatterns#NO_CONTROL_CHARS_NO_SLASHES_PATTERN}.
     */
    public static ImmutableFeature of(final String featureId,
            @Nullable final FeatureDefinition definition,
            @Nullable final FeatureProperties properties) {

        return of(featureId, definition, properties, null);
    }

    /**
     * Creates a new Feature with a specified ID, Definition, properties and desired properties.
     *
     * @param featureId the ID.
     * @param definition the Definition or {@code null}.
     * @param properties the properties or {@code null}.
     * @param desiredProperties the desired properties or {@code null}
     * @return the new Feature.
     * @throws NullPointerException if {@code featureId} is {@code null}.
     * @throws JsonKeyInvalidException if {@code featureId} was not valid according to pattern
     * {@link org.eclipse.ditto.base.model.entity.id.RegexPatterns#NO_CONTROL_CHARS_NO_SLASHES_PATTERN}.
     * @since 1.5.0
     */
    public static ImmutableFeature of(final CharSequence featureId,
            @Nullable final FeatureDefinition definition,
            @Nullable final FeatureProperties properties,
            @Nullable final FeatureProperties desiredProperties) {

        checkNotNull(featureId, "ID of the Feature");

        final Validator validator = NoControlCharactersNoSlashesValidator.getInstance(featureId);
        if (!validator.isValid()) {
            throw JsonKeyInvalidException.newBuilderWithDescription(featureId, validator.getReason().orElse(null))
                    .build();
        }

        return new ImmutableFeature(featureId.toString(), definition, properties, desiredProperties);
    }

    @Override
    public String getId() {
        return featureId;
    }

    @Override
    public Feature setDefinition(final FeatureDefinition featureDefinition) {
        checkNotNull(featureDefinition, "definition to be set");
        if (Objects.equals(definition, featureDefinition)) {
            return this;
        }
        return of(featureId, featureDefinition, properties, desiredProperties);
    }

    @Override
    public Feature removeDefinition() {
        if (null == definition) {
            return this;
        }
        return of(featureId, null, properties, desiredProperties);
    }

    @Override
    public Optional<FeatureProperties> getProperties() {
        return Optional.ofNullable(properties);
    }

    @Override
    public Feature setProperties(final FeatureProperties properties) {
        checkNotNull(properties, "properties to be set");

        if (Objects.equals(this.properties, properties)) {
            return this;
        }

        return ImmutableFeature.of(featureId, definition, properties, desiredProperties);
    }

    @Override
    public Feature removeProperties() {
        if (null == properties) {
            return this;
        }

        return ImmutableFeature.of(featureId, definition, null, desiredProperties);
    }

    @Override
    public Optional<JsonValue> getProperty(final JsonPointer propertyPath) {
        checkNotNull(propertyPath, "JSON path to the property to be retrieved");
        return getProperties().flatMap(props -> props.getValue(propertyPath));
    }

    @Override
    public Feature setProperty(final JsonPointer propertyPath, final JsonValue propertyValue) {
        checkNotNull(propertyPath, "JSON path to the property to be set");
        checkNotNull(propertyValue, "property value to be set");

        final FeatureProperties newFeatureProperties;
        if (null == properties || properties.isEmpty()) {
            newFeatureProperties = ThingsModelFactory.newFeaturePropertiesBuilder()
                    .set(propertyPath, propertyValue)
                    .build();
        } else {
            newFeatureProperties = properties.setValue(propertyPath, propertyValue);
        }

        return setProperties(newFeatureProperties);
    }

    @Override
    public Feature removeProperty(final JsonPointer propertyPath) {
        checkNotNull(propertyPath, "JSON path to the property to be removed");

        if (null == properties || properties.isEmpty()) {
            return this;
        }

        return setProperties(properties.remove(propertyPath));
    }

    @Override
    public Optional<FeatureProperties> getDesiredProperties() {
        return Optional.ofNullable(desiredProperties);
    }

    @Override
    public Feature setDesiredProperties(final FeatureProperties desiredProperties) {
        checkNotNull(desiredProperties, "desired properties to be set");

        if (Objects.equals(this.desiredProperties, desiredProperties)) {
            return this;
        }

        return ImmutableFeature.of(featureId, definition, properties, desiredProperties);
    }

    @Override
    public Feature removeDesiredProperties() {
        if (null == desiredProperties) {
            return this;
        }

        return ImmutableFeature.of(featureId, definition, properties, null);
    }

    @Override
    public Optional<JsonValue> getDesiredProperty(final JsonPointer desiredPropertyPath) {
        checkNotNull(desiredPropertyPath, "JSON path to the desired property to be retrieved");

        return getDesiredProperties().flatMap(props -> props.getValue(desiredPropertyPath));
    }

    @Override
    public Feature setDesiredProperty(final JsonPointer desiredPropertyPath, final JsonValue desiredPropertyValue) {
        checkNotNull(desiredPropertyPath, "JSON path to the desired property to be set");
        checkNotNull(desiredPropertyValue, "desired property value to be set");

        final FeatureProperties newDesiredFeatureProperties;
        if (null == desiredProperties || desiredProperties.isEmpty()) {
            newDesiredFeatureProperties = ThingsModelFactory.newFeaturePropertiesBuilder()
                    .set(desiredPropertyPath, desiredPropertyValue)
                    .build();
        } else {
            newDesiredFeatureProperties = desiredProperties.setValue(desiredPropertyPath, desiredPropertyValue);
        }

        return setDesiredProperties(newDesiredFeatureProperties);
    }

    @Override
    public Feature removeDesiredProperty(final JsonPointer desiredPropertyPath) {
        checkNotNull(desiredPropertyPath, "JSON path to the desired property to be removed");

        if (null == desiredProperties || desiredProperties.isEmpty()) {
            return this;
        }

        return setDesiredProperties(desiredProperties.remove(desiredPropertyPath));
    }

    @Override
    public Optional<FeatureDefinition> getDefinition() {
        return Optional.ofNullable(definition);
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);

        final JsonObjectBuilder jsonObjectBuilder = JsonObject.newBuilder();

        if (null != definition) {
            jsonObjectBuilder.set(JsonFields.DEFINITION, definition.toJson(), predicate);
        }

        if (null != properties) {
            jsonObjectBuilder.set(JsonFields.PROPERTIES, properties, predicate);
        }

        if (null != desiredProperties) {
            jsonObjectBuilder.set(JsonFields.DESIRED_PROPERTIES, desiredProperties, predicate);
        }

        return jsonObjectBuilder.build();
    }

    @Override
    public int hashCode() {
        return Objects.hash(featureId, definition, properties, desiredProperties);
    }

    @SuppressWarnings("squid:MethodCyclomaticComplexity")
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableFeature other = (ImmutableFeature) o;
        return Objects.equals(featureId, other.featureId) &&
                Objects.equals(definition, other.definition) &&
                Objects.equals(properties, other.properties) &&
                Objects.equals(desiredProperties, other.desiredProperties);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [featureId=" + featureId + ", definition=" + definition + ", " +
                "properties=" + properties + ", desiredProperties=" + desiredProperties + "]";
    }

}
