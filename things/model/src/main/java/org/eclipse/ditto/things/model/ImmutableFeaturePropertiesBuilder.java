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

import java.util.Iterator;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Stream;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;


/**
 * A mutable builder for an {@link ImmutableFeatureProperties} with a fluent API.
 */
@NotThreadSafe
final class ImmutableFeaturePropertiesBuilder implements FeaturePropertiesBuilder {

    private final JsonObjectBuilder jsonObjectBuilder;

    private ImmutableFeaturePropertiesBuilder(final JsonObjectBuilder theJsonObjectBuilder) {
        jsonObjectBuilder = theJsonObjectBuilder;
    }

    /**
     * Returns a new empty instance of {@code ImmutableFeaturePropertiesBuilder}.
     *
     * @return a new empty {@code ImmutableFeaturePropertiesBuilder}.
     */
    public static FeaturePropertiesBuilder empty() {
        return new ImmutableFeaturePropertiesBuilder(JsonFactory.newObjectBuilder());
    }

    /**
     * Returns a new instance of {@code ImmutableFeaturePropertiesBuilder} which is initialised with the values of the
     * provided JSON object.
     *
     * @param <T> the type of the JSON object.
     * @param jsonObject the JSON object which provides the initial values of the result.
     * @return a new initialised {@code ImmutableFeaturePropertiesBuilder}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     */
    public static <T extends JsonObject> FeaturePropertiesBuilder of(final T jsonObject) {
        return new ImmutableFeaturePropertiesBuilder(JsonFactory.newObjectBuilder(jsonObject));
    }

    @Override
    public FeaturePropertiesBuilder set(final CharSequence key, final int value, final Predicate<JsonField> predicate) {
        jsonObjectBuilder.set(key, value, predicate);
        return this;
    }

    @Override
    public FeaturePropertiesBuilder set(final CharSequence key, final long value,
            final Predicate<JsonField> predicate) {

        jsonObjectBuilder.set(key, value, predicate);
        return this;
    }

    @Override
    public FeaturePropertiesBuilder set(final CharSequence key, final double value,
            final Predicate<JsonField> predicate) {

        jsonObjectBuilder.set(key, value, predicate);
        return this;
    }

    @Override
    public FeaturePropertiesBuilder set(final CharSequence key, final boolean value,
            final Predicate<JsonField> predicate) {

        jsonObjectBuilder.set(key, value, predicate);
        return this;
    }

    @Override
    public FeaturePropertiesBuilder set(final CharSequence key, @Nullable final String value,
            final Predicate<JsonField> predicate) {

        jsonObjectBuilder.set(key, value, predicate);
        return this;
    }

    @Override
    public FeaturePropertiesBuilder set(final CharSequence key, final JsonValue value,
            final Predicate<JsonField> predicate) {

        jsonObjectBuilder.set(key, value, predicate);
        return this;
    }

    @Override
    public <T> FeaturePropertiesBuilder set(final JsonFieldDefinition<T> fieldDefinition, @Nullable final T value,
            final Predicate<JsonField> predicate) {

        jsonObjectBuilder.set(fieldDefinition, value, predicate);
        return this;
    }

    @Override
    public FeaturePropertiesBuilder set(final JsonField field, final Predicate<JsonField> predicate) {
        jsonObjectBuilder.set(field, predicate);
        return this;
    }

    @Override
    public FeaturePropertiesBuilder remove(final CharSequence key) {
        jsonObjectBuilder.remove(key);
        return this;
    }

    @Override
    public FeaturePropertiesBuilder remove(final JsonFieldDefinition<?> fieldDefinition) {
        jsonObjectBuilder.remove(fieldDefinition);
        return this;
    }

    @Override
    public FeaturePropertiesBuilder setAll(final Iterable<JsonField> fields, final Predicate<JsonField> predicate) {
        jsonObjectBuilder.setAll(fields, predicate);
        return this;
    }

    @Override
    public FeaturePropertiesBuilder removeAll() {
        jsonObjectBuilder.removeAll();
        return this;
    }

    @Override
    public Iterator<JsonField> iterator() {
        return jsonObjectBuilder.iterator();
    }

    @Override
    public FeatureProperties build() {
        final JsonObject featurePropertiesJsonObject = jsonObjectBuilder.build();
        return ImmutableFeatureProperties.of(featurePropertiesJsonObject);
    }

    @Override
    public boolean isEmpty() {
        return jsonObjectBuilder.isEmpty();
    }

    @Override
    public int getSize() {
        return jsonObjectBuilder.getSize();
    }

    @Override
    public Stream<JsonField> stream() {
        return jsonObjectBuilder.stream();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ImmutableFeaturePropertiesBuilder that = (ImmutableFeaturePropertiesBuilder) o;
        return Objects.equals(jsonObjectBuilder, that.jsonObjectBuilder);
    }

    @Override
    public int hashCode() {
        return Objects.hash(jsonObjectBuilder);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "jsonObjectBuilder=" + jsonObjectBuilder +
                "]";
    }

}
