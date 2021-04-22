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

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonKeyInvalidException;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.base.model.common.Validator;
import org.eclipse.ditto.base.model.entity.validation.NoControlCharactersNoSlashesValidator;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;


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
        ConditionChecker.checkNotNull(featureId, "ID of the Feature");

        final Validator validator = NoControlCharactersNoSlashesValidator.getInstance(featureId);
        if (!validator.isValid()) {
            throw JsonKeyInvalidException.newBuilderWithDescription(featureId, validator.getReason().orElse(null))
                    .build();
        }
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
    public Optional<FeatureProperties> getDesiredProperties() {
        return Optional.empty();
    }

    @Override
    public Feature setDesiredProperties(final FeatureProperties desiredProperties) {
        return this;
    }

    @Override
    public Feature removeDesiredProperties() {
        return this;
    }

    @Override
    public Optional<JsonValue> getDesiredProperty(final JsonPointer pointer) {
        return Optional.empty();
    }

    @Override
    public Feature setDesiredProperty(final JsonPointer pointer, final JsonValue desiredPropertyValue) {
        return this;
    }

    @Override
    public Feature removeDesiredProperty(final JsonPointer pointer) {
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
