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

import java.util.Optional;

/**
 * A {@code JsonObjectReader} helps to get values from a {@link JsonObject} without having to do existence or type
 * checks.
 *
 * <p>
 * Required values can be retrieved by methods such as {@link #getAsInt(CharSequence)}. If the value cannot be
 * obtained or it is not of the correct type, either a {@link JsonMissingFieldException} or a
 * {@link JsonParseException} is thrown.
 * </p>
 *
 * <p>
 * Optional values can be retrieved by methods such as {@link #getAsOptionalInt(CharSequence)}. If the value cannot
 * be obtained, an empty {@link Optional} is returned.
 * If the value is not of the correct type, a {@link JsonParseException} is thrown.
 * </p>
 */
public interface JsonObjectReader {

    /**
     * Retrieves from the JSON object the value for the specified key as Java string.
     *
     * @param key the key to get the Java string value for.
     * @return the value for {@code key} as Java string.
     * @throws NullPointerException if {@code key} is {@code null}.
     * @throws JsonMissingFieldException if the JSON object did not contain a value for {@code key} at all.
     * @throws JsonParseException if the value for {@code key} exists but is not a string.
     */
    String getAsString(CharSequence key);

    /**
     * Retrieves from the JSON object the value for the specified key as Java string, if such a value exists.
     *
     * @param key the key to get the Java string value for.
     * @return an Optional containing the value for {@code key} as Java string, if the value exists; an empty
     * Optional otherwise.
     * @throws NullPointerException if {@code key} is {@code null}.
     * @throws JsonParseException if the value for {@code key} exists but is not a string.
     */
    Optional<String> getAsOptionalString(CharSequence key);

    /**
     * Retrieves from the JSON object the value for the specified key as {@code int}.
     *
     * @param key the key to get the int value for.
     * @return the value for {@code key} as {@code int}.
     * @throws NullPointerException if {@code key} is {@code null}.
     * @throws JsonMissingFieldException if the JSON object did not contain a value for {@code key} at all.
     * @throws JsonParseException if the value for {@code key} exists but is not a {@code int}.
     */
    int getAsInt(CharSequence key);

    /**
     * Retrieves from the JSON object the value for the specified key as {@code int}, if such a value exists.
     *
     * @param key the key to get the int value for.
     * @return an Optional containing the value for {@code key} as {@code int}, if the value exists; an empty
     * Optional otherwise.
     * @throws NullPointerException if {@code key} is {@code null}.
     * @throws JsonParseException if the value for {@code key} exists but is not a {@code int}.
     */
    Optional<Integer> getAsOptionalInt(CharSequence key);

    /**
     * Retrieves from the JSON object the value for the specified key as {@code long}.
     *
     * @param key the key to get the long value for.
     * @return the value for {@code key} as {@code long}.
     * @throws NullPointerException if {@code key} is {@code null}.
     * @throws JsonMissingFieldException if the JSON object did not contain a value for {@code key} at all.
     * @throws JsonParseException if the value for {@code key} exists but is not a {@code long}.
     */
    long getAsLong(CharSequence key);

    /**
     * Retrieves from the JSON object the value for the specified key as {@code long}, if such a value exists.
     *
     * @param key the key to get the long value for.
     * @return an Optional containing the value for {@code key} as {@code long}, if the value exists; an empty
     * Optional otherwise.
     * @throws NullPointerException if {@code key} is {@code null}.
     * @throws JsonParseException if the value for {@code key} exists but is not a {@code long}.
     */
    Optional<Long> getAsOptionalLong(CharSequence key);

    /**
     * Retrieves from the JSON object the value for the specified key as {@code double}.
     *
     * @param key the key to get the double value for.
     * @return the value for {@code key} as {@code double}.
     * @throws NullPointerException if {@code key} is {@code null}.
     * @throws JsonMissingFieldException if the JSON object did not contain a value for {@code key} at all.
     * @throws JsonParseException if the value for {@code key} exists but is not a {@code double}.
     */
    double getAsDouble(CharSequence key);

    /**
     * Retrieves from the JSON object the value for the specified key as {@code double}, if such a value exists.
     *
     * @param key the key to get the double value for.
     * @return an Optional containing the value for {@code key} as {@code double}, if the value exists; an empty
     * Optional otherwise.
     * @throws NullPointerException if {@code key} is {@code null}.
     * @throws JsonParseException if the value for {@code key} exists but is not a {@code double}.
     */
    Optional<Double> getAsOptionalDouble(CharSequence key);

    /**
     * Retrieves from the JSON object the value for the specified key as {@code boolean}.
     *
     * @param key the key to get the boolean value for.
     * @return the value for {@code key} as {@code boolean}.
     * @throws NullPointerException if {@code key} is {@code null}.
     * @throws JsonMissingFieldException if the JSON object did not contain a value for {@code key} at all.
     * @throws JsonParseException if the value for {@code key} exists but is not a {@code boolean}.
     */
    boolean getAsBoolean(CharSequence key);

    /**
     * Retrieves from the JSON object the value for the specified key as {@code boolean}, if such a value exists.
     *
     * @param key the key to get the boolean value for.
     * @return an Optional containing the value for {@code key} as {@code boolean}, if the value exists; an empty
     * Optional otherwise.
     * @throws NullPointerException if {@code key} is {@code null}.
     * @throws JsonParseException if the value for {@code key} exists but is not a {@code boolean}.
     */
    Optional<Boolean> getAsOptionalBoolean(CharSequence key);

    /**
     * Retrieves from the JSON object the value for the specified key as {@code JsonObject}.
     *
     * @param key the key to get the JSON object for.
     * @return the value for {@code key} as {@code JsonObject}.
     * @throws NullPointerException if {@code key} is {@code null}.
     * @throws JsonMissingFieldException if the JSON object did not contain a value for {@code key} at all.
     * @throws JsonParseException if the value for {@code key} exists but is not a {@code JsonObject}.
     */
    JsonObject getAsJsonObject(CharSequence key);

    /**
     * Retrieves from the JSON object the value for the specified key as {@code JsonObject}, if such a value exists.
     *
     * @param key the key to get the JSON object for.
     * @return an Optional containing the value for {@code key} as {@code JsonObject}, if the value exists; an empty
     * Optional otherwise.
     * @throws NullPointerException if {@code key} is {@code null}.
     * @throws JsonParseException if the value for {@code key} exists but is not a {@code JsonObject}.
     */
    Optional<JsonObject> getAsOptionalJsonObject(CharSequence key);

    /**
     * Retrieves from the JSON object the value for the specified key as {@code JsonArray}.
     *
     * @param key the key to get the JSON array for.
     * @return the value for {@code key} as {@code JsonArray}.
     * @throws NullPointerException if {@code key} is {@code null}.
     * @throws JsonMissingFieldException if the JSON object did not contain a value for {@code key} at all.
     * @throws JsonParseException if the value for {@code key} exists but is not a {@code JsonArray}.
     */
    JsonArray getAsJsonArray(CharSequence key);

    /**
     * Retrieves from the JSON object the value for the specified key as {@code JsonArray}, if such a value exists.
     *
     * @param key the key to get the JSON array for.
     * @return an Optional containing the value for {@code key} as {@code JsonArray}, if the value exists; an empty
     * Optional otherwise.
     * @throws NullPointerException if {@code key} is {@code null}.
     * @throws JsonParseException if the value for {@code key} exists but is not a {@code JsonArray}.
     */
    Optional<JsonArray> getAsOptionalJsonArray(CharSequence key);

    /**
     * Retrieves from the JSON object the value for the specified field definition. The field definition hereby provides
     * the JSON pointer of the field to be retrieved as well as the supposed Java type of the value.
     *
     * @param fieldDefinition the definition of the field to be retrieved from the JSON object.
     * @param <T> the supposed type of the field's value.
     * @return the value of the field as the supposed type.
     * @throws NullPointerException if {@code fieldDefinition} is {@code null}.
     * @throws JsonMissingFieldException if the JSON object did not contain a value for pointer of
     * {@code fieldDefinition} at all.
     * @throws JsonParseException if the value for the pointer of {@code fieldDefinition} exists but it has not the Java
     * type which is defined by {@code fieldDefinition}.
     */
    <T> T get(JsonFieldDefinition fieldDefinition);

    /**
     * Retrieves from the JSON object the value for the specified field definition, if such a value exists. The field
     * definition hereby provides the JSON pointer of the field to be retrieved as well as the supposed Java type of
     * the value.
     *
     * @param fieldDefinition the definition of the field to be retrieved from the JSON object.
     * @param <T> the supposed type of the field's value.
     * @return an Optional containing the value of the field as the supposed type, if the value exists; an empty
     * Optional otherwise.
     * @throws NullPointerException if {@code fieldDefinition} is {@code null}.
     * @throws JsonParseException if the value for the pointer of {@code fieldDefinition} exists but it has not the Java
     * type which is defined by {@code fieldDefinition}.
     */
    <T> Optional<T> getAsOptional(JsonFieldDefinition fieldDefinition);

}
