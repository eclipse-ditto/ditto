/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.model.entity.metadata;

import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;

/**
 * A mutable builder for a {@link org.eclipse.ditto.base.model.entity.metadata.Metadata} with a fluent API.
 */
@NotThreadSafe
public interface MetadataBuilder extends JsonObjectBuilder {

    /**
     * Sets a new {@code int} metadate to the {@code Metadata} to be built if the specified predicate evaluates to
     * {@code true}.
     *
     * @param key the key of the metadate to be set.
     * @param value the value of the metadate to be set.
     * @param predicate the predicate which finally determines if the metadate is to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code key} or {@code predicate} is {@code null}.
     * @throws IllegalArgumentException if {@code key} is empty.
     */
    @Override
    MetadataBuilder set(CharSequence key, int value, Predicate<JsonField> predicate);

    /**
     * Sets a new {@code int} metadate to the {@code Metadata} to be built.
     *
     * @param key the name of the metadate to be set.
     * @param value the value of the metadate to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code name} is {@code null}.
     * @throws IllegalArgumentException if {@code name} is empty.
     */
    @Override
    default MetadataBuilder set(final CharSequence key, final int value) {
        return set(key, value, field -> true);
    }

    /**
     * Sets a new {@code long} metadate to the {@code Metadata} to be built if the specified predicate evaluates to
     * {@code true}.
     *
     * @param key the name of the metadate to be set.
     * @param value the value of the metadate to be set.
     * @param predicate the predicate which finally determines if the metadate is to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code name} is {@code null}.
     * @throws IllegalArgumentException if {@code name} is empty.
     */
    @Override
    MetadataBuilder set(CharSequence key, long value, Predicate<JsonField> predicate);

    /**
     * Sets a new {@code long} metadate to the {@code Metadata} to be built.
     *
     * @param key the name of the metadate to be set.
     * @param value the value of the metadate to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code name} is {@code null}.
     * @throws IllegalArgumentException if {@code name} is empty.
     */
    @Override
    default MetadataBuilder set(final CharSequence key, final long value) {
        return set(key, value, field -> true);
    }

    /**
     * Sets a new {@code double} value metadate to the {@code Metadata} to be built if the specified predicate
     * evaluates to {@code true}.
     *
     * @param key the name of the metadate to be set.
     * @param value the value of the metadate to be set.
     * @param predicate the predicate which finally determines if the metadate is to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code name} is {@code null}.
     * @throws IllegalArgumentException if {@code name} is empty.
     */
    @Override
    MetadataBuilder set(CharSequence key, double value, Predicate<JsonField> predicate);

    /**
     * Sets a new {@code double} value metadate to the {@code Metadata} to be built.
     *
     * @param key the name of the metadate to be set.
     * @param value the value of the metadate to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code name} is {@code null}.
     * @throws IllegalArgumentException if {@code name} is empty.
     */
    @Override
    default MetadataBuilder set(final CharSequence key, final double value) {
        return set(key, value, field -> true);
    }

    /**
     * Sets a new {@code boolean} value metadate to the {@code Metadata} to be built if the specified predicate
     * evaluates to {@code true}.
     *
     * @param key the name of the metadate to be set.
     * @param value the value of the metadate to be set.
     * @param predicate the predicate which finally determines if the metadate is to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code name} is {@code null}.
     * @throws IllegalArgumentException if {@code name} is empty.
     */
    @Override
    MetadataBuilder set(CharSequence key, boolean value, Predicate<JsonField> predicate);

    /**
     * Sets a new {@code boolean} value metadate to the {@code Metadata} to be built.
     *
     * @param key the name of the metadate to be set.
     * @param value the value of the metadate to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code name} is {@code null}.
     * @throws IllegalArgumentException if {@code name} is empty.
     */
    @Override
    default MetadataBuilder set(final CharSequence key, final boolean value) {
        return set(key, value, field -> true);
    }

    /**
     * Sets a new string value metadate to the {@code Metadata} to be built if the specified predicate evaluates to
     * {@code true}.
     *
     * @param key the name of the metadate to be set.
     * @param value the value of the metadate to be set.
     * @param predicate the predicate which finally determines if the metadate is to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code name} is {@code null}.
     * @throws IllegalArgumentException if {@code name} is empty.
     */
    @Override
    MetadataBuilder set(CharSequence key, @Nullable String value, Predicate<JsonField> predicate);

    /**
     * Sets a new string value metadate to the {@code Metadata} to be built.
     *
     * @param key the name of the metadate to be set.
     * @param value the value of the metadate to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code name} is {@code null}.
     * @throws IllegalArgumentException if {@code name} is empty.
     */
    @Override
    default MetadataBuilder set(final CharSequence key, @Nullable final String value) {
        return set(key, value, field -> true);
    }

    /**
     * Sets a new {@link JsonValue} metadate to the {@code Metadata} to be built if the specified predicate evaluates
     * to {@code true}.
     *
     * @param key the name of the metadate to be set.
     * @param value the value of the metadate to be set.
     * @param predicate the predicate which finally determines if the metadate is to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code name} is empty.
     */
    @Override
    MetadataBuilder set(CharSequence key, JsonValue value, Predicate<JsonField> predicate);

    /**
     * Sets a new {@link JsonValue} metadate to the {@code Metadata} to be built.
     *
     * @param key the name of the metadate to be set.
     * @param value the value of the metadate to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code name} is empty.
     */
    @Override
    default MetadataBuilder set(final CharSequence key, final JsonValue value) {
        return set(key, value, field -> true);
    }

    /**
     * Sets a new {@link JsonValue} metadate to the {@code Metadata} to be built.
     *
     * @param fieldDefinition the field definition of the metadate to be set
     * @param value the value of the metadate to be set.
     * @param predicate the predicate which finally determines if the metadate is to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code name} is empty.
     */
    @Override
    <T> MetadataBuilder set(JsonFieldDefinition<T> fieldDefinition, @Nullable T value, Predicate<JsonField> predicate);

    /**
     * Sets a new {@link JsonValue} metadate to the {@code Metadata} to be built.
     *
     * @param fieldDefinition the field definition of the metadate to be set
     * @param value the value of the metadate to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code name} is empty.
     */
    @Override
    default <T> MetadataBuilder set(final JsonFieldDefinition<T> fieldDefinition, @Nullable final T value) {
        return set(fieldDefinition, value, jsonField -> true);
    }

    /**
     * Sets the specified metadate to the {@code Metadata} to be built if the specified predicate evaluates to
     * {@code true}. If this builder already contains a field with the same key, the existing one will be replaced.
     *
     * @param field the field to be set.
     * @param predicate the predicate which finally determines if the metadate is to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code field} is {@code null}.
     */
    @Override
    MetadataBuilder set(JsonField field, Predicate<JsonField> predicate);

    /**
     * Sets the specified metadate to the {@code Metadata} to be built. If this builder already contains a field with
     * the same key, the existing one will be replaced.
     *
     * @param field the field to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code field} is {@code null}.
     */
    @Override
    default MetadataBuilder set(final JsonField field) {
        return set(field, jsonField -> true);
    }

    /**
     * Removes the metadate from the {@code Metadata} to be built which is associated with the given name. This
     * method has no effect if no such field exists.
     *
     * @param key the name of the metadate to be removed.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code name} is {@code null}.
     */
    @Override
    MetadataBuilder remove(CharSequence key);

    /**
     * Removes the metadate from the {@code Metadata} to be built which is associated with the pointer of the
     * specified JSON field definition. This method has no effect if no such field exists.
     *
     * @param fieldDefinition provides the JSON pointer of the metadate to be deleted.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code fieldDefinition} is {@code null}.
     * @throws IllegalArgumentException if the JSON pointer of {@code fieldDefinition} is empty.
     */
    @Override
    MetadataBuilder remove(JsonFieldDefinition<?> fieldDefinition);

    /**
     * Sets the given {@link JsonField}s to the {@code Metadata} to be built. This method prevents duplicates, i. e.
     * if two fields have the same key, the previous field is replaced by the new field; the position of the new field
     * in the resulting JSON object is the same as the position of the previous field. For each field applies that the
     * field is only set if the specified predicate evaluates to {@code true}.
     *
     * @param fields the fields to set.
     * @param predicate the predicate which finally determines for each field if it is to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code fields} is {@code null}.
     */
    @Override
    MetadataBuilder setAll(Iterable<JsonField> fields, Predicate<JsonField> predicate);

    /**
     * Sets the given {@link JsonField}s to the {@code Metadata} to be built. This method prevents duplicates, i. e.
     * if two fields have the same key, the previous field is replaced by the new field; the position of the new field
     * in the resulting JSON object is the same as the position of the previous field.
     *
     * @param fields the fields to set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code fields} is {@code null}.
     */
    @Override
    default MetadataBuilder setAll(final Iterable<JsonField> fields) {
        return setAll(fields, jsonField -> true);
    }

    /**
     * Removes all fields from the {@code Metadata} to be built.
     *
     * @return this builder to allow method chaining.
     */
    @Override
    MetadataBuilder removeAll();

    /**
     * Creates a new {@link org.eclipse.ditto.base.model.entity.metadata.Metadata} object containing all values which were set to this builder beforehand.
     *
     * @return the new {@code Metadata} object.
     */
    @Override
    Metadata build();

}
