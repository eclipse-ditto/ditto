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
package org.eclipse.ditto.json;

import java.util.function.Predicate;

import javax.annotation.Nullable;

/**
 * A mutable builder for a {@link JsonObject}. Implementations of this interface are normally not thread safe and not
 * reusable.
 * The order in which the key-value-pairs are set is preserved in the resulting JSON object.
 */
public interface JsonObjectBuilder extends JsonValueContainer<JsonField> {

    /**
     * Sets a new int value field to the JSON object to be built if the specified predicate evaluates to {@code true}.
     *
     * @param key the JSON key or JSON pointer of the field to be set.
     * @param value the value to be set.
     * @param predicate the predicate which finally determines if the value is to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code keyOrPointer} or {@code predicate} is {@code null}.
     * @throws IllegalArgumentException if {@code key} is empty or if {@code key} is an empty JsonPointer. Setting a
     * value with slash as JsonKey object explicitly works.
     */
    JsonObjectBuilder set(CharSequence key, int value, Predicate<JsonField> predicate);

    /**
     * Sets a new int value field to the JSON object to be built.
     *
     * @param key the JSON key or JSON pointer of the field to be set.
     * @param value the value to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code key} is {@code null}.
     * @throws IllegalArgumentException if {@code key} is empty or if {@code key} is an empty JsonPointer. Setting a
     * value with slash as JsonKey object explicitly works.
     */
    default JsonObjectBuilder set(final CharSequence key, final int value) {
        return set(key, value, field -> true);
    }

    /**
     * Sets a new long value field to the JSON object to be built if the specified predicate evaluates to {@code true}.
     *
     * @param key the JSON key or JSON pointer of the field to be set.
     * @param value the value to be set.
     * @param predicate the predicate which finally determines if the value is to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code key} or {@code predicate} is {@code null}.
     * @throws IllegalArgumentException if {@code key} is empty or if {@code key} is an empty JsonPointer. Setting a
     * value with slash as JsonKey object explicitly works.
     */
    JsonObjectBuilder set(CharSequence key, long value, Predicate<JsonField> predicate);

    /**
     * Sets a new long value field to the JSON object to be built.
     *
     * @param key the JSON key or JSON pointer of the field to be set.
     * @param value the value to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code key} is {@code null}.
     * @throws IllegalArgumentException if {@code key} is empty or if {@code key} is an empty JsonPointer. Setting a
     * value with slash as JsonKey object explicitly works.
     */
    default JsonObjectBuilder set(final CharSequence key, final long value) {
        return set(key, value, field -> true);
    }

    /**
     * Sets a new double value field to the JSON object to be built if the specified predicate evaluates to
     * {@code true}.
     *
     * @param key the JSON key or JSON pointer of the field to be set.
     * @param value the value to be set.
     * @param predicate the predicate which finally determines if the field is to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code key} or {@code predicate} is {@code null}.
     * @throws IllegalArgumentException if {@code key} is empty or if {@code key} is an empty JsonPointer. Setting a
     * value with slash as JsonKey object explicitly works.
     */
    JsonObjectBuilder set(CharSequence key, double value, Predicate<JsonField> predicate);

    /**
     * Sets a new double value field to the JSON object to be built.
     *
     * @param key the JSON key or JSON pointer of the field to be set.
     * @param value the value to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code key} is {@code null}.
     * @throws IllegalArgumentException if {@code key} is empty or if {@code key} is an empty JsonPointer. Setting a
     * value with slash as JsonKey object explicitly works.
     */
    default JsonObjectBuilder set(final CharSequence key, final double value) {
        return set(key, value, field -> true);
    }

    /**
     * Sets a new boolean value field to the JSON object to be built if the specified predicate evaluates to
     * {@code true}.
     *
     * @param key the JSON key or JSON pointer of the field to be set.
     * @param value the value to be set.
     * @param predicate the predicate which finally determines if the value is to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code key} or {@code predicate} is {@code null}.
     * @throws IllegalArgumentException if {@code key} is empty or if {@code key} is an empty JsonPointer. Setting a
     * value with slash as JsonKey object explicitly works.
     */
    JsonObjectBuilder set(CharSequence key, boolean value, Predicate<JsonField> predicate);

    /**
     * Sets a new boolean value field to the JSON object to be built.
     *
     * @param key the JSON key or JSON pointer of the field to be set.
     * @param value the value to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code key} is {@code null}.
     * @throws IllegalArgumentException if {@code key} is empty or if {@code key} is an empty JsonPointer. Setting a
     * value with slash as JsonKey object explicitly works.
     */
    default JsonObjectBuilder set(final CharSequence key, final boolean value) {
        return set(key, value, field -> true);
    }

    /**
     * Sets a new string value field to the JSON object to be built if the specified predicate evaluates to {@code
     * true}.
     *
     * @param key the JSON key or JSON pointer of the field to be set.
     * @param value the value to be set.
     * @param predicate the predicate which finally determines if the field is to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code key} or {@code predicate} is {@code null}.
     * @throws IllegalArgumentException if {@code key} is empty or if {@code key} is an empty JsonPointer. Setting a
     * value with slash as JsonKey object explicitly works.
     */
    JsonObjectBuilder set(CharSequence key, @Nullable String value, Predicate<JsonField> predicate);

    /**
     * Sets a new string value field to the JSON object to be built.
     *
     * @param key the JSON key or JSON pointer of the field to be set.
     * @param value the value to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code key} is {@code null}.
     * @throws IllegalArgumentException if {@code key} is empty or if {@code key} is an empty JsonPointer. Setting a
     * value with slash as JsonKey object explicitly works.
     */
    default JsonObjectBuilder set(final CharSequence key, @Nullable final String value) {
        return set(key, value, field -> true);
    }

    /**
     * Sets a new {@link JsonValue} field to the JSON object to be built if the specified predicate evaluates to {@code
     * true}.
     *
     * @param key the JSON key or JSON pointer of the field to be set.
     * @param value the value to be set.
     * @param predicate the predicate which finally determines if the field is to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if any argument is {@code null}; the predicate is evaluated before the null check
     * of the value.
     * @throws IllegalArgumentException if {@code key} is empty or if {@code key} is an empty JsonPointer. Setting a
     * value with slash as JsonKey object explicitly works.
     */
    JsonObjectBuilder set(CharSequence key, JsonValue value, Predicate<JsonField> predicate);

    /**
     * Sets a new {@link JsonValue} field to the JSON object to be built.
     *
     * @param key the JSON key or JSON pointer of the field to be set.
     * @param value the value of the field to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code key} is empty or if {@code key} is an empty JsonPointer. Setting a
     * value with slash as JsonKey object explicitly works.
     */
    default JsonObjectBuilder set(final CharSequence key, final JsonValue value) {
        return set(key, value, field -> true);
    }

    /**
     * Sets a new field to the JSON object to be built if the specified predicate evaluates to {@code true}.
     *
     * @param fieldDefinition defines the location via JsonPointer, the Java value type and optional
     * JsonFieldMarkers of the field to be set.
     * @param value the value of the field to be set.
     * @param <T> the type of {@code value}.
     * @param predicate the predicate which finally determines if the field is to be set. The field was derived from
     * {@code fieldDefinition} and {@code value}.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code fieldDefinition} or {@code predicate} is null.
     * @throws IllegalArgumentException if the value type of {@code fieldDefinition} is not a {@code int}.
     * @see #set(CharSequence, int)
     */
    <T> JsonObjectBuilder set(JsonFieldDefinition<T> fieldDefinition, @Nullable T value,
            Predicate<JsonField> predicate);

    /**
     * Sets a new field to the JSON object to be built.
     *
     * @param fieldDefinition defines the location via JsonPointer, the Java value type and optional
     * JsonFieldMarkers of the field to be set.
     * @param value the value of the field to be set.
     * @param <T> the type of {@code value}.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code fieldDefinition} or {@code predicate} is null.
     * @throws IllegalArgumentException if the value type of {@code fieldDefinition} is not a {@code int}.
     * @see #set(CharSequence, int)
     */
    default <T> JsonObjectBuilder set(final JsonFieldDefinition<T> fieldDefinition, @Nullable final T value) {
        return set(fieldDefinition, value, jsonField -> true);
    }

    /**
     * Sets the specified field to the JSON object to be built if the specified predicate evaluates to {@code true}. If
     * this builder already contains a field with the same key, the existing one will be replaced.
     *
     * @param field the field to be set.
     * @param predicate the predicate which finally determines if the field is to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if any argument is {@code null}.
     */
    JsonObjectBuilder set(JsonField field, Predicate<JsonField> predicate);

    /**
     * Sets the specified field to the JSON object to be built. If this builder already contains a field with the same
     * key, the existing one will be replaced.
     *
     * @param field the field to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code field} is {@code null}.
     */
    default JsonObjectBuilder set(final JsonField field) {
        return set(field, jsonField -> true);
    }

    /**
     * Removes the field from the JSON object to be built which is associated with the given key. This method has no
     * effect if no such field exists.
     *
     * @param key the key of the field to be removed.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code key} is {@code null}.
     */
    JsonObjectBuilder remove(CharSequence key);

    /**
     * Removes the field from the JSON object to be built which is associated with the pointer of the specified JSON
     * field definition. This method has no effect if no such field exists.
     *
     * @param fieldDefinition provides the JSON pointer of the field to be deleted.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code fieldDefinition} is {@code null}.
     * @throws IllegalArgumentException if the JSON pointer of {@code fieldDefinition} is empty.
     */
    JsonObjectBuilder remove(JsonFieldDefinition<?> fieldDefinition);

    /**
     * Sets the given {@link JsonField}s to the JSON object to be built. This method prevents duplicates, i. e. if two
     * fields have the same key, the previous field is replaced by the new field; the position of the new field in the
     * resulting JSON object is the same as the position of the previous field. For each field applies that the field is
     * only set if the specified predicate evaluates to {@code true}.
     *
     * @param fields the fields to set.
     * @param predicate the predicate which finally determines for each field if it is to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if any argument is {@code null}.
     */
    JsonObjectBuilder setAll(Iterable<JsonField> fields, Predicate<JsonField> predicate);

    /**
     * Sets the given {@link JsonField}s to the JSON object to be built. This method prevents duplicates, i. e. if two
     * fields have the same key, the previous field is replaced by the new field; the position of the new field in the
     * resulting JSON object is the same as the position of the previous field.
     *
     * @param fields the fields to set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code fields} is {@code null}.
     */
    default JsonObjectBuilder setAll(final Iterable<JsonField> fields) {
        return setAll(fields, field -> true);
    }

    /**
     * Removes all fields from the JSON object to be built.
     *
     * @return this builder to allow method chaining.
     */
    JsonObjectBuilder removeAll();

    /**
     * Creates a new {@link JsonObject} containing all values which were added beforehand.
     *
     * @return a new JSON object.
     */
    JsonObject build();

}
