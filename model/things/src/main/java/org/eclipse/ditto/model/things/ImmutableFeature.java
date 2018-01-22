/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.model.things;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.common.ConditionChecker;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;

/**
 * Representation of one Feature within Ditto.
 */
@Immutable
final class ImmutableFeature implements Feature {

    private final String featureId;
    @Nullable private final FeatureDefinition definition;
    @Nullable private final FeatureProperties properties;

    private ImmutableFeature(final String featureId, @Nullable final FeatureDefinition definition,
            @Nullable final FeatureProperties properties) {

        this.featureId = ConditionChecker.checkNotNull(featureId, "ID of the Feature");
        this.definition = definition;
        this.properties = properties;
    }

    /**
     * Creates a new Feature with a specified ID.
     *
     * @param featureId the ID.
     * @return the new Feature.
     * @throws NullPointerException if {@code featureId} is {@code null}.
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
     */
    public static ImmutableFeature of(final String featureId, @Nullable final FeatureDefinition definition,
            @Nullable final FeatureProperties properties) {

        return new ImmutableFeature(featureId, definition, properties);
    }

    @Override
    public String getId() {
        return featureId;
    }

    @Override
    public Feature setDefinition(final FeatureDefinition featureDefinition) {
        ConditionChecker.checkNotNull(featureDefinition, "definition to be set");
        if (Objects.equals(definition, featureDefinition)) {
            return this;
        }
        return of(featureId, featureDefinition, properties);
    }

    @Override
    public Feature removeDefinition() {
        if (null == definition) {
            return this;
        }
        return of(featureId, properties);
    }

    @Override
    public Optional<FeatureProperties> getProperties() {
        return Optional.ofNullable(properties);
    }

    @Override
    public Feature setProperties(final FeatureProperties properties) {
        ConditionChecker.checkNotNull(properties, "properties to be set");

        if (Objects.equals(this.properties, properties)) {
            return this;
        }

        return ImmutableFeature.of(featureId, definition, properties);
    }

    @Override
    public Feature removeProperties() {
        if (null == properties) {
            return this;
        }

        return ImmutableFeature.of(featureId, definition, null);
    }

    @Override
    public Optional<JsonValue> getProperty(final JsonPointer propertyPath) {
        ConditionChecker.checkNotNull(propertyPath, "JSON path to the property to be retrieved");

        return getProperties().flatMap(props -> props.getValue(propertyPath));
    }

    @Override
    public Feature setProperty(final JsonPointer propertyPath, final JsonValue propertyValue) {
        ConditionChecker.checkNotNull(propertyPath, "JSON path to the property to be set");
        ConditionChecker.checkNotNull(propertyValue, "property value to be set");

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
        ConditionChecker.checkNotNull(propertyPath, "JSON path to the property to be removed");

        if (null == properties || properties.isEmpty()) {
            return this;
        }

        return setProperties(properties.remove(propertyPath));
    }

    @Override
    public Optional<FeatureDefinition> getDefinition() {
        return Optional.ofNullable(definition);
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        final Predicate<JsonField> predicate = schemaVersion.and(thePredicate);
        final JsonObjectBuilder jsonObjectBuilder = JsonFactory.newObjectBuilder()
                .set(JsonFields.SCHEMA_VERSION, schemaVersion.toInt(), predicate);

        if (null != definition) {
            jsonObjectBuilder.set(JsonFields.DEFINITION, definition.toJson(), predicate);
        }

        if (null != properties) {
            jsonObjectBuilder.set(JsonFields.PROPERTIES, properties, predicate);
        }

        return jsonObjectBuilder.build();
    }

    @Override
    public int hashCode() {
        return Objects.hash(featureId, definition, properties);
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
        return Objects.equals(featureId, other.featureId) && Objects.equals(definition, other.definition) &&
                Objects.equals(properties, other.properties);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [featureId=" + featureId + ", definition=" + definition + ", " +
                "properties=" + properties + "]";
    }

}
