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

import java.util.Collections;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;

/**
 * This class is representation of {@link Feature}s from JSON NULL.
 */
@Immutable
final class NullFeatures implements Features {

    private NullFeatures() {
        super();
    }

    /**
     * Creates a new instance of Features with value JSON NULL.
     *
     * @return a new NullFeatures.
     * @throws NullPointerException if {@code featureId} is {@code null}.
     */
    public static NullFeatures newInstance() {
        return new NullFeatures();
    }

    private static void checkFeatureId(final CharSequence featureId) {
        ConditionChecker.checkNotNull(featureId, "Feature ID");
    }

    @Override
    public Optional<Feature> getFeature(final String featureId) {
        checkFeatureId(featureId);

        return Optional.empty();
    }

    @Override
    public Features setFeature(final Feature feature) {
        return ThingsModelFactory.newFeatures(feature);
    }

    @Override
    @SuppressWarnings("squid:S4144")
    public Features removeFeature(final String featureId) {
        checkFeatureId(featureId);

        return this;
    }

    @Override
    public Features setDefinition(final String featureId, final FeatureDefinition definition) {
        checkFeatureId(featureId);
        ConditionChecker.checkNotNull(definition, "definition to be set");

        return this;
    }

    @Override
    @SuppressWarnings("squid:S4144")
    public Features removeDefinition(final String featureId) {
        checkFeatureId(featureId);

        return this;
    }

    @Override
    public Features setProperties(final String featureId, final FeatureProperties properties) {
        checkFeatureId(featureId);
        ConditionChecker.checkNotNull(properties, "properties to be set");

        return this;
    }

    @Override
    @SuppressWarnings("squid:S4144")
    public Features removeProperties(final String featureId) {
        checkFeatureId(featureId);

        return this;
    }

    @Override
    public Features setProperty(final String featureId, final JsonPointer propertyPath, final JsonValue propertyValue) {
        checkFeatureId(featureId);
        ConditionChecker.checkNotNull(propertyPath, "JSON pointer to the property to be set");
        ConditionChecker.checkNotNull(propertyValue, "value of the property to be set");

        return this;
    }

    @Override
    public Features removeProperty(final String featureId, final JsonPointer propertyPath) {
        checkFeatureId(featureId);
        ConditionChecker.checkNotNull(propertyPath, "JSON pointer to the property to be removed");

        return this;
    }

    @Override
    public Features setDesiredProperties(final CharSequence featureId, final FeatureProperties desiredProperties) {
        checkFeatureId(featureId);
        ConditionChecker.checkNotNull(desiredProperties, "desiredProperties");

        return this;
    }

    @Override
    @SuppressWarnings("squid:S4144")
    public Features removeDesiredProperties(final CharSequence featureId) {
        checkFeatureId(featureId);

        return this;
    }

    @Override
    public Features setDesiredProperty(final CharSequence featureId, final JsonPointer desiredPropertyPath,
            final JsonValue desiredPropertyValue) {

        checkFeatureId(featureId);
        ConditionChecker.checkNotNull(desiredPropertyPath, "desiredPropertyPath");
        ConditionChecker.checkNotNull(desiredPropertyValue, "desiredPropertyValue");

        return this;
    }

    @Override
    public Features removeDesiredProperty(final CharSequence featureId, final JsonPointer desiredPropertyPath) {
        checkFeatureId(featureId);
        ConditionChecker.checkNotNull(desiredPropertyPath, "desiredPropertyPath");

        return this;
    }

    @Override
    public boolean isNull() {
        return true;
    }

    @Override
    public int getSize() {
        return 0;
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public Stream<Feature> stream() {
        return Stream.empty();
    }

    @Override
    public Iterator<Feature> iterator() {
        return Collections.emptyIterator();
    }

    @Override
    public JsonObject toJson(final JsonSchemaVersion schemaVersion, final Predicate<JsonField> thePredicate) {
        return JsonFactory.nullObject();
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getClass().getName());
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        return obj != null && getClass() == obj.getClass();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " []";
    }

}
