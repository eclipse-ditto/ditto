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

import static java.util.Objects.requireNonNull;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * Immutable default implementation of {@link JsonObjectReader}. The method {@link #get(JsonFieldDefinition)} uses
 * {@link JsonFieldDefinition#getValueType()} to determine the return type. The following types are supported:
 * <ul>
 * <li>{@code String.class},</li>
 * <li>{@code CharSequence.class},</li>
 * <li>{@code int.class},</li>
 * <li>{@code Integer.class},</li>
 * <li>{@code long.class},</li>
 * <li>{@code Long.class},</li>
 * <li>{@code double.class},</li>
 * <li>{@code Double.class},</li>
 * <li>{@code boolean.class},</li>
 * <li>{@code Boolean.class},</li>
 * <li>{@code JsonValue.class}</li>
 * <li>{@code JsonObject.class},</li>
 * <li>{@code JsonArray.class}.</li>
 * </ul>
 */
@Immutable
public final class JsonReader implements JsonObjectReader {

    private static final Map<Class<?>, BiFunction<Optional<JsonValue>, CharSequence, ?>> MAPPING_FUNCTIONS =
            new HashMap<>();

    static {
        final BiFunction<Optional<JsonValue>, CharSequence, ?> stringFunction =
                new MappingFunction<>("string", JsonValue::isString, JsonValue::asString);
        MAPPING_FUNCTIONS.put(String.class, stringFunction);
        MAPPING_FUNCTIONS.put(CharSequence.class, stringFunction);

        final BiFunction<Optional<JsonValue>, CharSequence, ?> intFunction =
                new MappingFunction<>("integer", JsonValue::isNumber, JsonValue::asInt);
        MAPPING_FUNCTIONS.put(int.class, intFunction);
        MAPPING_FUNCTIONS.put(Integer.class, intFunction);

        final BiFunction<Optional<JsonValue>, CharSequence, ?> longFunction =
                new MappingFunction<>("long", JsonValue::isNumber, JsonValue::asLong);
        MAPPING_FUNCTIONS.put(long.class, longFunction);
        MAPPING_FUNCTIONS.put(Long.class, longFunction);

        final BiFunction<Optional<JsonValue>, CharSequence, ?> doubleFunction =
                new MappingFunction<>("double", JsonValue::isNumber, JsonValue::asDouble);
        MAPPING_FUNCTIONS.put(double.class, doubleFunction);
        MAPPING_FUNCTIONS.put(Double.class, doubleFunction);

        final BiFunction<Optional<JsonValue>, CharSequence, ?> booleanFunction =
                new MappingFunction<>("boolean", JsonValue::isBoolean, JsonValue::asBoolean);
        MAPPING_FUNCTIONS.put(boolean.class, booleanFunction);
        MAPPING_FUNCTIONS.put(Boolean.class, booleanFunction);

        MAPPING_FUNCTIONS.put(JsonValue.class,
                (jsonValue, pointer) -> jsonValue.orElseThrow(() -> unexpectedValueType(pointer, "JSON value")));

        MAPPING_FUNCTIONS.put(JsonObject.class,
                new MappingFunction<>("JSON object", JsonValue::isObject, JsonValue::asObject));

        MAPPING_FUNCTIONS.put(JsonArray.class,
                new MappingFunction<>("JSON array", JsonValue::isArray, JsonValue::asArray));

        MAPPING_FUNCTIONS.put(JsonPointer.class, new MappingFunction<>("JSON pointer", JsonValue::isString,
                jsonValue -> JsonFactory.newPointer(jsonValue.asString())));
    }

    private final JsonObject jsonObject;


    private JsonReader(final JsonObject theJsonObject) {
        jsonObject = theJsonObject;
    }

    /**
     * Returns a new instance of {@code JsonReader}.
     *
     * @param jsonObject the JSON object to get values from.
     * @return a new reader for {@code jsonObject}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     */
    public static JsonObjectReader from(final JsonObject jsonObject) {
        requireNonNull(jsonObject, "The JSON object to be read must not be null!");
        return new JsonReader(jsonObject);
    }

    private static void checkKey(final Object key) {
        requireNonNull(key, "The key to get the value for must not be null!");
    }

    /**
     * Returns a new {@code JsonParseException} which indicates that the value of a particular key was not of the
     * expected type.
     *
     * @param key the key whose value was of unexpected type.
     * @param expectedType the type which was expected for the value of {@code key}.
     * @return a new JsonParseException.
     */
    private static JsonParseException unexpectedValueType(final CharSequence key, final String expectedType) {
        requireNonNull(expectedType, "The expected type must not be null!");
        final String msgTemplate = "Value of {0} was not of type {1}!";
        return new JsonParseException(MessageFormat.format(msgTemplate, key, expectedType));
    }

    @Override
    public <T> T get(final JsonFieldDefinition fieldDefinition) {
        requireNonNull(fieldDefinition, "The field definition must not be null!");

        return getValueFor(fieldDefinition.getPointer(), fieldDefinition.getValueType());
    }

    @Override
    public <T> Optional<T> getAsOptional(final JsonFieldDefinition fieldDefinition) {
        requireNonNull(fieldDefinition, "The field definition must not be null!");

        return getOptionalValueFor(fieldDefinition.getPointer(), fieldDefinition.getValueType());
    }

    @Override
    public String getAsString(final CharSequence key) {
        return getValueFor(key, String.class);
    }

    @Override
    public Optional<String> getAsOptionalString(final CharSequence key) {
        return getOptionalValueFor(key, String.class);
    }

    @Override
    public int getAsInt(final CharSequence key) {
        return getValueFor(key, int.class);
    }

    @Override
    public Optional<Integer> getAsOptionalInt(final CharSequence key) {
        return getOptionalValueFor(key, int.class);
    }

    @Override
    public long getAsLong(final CharSequence key) {
        return getValueFor(key, long.class);
    }

    @Override
    public Optional<Long> getAsOptionalLong(final CharSequence key) {
        return getOptionalValueFor(key, long.class);
    }

    @Override
    public double getAsDouble(final CharSequence key) {
        return getValueFor(key, double.class);
    }

    @Override
    public Optional<Double> getAsOptionalDouble(final CharSequence key) {
        return getOptionalValueFor(key, double.class);
    }

    @Override
    public boolean getAsBoolean(final CharSequence key) {
        return getValueFor(key, boolean.class);
    }

    @Override
    public Optional<Boolean> getAsOptionalBoolean(final CharSequence key) {
        return getOptionalValueFor(key, boolean.class);
    }

    @Override
    public JsonObject getAsJsonObject(final CharSequence key) {
        return getValueFor(key, JsonObject.class);
    }

    @Override
    public Optional<JsonObject> getAsOptionalJsonObject(final CharSequence key) {
        return getOptionalValueFor(key, JsonObject.class);
    }

    @Override
    public JsonArray getAsJsonArray(final CharSequence key) {
        return getValueFor(key, JsonArray.class);
    }

    @Override
    public Optional<JsonArray> getAsOptionalJsonArray(final CharSequence key) {
        return getOptionalValueFor(key, JsonArray.class);
    }

    @SuppressWarnings("unchecked")
    private <T> T getValueFor(final CharSequence key, final Class<?> targetType) {
        checkKey(key);
        checkForExistence(key);

        final BiFunction<Optional<JsonValue>, CharSequence, ?> mappingFunction = MAPPING_FUNCTIONS.get(targetType);
        return (T) mappingFunction.apply(jsonObject.getValue(key), key);
    }

    private void checkForExistence(final CharSequence key) {
        if (!jsonObject.contains(key)) {
            throw JsonMissingFieldException.newBuilder().fieldName(key.toString()).build();
        }
    }

    private <T> Optional<T> getOptionalValueFor(final CharSequence key, final Class<?> targetType) {
        checkKey(key);

        final Optional<JsonValue> jsonValue = jsonObject.getValue(key);
        if (!jsonValue.isPresent()) {
            return Optional.empty();
        }

        final BiFunction<Optional<JsonValue>, CharSequence, ?> mappingFunction = MAPPING_FUNCTIONS.get(targetType);
        @SuppressWarnings("unchecked") final T mappedValue = (T) mappingFunction.apply(jsonValue, key);

        return Optional.ofNullable(mappedValue);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final JsonReader that = (JsonReader) o;
        return Objects.equals(jsonObject, that.jsonObject);
    }

    @Override
    public int hashCode() {
        return Objects.hash(jsonObject);
    }

    @Immutable
    private static final class MappingFunction<T> implements BiFunction<Optional<JsonValue>, CharSequence, T> {

        private final String expectedTypeName;
        private final Predicate<JsonValue> isExpectedType;
        private final Function<JsonValue, T> mapToExpectedType;

        private MappingFunction(final CharSequence expectedTypeName, final Predicate<JsonValue> checkTypePredicate,
                final Function<JsonValue, T> mappingFunction) {

            this.expectedTypeName = requireNonNull(expectedTypeName).toString();
            isExpectedType = requireNonNull(checkTypePredicate);
            mapToExpectedType = requireNonNull(mappingFunction);
        }

        @Nullable
        @Override
        public T apply(final Optional<JsonValue> jsonValueOptional, final CharSequence key) {
            final T result;

            if (jsonValueOptional.isPresent()) {
                final JsonValue jsonValue = jsonValueOptional.get();
                if (jsonValue.isNull()) {
                    result = null;
                } else if (isExpectedType.test(jsonValue)) {
                    result = mapToExpectedType.apply(jsonValue);
                } else {
                    throw unexpectedValueType(key, expectedTypeName);
                }
            } else {
                result = null;
            }

            return result;
        }

    }

}
