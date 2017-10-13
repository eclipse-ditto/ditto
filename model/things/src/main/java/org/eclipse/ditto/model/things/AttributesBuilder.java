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

import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.json.JsonValue;

/**
 * A mutable builder for a {@link Attributes} with a fluent API.
 */
@NotThreadSafe
public interface AttributesBuilder extends JsonObjectBuilder {

    /**
     * Sets a new {@code int} attribute to the {@code Attributes} to be built if the specified predicate evaluates to
     * {@code true}.
     *
     * @param key the name of the attribute to be set.
     * @param value the value of the attribute to be set.
     * @param predicate the predicate which finally determines if the attribute is to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code name} is {@code null}.
     * @throws IllegalArgumentException if {@code name} is empty.
     */
    @Override
    AttributesBuilder set(CharSequence key, int value, Predicate<JsonField> predicate);

    /**
     * Sets a new {@code int} attribute to the {@code Attributes} to be built.
     *
     * @param key the name of the attribute to be set.
     * @param value the value of the attribute to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code name} is {@code null}.
     * @throws IllegalArgumentException if {@code name} is empty.
     */
    @Override
    default AttributesBuilder set(final CharSequence key, final int value) {
        return set(key, value, field -> true);
    }

    /**
     * Sets a new {@code long} attribute to the {@code Attributes} to be built if the specified predicate evaluates to
     * {@code true}.
     *
     * @param key the name of the attribute to be set.
     * @param value the value of the attribute to be set.
     * @param predicate the predicate which finally determines if the attribute is to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code name} is {@code null}.
     * @throws IllegalArgumentException if {@code name} is empty.
     */
    @Override
    AttributesBuilder set(CharSequence key, long value, Predicate<JsonField> predicate);

    /**
     * Sets a new {@code long} attribute to the {@code Attributes} to be built.
     *
     * @param key the name of the attribute to be set.
     * @param value the value of the attribute to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code name} is {@code null}.
     * @throws IllegalArgumentException if {@code name} is empty.
     */
    @Override
    default AttributesBuilder set(final CharSequence key, final long value) {
        return set(key, value, field -> true);
    }

    /**
     * Sets a new {@code double} value attribute to the {@code Attributes} to be built if the specified predicate
     * evaluates to {@code true}.
     *
     * @param key the name of the attribute to be set.
     * @param value the value of the attribute to be set.
     * @param predicate the predicate which finally determines if the attribute is to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code name} is {@code null}.
     * @throws IllegalArgumentException if {@code name} is empty.
     */
    @Override
    AttributesBuilder set(CharSequence key, double value, Predicate<JsonField> predicate);

    /**
     * Sets a new {@code double} value attribute to the {@code Attributes} to be built.
     *
     * @param key the name of the attribute to be set.
     * @param value the value of the attribute to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code name} is {@code null}.
     * @throws IllegalArgumentException if {@code name} is empty.
     */
    @Override
    default AttributesBuilder set(final CharSequence key, final double value) {
        return set(key, value, field -> true);
    }

    /**
     * Sets a new {@code boolean} value attribute to the {@code Attributes} to be built if the specified predicate
     * evaluates to {@code true}.
     *
     * @param key the name of the attribute to be set.
     * @param value the value of the attribute to be set.
     * @param predicate the predicate which finally determines if the attribute is to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code name} is {@code null}.
     * @throws IllegalArgumentException if {@code name} is empty.
     */
    @Override
    AttributesBuilder set(CharSequence key, boolean value, Predicate<JsonField> predicate);

    /**
     * Sets a new {@code boolean} value attribute to the {@code Attributes} to be built.
     *
     * @param key the name of the attribute to be set.
     * @param value the value of the attribute to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code name} is {@code null}.
     * @throws IllegalArgumentException if {@code name} is empty.
     */
    @Override
    default AttributesBuilder set(final CharSequence key, final boolean value) {
        return set(key, value, field -> true);
    }

    /**
     * Sets a new string value attribute to the {@code Attributes} to be built if the specified predicate evaluates to
     * {@code true}.
     *
     * @param key the name of the attribute to be set.
     * @param value the value of the attribute to be set.
     * @param predicate the predicate which finally determines if the attribute is to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code name} is {@code null}.
     * @throws IllegalArgumentException if {@code name} is empty.
     */
    @Override
    AttributesBuilder set(CharSequence key, String value, Predicate<JsonField> predicate);

    /**
     * Sets a new string value attribute to the {@code Attributes} to be built.
     *
     * @param key the name of the attribute to be set.
     * @param value the value of the attribute to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code name} is {@code null}.
     * @throws IllegalArgumentException if {@code name} is empty.
     */
    @Override
    default AttributesBuilder set(final CharSequence key, final String value) {
        return set(key, value, field -> true);
    }

    /**
     * Sets a new {@link JsonValue} attribute to the {@code Attributes} to be built if the specified predicate evaluates
     * to {@code true}.
     *
     * @param key the name of the attribute to be set.
     * @param value the value of the attribute to be set.
     * @param predicate the predicate which finally determines if the attribute is to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code name} is empty.
     */
    @Override
    AttributesBuilder set(CharSequence key, JsonValue value, Predicate<JsonField> predicate);

    /**
     * Sets a new {@link JsonValue} attribute to the {@code Attributes} to be built.
     *
     * @param key the name of the attribute to be set.
     * @param value the value of the attribute to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if any argument is {@code null}.
     * @throws IllegalArgumentException if {@code name} is empty.
     */
    @Override
    default AttributesBuilder set(final CharSequence key, final JsonValue value) {
        return set(key, value, field -> true);
    }

    @Override
    <T> AttributesBuilder set(JsonFieldDefinition<T> fieldDefinition, @Nullable T value,
            Predicate<JsonField> predicate);

    @Override
    default <T> AttributesBuilder set(final JsonFieldDefinition<T> fieldDefinition, @Nullable final T value) {
        return set(fieldDefinition, value, jsonField -> true);
    }

    //    /**
//     * Sets a new int attribute to the {@code Attributes} to be built if the specified predicate evaluates to
//     * {@code true}.
//     *
//     * @param fieldDefinition this field definition provides the JSON pointer to the attribute to be set.
//     * @param value the value of the attribute to be set.
//     * @param predicate the predicate which finally determines if the attribute is to be set.
//     * @return this builder to allow method chaining.
//     * @throws NullPointerException if {@code fieldDefinition} is null.
//     * @see #set(CharSequence, int)
//     */
//    @Override
//    AttributesBuilder set(JsonFieldDefinition fieldDefinition, int value, Predicate<JsonField> predicate);
//
//    /**
//     * Sets a new int attribute to the {@code Attributes} to be built.
//     *
//     * @param fieldDefinition this field definition provides the JSON pointer to the attribute to be set.
//     * @param value the value of the attribute to be set.
//     * @return this builder to allow method chaining.
//     * @throws NullPointerException if {@code fieldDefinition} is null.
//     * @see #set(CharSequence, int)
//     */
//    @Override
//    default AttributesBuilder set(final JsonFieldDefinition fieldDefinition, final int value) {
//        return set(fieldDefinition, value, field -> true);
//    }
//
//    /**
//     * Sets a new long attribute to the {@code Attributes} to be built if the specified predicate evaluates to
//     * {@code true}.
//     *
//     * @param fieldDefinition this field definition provides the JSON pointer to the attribute to be set.
//     * @param value the value of the attribute to be set.
//     * @param predicate the predicate which finally determines if the attribute is to be set.
//     * @return this builder to allow method chaining.
//     * @throws NullPointerException if {@code fieldDefinition} is null.
//     * @see #set(CharSequence, long)
//     */
//    @Override
//    AttributesBuilder set(JsonFieldDefinition fieldDefinition, long value, Predicate<JsonField> predicate);
//
//    /**
//     * Sets a new long attribute to the {@code Attributes} to be built.
//     *
//     * @param fieldDefinition this field definition provides the JSON pointer to the attribute to be set.
//     * @param value the value of the attribute to be set.
//     * @return this builder to allow method chaining.
//     * @throws NullPointerException if {@code fieldDefinition} is null.
//     * @see #set(CharSequence, long)
//     */
//    @Override
//    default AttributesBuilder set(final JsonFieldDefinition fieldDefinition, final long value) {
//        return set(fieldDefinition, value, field -> true);
//    }
//
//    /**
//     * Sets a new double attribute to the {@code Attributes} to be built if the specified predicate evaluates to
//     * {@code true}.
//     *
//     * @param fieldDefinition this field definition provides the JSON pointer to the attribute to be set.
//     * @param value the value of the attribute to be set.
//     * @param predicate the predicate which finally determines if the attribute is to be set.
//     * @return this builder to allow method chaining.
//     * @throws NullPointerException if {@code fieldDefinition} is null.
//     * @see #set(CharSequence, double)
//     */
//    @Override
//    AttributesBuilder set(JsonFieldDefinition fieldDefinition, double value, Predicate<JsonField> predicate);
//
//    /**
//     * Sets a new double attribute to the {@code Attributes} to be built.
//     *
//     * @param fieldDefinition this field definition provides the JSON pointer to the attribute to be set.
//     * @param value the value of the attribute to be set.
//     * @return this builder to allow method chaining.
//     * @throws NullPointerException if {@code fieldDefinition} is null.
//     * @see #set(CharSequence, double)
//     */
//    @Override
//    default AttributesBuilder set(final JsonFieldDefinition fieldDefinition, final double value) {
//        return set(fieldDefinition, value, field -> true);
//    }
//
//    /**
//     * Sets a new boolean attribute to the {@code Attributes} to be built if the specified predicate evaluates to
//     * {@code true}.
//     *
//     * @param fieldDefinition this field definition provides the JSON pointer to the attribute to be set.
//     * @param value the value of the attribute to be set.
//     * @param predicate the predicate which finally determines if the attribute is to be set.
//     * @return this builder to allow method chaining.
//     * @throws NullPointerException if {@code fieldDefinition} is null.
//     * @see #set(CharSequence, boolean)
//     */
//    @Override
//    AttributesBuilder set(JsonFieldDefinition fieldDefinition, boolean value, Predicate<JsonField> predicate);
//
//    /**
//     * Sets a new boolean attribute to the {@code Attributes} to be built.
//     *
//     * @param fieldDefinition this field definition provides the JSON pointer to the attribute to be set.
//     * @param value the value of the attribute to be set.
//     * @return this builder to allow method chaining.
//     * @throws NullPointerException if {@code fieldDefinition} is null.
//     * @see #set(CharSequence, boolean)
//     */
//    @Override
//    default AttributesBuilder set(final JsonFieldDefinition fieldDefinition, final boolean value) {
//        return set(fieldDefinition, value, field -> true);
//    }
//
//    /**
//     * Sets a new string attribute to the {@code Attributes} to be built if the specified predicate evaluates to
//     * {@code true}.
//     *
//     * @param fieldDefinition this field definition provides the JSON pointer to the attribute to be set.
//     * @param value the value of the attribute to be set.
//     * @param predicate the predicate which finally determines if the attribute is to be set.
//     * @return this builder to allow method chaining.
//     * @throws NullPointerException if {@code fieldDefinition} is null.
//     * @see #set(CharSequence, String)
//     */
//    @Override
//    AttributesBuilder set(JsonFieldDefinition fieldDefinition, String value, Predicate<JsonField> predicate);
//
//    /**
//     * Sets a new string attribute to the {@code Attributes} to be built.
//     *
//     * @param fieldDefinition this field definition provides the JSON pointer to the attribute to be set.
//     * @param value the value of the attribute to be set.
//     * @return this builder to allow method chaining.
//     * @throws NullPointerException if {@code fieldDefinition} is null.
//     * @see #set(CharSequence, String)
//     */
//    @Override
//    default AttributesBuilder set(final JsonFieldDefinition fieldDefinition, final String value) {
//        return set(fieldDefinition, value, field -> true);
//    }
//
//    /**
//     * Sets a new {@link JsonValue} attribute to the {@code Attributes} to be built if the specified predicate evaluates
//     * to {@code true}.
//     *
//     * @param fieldDefinition this field definition provides the JSON pointer to the attribute to be set.
//     * @param value the value of the attribute to be set.
//     * @param predicate the predicate which finally determines if the attribute is to be set.
//     * @return this builder to allow method chaining.
//     * @throws NullPointerException if any argument but {@code value} is {@code null}.
//     * @see #set(CharSequence, JsonValue)
//     */
//    @Override
//    AttributesBuilder set(JsonFieldDefinition fieldDefinition, JsonValue value, Predicate<JsonField> predicate);
//
//    /**
//     * Sets a new {@link JsonValue} attribute to the {@code Attributes} to be built.
//     *
//     * @param fieldDefinition this field definition provides the JSON pointer to the attribute to be set.
//     * @param value the value of the attribute to be set.
//     * @return this builder to allow method chaining.
//     * @throws NullPointerException if {@code fieldDefinition} is {@code null}.
//     * @see #set(CharSequence, JsonValue)
//     */
//    @Override
//    default AttributesBuilder set(final JsonFieldDefinition fieldDefinition, final JsonValue value) {
//        return set(fieldDefinition, value, field -> true);
//    } // TODO

    /**
     * Sets the specified attribute to the {@code Attributes} to be built if the specified predicate evaluates to
     * {@code true}. If this builder already contains a field with the same key, the existing one will be replaced.
     *
     * @param field the field to be set.
     * @param predicate the predicate which finally determines if the attribute is to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code field} is {@code null}.
     */
    @Override
    AttributesBuilder set(JsonField field, Predicate<JsonField> predicate);

    /**
     * Sets the specified attribute to the {@code Attributes} to be built. If this builder already contains a field with
     * the same key, the existing one will be replaced.
     *
     * @param field the field to be set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code field} is {@code null}.
     */
    @Override
    default AttributesBuilder set(final JsonField field) {
        return set(field, jsonField -> true);
    }

    /**
     * Removes the attribute from the {@code Attributes} to be built which is associated with the given name. This
     * method has no effect if no such field exists.
     *
     * @param key the name of the attribute to be removed.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code name} is {@code null}.
     */
    @Override
    AttributesBuilder remove(CharSequence key);

    /**
     * Removes the attribute from the {@code Attributes} to be built which is associated with the pointer of the
     * specified JSON field definition. This method has no effect if no such field exists.
     *
     * @param fieldDefinition provides the JSON pointer of the attribute to be deleted.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code fieldDefinition} is {@code null}.
     * @throws IllegalArgumentException if the JSON pointer of {@code fieldDefinition} is empty.
     */
    @Override
    AttributesBuilder remove(JsonFieldDefinition fieldDefinition);

    /**
     * Sets the given {@link JsonField}s to the {@code Attributes} to be built. This method prevents duplicates, i. e.
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
    AttributesBuilder setAll(Iterable<JsonField> fields, Predicate<JsonField> predicate);

    /**
     * Sets the given {@link JsonField}s to the {@code Attributes} to be built. This method prevents duplicates, i. e.
     * if two fields have the same key, the previous field is replaced by the new field; the position of the new field
     * in the resulting JSON object is the same as the position of the previous field.
     *
     * @param fields the fields to set.
     * @return this builder to allow method chaining.
     * @throws NullPointerException if {@code fields} is {@code null}.
     */
    @Override
    default AttributesBuilder setAll(final Iterable<JsonField> fields) {
        return setAll(fields, jsonField -> true);
    }

    /**
     * Removes all fields from the {@code Attributes} to be built.
     *
     * @return this builder to allow method chaining.
     */
    @Override
    AttributesBuilder removeAll();

    /**
     * Creates a new {@link Attributes} object containing all values which were set to this builder beforehand.
     *
     * @return the new {@code Attributes} object.
     */
    @Override
    Attributes build();

}
