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

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.common.ConditionChecker;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;


/**
 * Representation of one Feature with JSON NULL value within Ditto.
 */
@Immutable
final class NullFeature implements Feature {

    private final String featureId;

    private NullFeature(final String featureId) {
        this.featureId = ConditionChecker.checkNotNull(featureId, "ID");
    }

    /**
     * Creates a new Feature with the specified ID of which value is JSON NULL.
     *
     * @param featureId the ID.
     * @return a new NullFeature.
     * @throws NullPointerException if {@code featureId} is {@code null}.
     */
    public static NullFeature of(final String featureId) {
        return new NullFeature(featureId);
    }

    @Override
    public String getId() {
        return featureId;
    }

    @Override
    public Optional<FeatureDefinition> getDefinition() {
        return Optional.empty();
    }

    @Override
    public Feature setDefinition(final FeatureDefinition featureDefinition) {
        return this;
    }

    @Override
    public Feature removeDefinition() {
        return this;
    }

    @Override
    public Optional<FeatureProperties> getProperties() {
        return Optional.empty();
    }

    @Override
    public Feature setProperties(final FeatureProperties properties) {
        return this;
    }

    @Override
    public Feature removeProperties() {
        return this;
    }

    @Override
    public Optional<JsonValue> getProperty(final JsonPointer pointer) {
        return Optional.empty();
    }

    @Override
    public Feature setProperty(final JsonPointer pointer, final JsonValue propertyValue) {
        return this;
    }

    @Override
    public Feature removeProperty(final JsonPointer pointer) {
        return this;
    }

    @Override
    public JsonObject toJson() {
        return JsonFactory.nullObject();
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        return JsonFactory.nullObject();
    }

    @Override
    public int hashCode() {
        return Objects.hash(featureId);
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
        final NullFeature other = (NullFeature) o;
        return Objects.equals(featureId, other.featureId);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [featureId=" + featureId + "]";
    }

}
