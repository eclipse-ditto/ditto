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
package org.eclipse.ditto.json;

import java.util.function.Predicate;

/**
 * A mutable builder for a {@link JsonObject}. Implementations of this interface are normally not thread safe and not
 * reusable.
 */
public interface JsonObjectBuilder extends Iterable<JsonField> {

    /**
     * Sets a new int value field to the JSON object to be built if the specified predicate evaluates to {@code true}.
     *
     * @param key the key of the value to be set.
     * @param value the value to be set.
     * @param predicate the predicate which finally determines if the value is to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code key} or {@code predicate} is {@code null}.
     * @throws IllegalArgumentException if {@code key} is empty.
     */
    JsonObjectBuilder set(CharSequence key, int value, Predicate<JsonField> predicate);

    /**
     * Sets a new int value field to the JSON object to be built.
     *
     * @param key the key of the value to be set.
     * @param value the value to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code key} is {@code null}.
     * @throws IllegalArgumentException if {@code key} is empty.
     */
    default JsonObjectBuilder set(final CharSequence key, final int value) {
        return set(key, value, field -> true);
    }

//    /**
//     * Sets a new int value field to the JSON object to be built if the specified predicate evaluates to {@code true}.
//     *
//     * @param pointer the pointer to the field to be set.
//     * @param value the value of the field to be set.
//     * @param predicate the predicate which finally determines if the field is to be set.
//     * @return this builder to allow method chaining.
//     * @throws NullPointerException if {@code pointer} is {@code null}.
//     * @throws IllegalArgumentException if {@code pointer} is empty.
//     */
//    JsonObjectBuilder set(JsonPointer pointer, int value, Predicate<JsonField> predicate);
//
//    /**
//     * Sets a new int value field to the JSON object to be built.
//     *
//     * @param pointer the pointer to the field to be set.
//     * @param value the value of the field to be set.
//     * @return this builder to allow method chaining.
//     * @throws NullPointerException if {@code pointer} is {@code null}.
//     * @throws IllegalArgumentException if {@code pointer} is empty.
//     */
//    default JsonObjectBuilder set(final JsonPointer pointer, final int value) {
//        return set(pointer, value, field -> true);
//    }

    /**
     * Sets a new long value field to the JSON object to be built if the specified predicate evaluates to {@code true}.
     *
     * @param key the name of the value to be set.
     * @param value the value to be set.
     * @param predicate the predicate which finally determines if the value is to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code key} or {@code predicate} is {@code null}.
     * @throws IllegalArgumentException if {@code key} is empty.
     */
    JsonObjectBuilder set(CharSequence key, long value, Predicate<JsonField> predicate);

    /**
     * Sets a new long value field to the JSON object to be built.
     *
     * @param key the key of the value to be set.
     * @param value the value to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code key} is {@code null}.
     * @throws IllegalArgumentException if {@code key} is empty.
     */
    default JsonObjectBuilder set(final CharSequence key, final long value) {
        return set(key, value, field -> true);
    }

//    /**
//     * Sets a new long value field to the JSON object to be built if the specified predicate evaluates to {@code true}.
//     *
//     * @param pointer the pointer to the field to be set.
//     * @param value the value of the field to be set.
//     * @param predicate the predicate which finally determines if the field is to be set.
//     * @return this builder to allow method chaining.
//     * @throws NullPointerException if {@code pointer} is {@code null}.
//     * @throws IllegalArgumentException if {@code pointer} is empty.
//     */
//    JsonObjectBuilder set(JsonPointer pointer, long value, Predicate<JsonField> predicate);
//
//    /**
//     * Sets a new long value field to the JSON object to be built.
//     *
//     * @param pointer the pointer to the field to be set.
//     * @param value the value of the field to be set.
//     * @return this builder to allow method chaining.
//     * @throws NullPointerException if {@code pointer} is {@code null}.
//     * @throws IllegalArgumentException if {@code pointer} is empty.
//     */
//    default JsonObjectBuilder set(final JsonPointer pointer, final long value) {
//        return set(pointer, value, field -> true);
//    }

    /**
     * Sets a new double value field to the JSON object to be built if the specified predicate evaluates to
     * {@code true}.
     *
     * @param key the key of the value to be set.
     * @param value the value to be set.
     * @param predicate the predicate which finally determines if the field is to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code key} is {@code null}.
     * @throws IllegalArgumentException if {@code key} is empty.
     */
    JsonObjectBuilder set(CharSequence key, double value, Predicate<JsonField> predicate);

    /**
     * Sets a new double value field to the JSON object to be built.
     *
     * @param key the key of the value to be set.
     * @param value the value to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code key} is {@code null}.
     * @throws IllegalArgumentException if {@code key} is empty.
     */
    default JsonObjectBuilder set(final CharSequence key, final double value) {
        return set(key, value, field -> true);
    }

//    /**
//     * Sets a new double value field to the JSON object to be built if the specified predicate evaluates to {@code
//     * true}.
//     *
//     * @param pointer the pointer to the field to be set.
//     * @param value the value of the field to be set.
//     * @param predicate the predicate which finally determines if the field is to be set.
//     * @return this builder to allow method chaining.
//     * @throws NullPointerException if {@code pointer} is {@code null}.
//     */
//    JsonObjectBuilder set(JsonPointer pointer, double value, Predicate<JsonField> predicate);
//
//    /**
//     * Sets a new double value field to the JSON object to be built.
//     *
//     * @param pointer the pointer to the field to be set.
//     * @param value the value of the field to be set.
//     * @return this builder to allow method chaining.
//     * @throws NullPointerException if {@code pointer} is {@code null}.
//     * @throws IllegalArgumentException if {@code pointer} is empty.
//     */
//    default JsonObjectBuilder set(final JsonPointer pointer, final double value) {
//        return set(pointer, value, field -> true);
//    }

    /**
     * Sets a new boolean value field to the JSON object to be built if the specified predicate evaluates to
     * {@code true}.
     *
     * @param key the key of the value to be set.
     * @param value the value to be set.
     * @param predicate the predicate which finally determines if the value is to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code key} is {@code null}.
     * @throws IllegalArgumentException if {@code key} is empty.
     */
    JsonObjectBuilder set(CharSequence key, boolean value, Predicate<JsonField> predicate);

    /**
     * Sets a new boolean value field to the JSON object to be built.
     *
     * @param key the key of the value to be set.
     * @param value the value to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code key} is {@code null}.
     * @throws IllegalArgumentException if {@code key} is empty.
     */
    default JsonObjectBuilder set(final CharSequence key, final boolean value) {
        return set(key, value, field -> true);
    }

//    /**
//     * Sets a new boolean value field to the JSON object to be built if the specified predicate evaluates to {@code
//     * true}.
//     *
//     * @param pointer the pointer to the field to be set.
//     * @param value the value of the field to be set.
//     * @param predicate the predicate which finally determines if the field is to be set.
//     * @return this builder to allow method chaining.
//     * @throws NullPointerException if {@code pointer} is {@code null}.
//     * @throws IllegalArgumentException if {@code pointer} is empty.
//     */
//    JsonObjectBuilder set(JsonPointer pointer, boolean value, Predicate<JsonField> predicate);
//
//    /**
//     * Sets a new boolean value field to the JSON object to be built.
//     *
//     * @param pointer the pointer to the field to be set.
//     * @param value the value of the field to be set.
//     * @return this builder to allow method chaining.
//     * @throws NullPointerException if {@code pointer} is {@code null}.
//     * @throws IllegalArgumentException if {@code pointer} is empty.
//     */
//    default JsonObjectBuilder set(final JsonPointer pointer, final boolean value) {
//        return set(pointer, value, field -> true);
//    }

    /**
     * Sets a new string value field to the JSON object to be built if the specified predicate evaluates to {@code
     * true}.
     *
     * @param key the key of the value to be set.
     * @param value the value to be set.
     * @param predicate the predicate which finally determines if the field is to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code key} is {@code null}.
     * @throws IllegalArgumentException if {@code key} is empty.
     */
    JsonObjectBuilder set(CharSequence key, String value, Predicate<JsonField> predicate);

    /**
     * Sets a new string value field to the JSON object to be built.
     *
     * @param key the key of the value to be set.
     * @param value the value to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code key} is {@code null}.
     * @throws IllegalArgumentException if {@code key} is empty.
     */
    default JsonObjectBuilder set(final CharSequence key, final String value) {
        return set(key, value, field -> true);
    }

//    /**
//     * Sets a new string value field to the JSON object to be built if the specified predicate evaluates to {@code
//     * true}.
//     *
//     * @param pointer the pointer to the field to be set.
//     * @param value the value of the field to be set.
//     * @param predicate the predicate which finally determines if the field is to be set.
//     * @return this builder to allow method chaining.
//     * @throws NullPointerException if {@code pointer} is {@code null}.
//     */
//    JsonObjectBuilder set(JsonPointer pointer, String value, Predicate<JsonField> predicate);
//
//    /**
//     * Sets a new string value field to the JSON object to be built.
//     *
//     * @param pointer the pointer to the field to be set.
//     * @param value the value of the field to be set.
//     * @return this builder to allow method chaining.
//     * @throws NullPointerException if {@code pointer} is {@code null}.
//     * @throws IllegalArgumentException if {@code pointer} is empty.
//     */
//    default JsonObjectBuilder set(final JsonPointer pointer, final String value) {
//        return set(pointer, value, field -> true);
//    }

    /**
     * Sets a new {@link JsonValue} field to the JSON object to be built if the specified predicate evaluates to {@code
     * true}.
     *
     * @param key the key of the value to be set.
     * @param value the value to be set.
     * @param predicate the predicate which finally determines if the field is to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if any argument is {@code null}; the predicate is evaluated before the null check
     * of the value.
     * @throws IllegalArgumentException if {@code key} is empty.
     */
    JsonObjectBuilder set(CharSequence key, JsonValue value, Predicate<JsonField> predicate);

    /**
     * Sets a new {@link JsonValue} field to the JSON object to be built.
     *
     * @param key the key of the field to be set.
     * @param value the value of the field to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code key} is empty.
     */
    default JsonObjectBuilder set(final CharSequence key, final JsonValue value) {
        return set(key, value, field -> true);
    }

//    /**
//     * Sets a new {@link JsonValue} field to the JSON object to be built if the specified predicate evaluates to {@code
//     * true}.
//     *
//     * @param pointer the pointer to the field to be set.
//     * @param value the value of the field to be set.
//     * @param predicate the predicate which finally determines if the field is to be set.
//     * @return this builder to allow method chaining.
//     * @throws NullPointerException if any argument is {@code null}.
//     * @throws IllegalArgumentException if {@code pointer} is empty.
//     */
//    JsonObjectBuilder set(JsonPointer pointer, JsonValue value, Predicate<JsonField> predicate);
//
//    /**
//     * Sets a new {@link JsonValue} field to the JSON object to be built.
//     *
//     * @param pointer the pointer to the field to be set.
//     * @param value the value of the field to be set.
//     * @return this builder to allow method chaining.
//     * @throws NullPointerException if any argument is {@code null}.
//     * @throws IllegalArgumentException if {@code pointer} is empty.
//     */
//    default JsonObjectBuilder set(final JsonPointer pointer, final JsonValue value) {
//        return set(pointer, value, field -> true);
//    }

    /**
     * Sets a new int value field to the JSON object to be built if the specified predicate evaluates to
     * {@code true}.
     *
     * @param fieldDefinition this field definition provides the JSON pointer to the field to be set.
     * @param value the value of the field to be set.
     * @param predicate the predicate which finally determines if the field is to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code fieldDefinition} is null.
     * @see #set(CharSequence, int)
     */
    JsonObjectBuilder set(JsonFieldDefinition fieldDefinition, int value, Predicate<JsonField> predicate);

    /**
     * Sets a new int value field to the JSON object to be built.
     *
     * @param fieldDefinition this field definition provides the JSON pointer to the field to be set.
     * @param value the value of the field to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code fieldDefinition} is null.
     * @see #set(CharSequence, int)
     */
    default JsonObjectBuilder set(final JsonFieldDefinition fieldDefinition, final int value) {
        return set(fieldDefinition, value, field -> true);
    }

    /**
     * Sets a new long value field to the JSON object to be built if the specified predicate evaluates to
     * {@code true}.
     *
     * @param fieldDefinition this field definition provides the JSON pointer to the field to be set.
     * @param value the value of the field to be set.
     * @param predicate the predicate which finally determines if the field is to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code fieldDefinition} is null.
     * @see #set(CharSequence, long)
     */
    JsonObjectBuilder set(JsonFieldDefinition fieldDefinition, long value, Predicate<JsonField> predicate);

    /**
     * Sets a new long value field to the JSON object to be built.
     *
     * @param fieldDefinition this field definition provides the JSON pointer to the field to be set.
     * @param value the value of the field to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code fieldDefinition} is null.
     * @see #set(CharSequence, long)
     */
    default JsonObjectBuilder set(final JsonFieldDefinition fieldDefinition, final long value) {
        return set(fieldDefinition, value, field -> true);
    }

    /**
     * Sets a new double value field to the JSON object to be built if the specified predicate evaluates to {@code
     * true}.
     *
     * @param fieldDefinition this field definition provides the JSON pointer to the field to be set.
     * @param value the value of the field to be set.
     * @param predicate the predicate which finally determines if the field is to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code fieldDefinition} is null.
     * @see #set(CharSequence, double)
     */
    JsonObjectBuilder set(JsonFieldDefinition fieldDefinition, double value, Predicate<JsonField> predicate);

    /**
     * Sets a new double value field to the JSON object to be built.
     *
     * @param fieldDefinition this field definition provides the JSON pointer to the field to be set.
     * @param value the value of the field to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code fieldDefinition} is null.
     * @see #set(CharSequence, double)
     */
    default JsonObjectBuilder set(final JsonFieldDefinition fieldDefinition, final double value) {
        return set(fieldDefinition, value, field -> true);
    }

    /**
     * Sets a new boolean value field to the JSON object to be built if the specified predicate evaluates to {@code
     * true}.
     *
     * @param fieldDefinition this field definition provides the JSON pointer to the field to be set.
     * @param value the value of the field to be set.
     * @param predicate the predicate which finally determines if the field is to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code fieldDefinition} is null.
     * @see #set(CharSequence, boolean)
     */
    JsonObjectBuilder set(JsonFieldDefinition fieldDefinition, boolean value, Predicate<JsonField> predicate);

    /**
     * Sets a new boolean value field to the JSON object to be built.
     *
     * @param fieldDefinition this field definition provides the JSON pointer to the field to be set.
     * @param value the value of the field to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code fieldDefinition} is null.
     * @see #set(CharSequence, boolean)
     */
    default JsonObjectBuilder set(final JsonFieldDefinition fieldDefinition, final boolean value) {
        return set(fieldDefinition, value, field -> true);
    }

    /**
     * Sets a new string value field to the JSON object to be built if the specified predicate evaluates to {@code
     * true}.
     *
     * @param fieldDefinition this field definition provides the JSON pointer to the field to be set.
     * @param value the value of the field to be set.
     * @param predicate the predicate which finally determines if the value is to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code fieldDefinition} is null.
     * @see #set(CharSequence, String)
     */
    JsonObjectBuilder set(JsonFieldDefinition fieldDefinition, String value, Predicate<JsonField> predicate);

    /**
     * Sets a new string value field to the JSON object to be built.
     *
     * @param fieldDefinition this field definition provides the JSON pointer to the field to be set.
     * @param value the value to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code fieldDefinition} is null.
     * @see #set(CharSequence, String)
     */
    default JsonObjectBuilder set(final JsonFieldDefinition fieldDefinition, final String value) {
        return set(fieldDefinition, value, field -> true);
    }

    /**
     * Sets a new {@link JsonValue} field to the JSON object to be built if the specified predicate evaluates to {@code
     * true}.
     *
     * @param fieldDefinition this field definition provides the JSON pointer to the field to be set.
     * @param value the value of the field to be set.
     * @param predicate the predicate which finally determines if the field is to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if any argument is {@code null}; the predicate is evaluated before the null check
     * of the value.
     * @see #set(CharSequence, JsonValue)
     */
    JsonObjectBuilder set(JsonFieldDefinition fieldDefinition, JsonValue value, Predicate<JsonField> predicate);

    /**
     * Sets a new {@link JsonValue} field to the JSON object to be built.
     *
     * @param fieldDefinition this field definition provides the JSON pointer to the field to be set.
     * @param value the value of the field to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if any argument is {@code null}.
     * @see #set(CharSequence, JsonValue)
     */
    default JsonObjectBuilder set(final JsonFieldDefinition fieldDefinition, final JsonValue value) {
        return set(fieldDefinition, value, field -> true);
    }

    /**
     * Sets the specified field to the JSON object to be built if the specified predicate evaluates to {@code true}. If
     * this builder already contains a field with the same key, the existing one will be replaced.
     *
     * @param field the field to be set.
     * @param predicate the predicate which finally determines if the field is to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code field} is {@code null}.
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

//    /**
//     * Removes the field from the JSON object to be built which is associated with the given pointer. This method
//     * has no effect if no such field exists.
//     *
//     * @param pointer the pointer to the field to be removed.
//     * @return this builder to allow method chaining.
//     * @throws NullPointerException if {@code pointer} is {@code null}.
//     * @throws IllegalArgumentException if {@code pointer} is empty.
//     */
//    JsonObjectBuilder remove(JsonPointer pointer);

    /**
     * Removes the field from the JSON object to be built which is associated with the pointer of the specified JSON
     * field definition. This method has no effect if no such field exists.
     *
     * @param fieldDefinition provides the JSON pointer of the field to be deleted.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code fieldDefinition} is {@code null}.
     * @throws IllegalArgumentException if the JSON pointer of {@code fieldDefinition} is empty.
     */
    JsonObjectBuilder remove(JsonFieldDefinition fieldDefinition);

    /**
     * Sets the given {@link JsonField}s to the JSON object to be built. This method prevents duplicates, i. e. if two
     * fields have the same key, the previous field is replaced by the new field; the position of the new field in the
     * resulting JSON object is the same as the position of the previous field. For each field applies that the field is
     * only set if the specified predicate evaluates to {@code true}.
     *
     * @param fields the fields to set.
     * @param predicate the predicate which finally determines for each field if it is to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code fields} is {@code null}.
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
