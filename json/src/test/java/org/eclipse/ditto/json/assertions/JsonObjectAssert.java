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
package org.eclipse.ditto.json.assertions;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

import org.assertj.core.api.AbstractIterableAssert;
import org.assertj.core.api.AssertFactory;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.ObjectAssert;
import org.assertj.core.api.ObjectAssertFactory;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;

/**
 * Specific assertion for {@link JsonObject} objects.
 */
public final class JsonObjectAssert
        extends AbstractIterableAssert<JsonObjectAssert, JsonObject, JsonField, ObjectAssert<JsonField>>
        implements JsonValueAssertable<JsonObjectAssert> {

    private final JsonValueAssertable<JsonValueAssert> jsonValueAssert;

    /**
     * Constructs a new {@code JsonObjectAssert} object.
     *
     * @param actual the actual JSON object to be verified.
     */
    JsonObjectAssert(final JsonObject actual) {
        super(actual, JsonObjectAssert.class);
        jsonValueAssert = new JsonValueAssert(actual);
    }

    /**
     * Verifies that the actual JSON object has the expected size.
     *
     * @param expectedSize the expected size.
     * @return this assert to allow method chaining.
     */
    @Override
    public JsonObjectAssert hasSize(final int expectedSize) {
        isNotNull();
        final int actualSize = actual.getSize();

        Assertions.assertThat(actualSize)
                .overridingErrorMessage("Expected JSON object to have size <%d> but was <%d>", expectedSize, actualSize)
                .isEqualTo(expectedSize);

        return this;
    }

    @Override
    protected ObjectAssert<JsonField> toAssert(final JsonField jsonField, final String description) {
        final AssertFactory<JsonField, ObjectAssert<JsonField>> objectAssertFactory = new ObjectAssertFactory<>();
        return objectAssertFactory.createAssert(jsonField).as(description);
    }

    @Override
    protected JsonObjectAssert newAbstractIterableAssert(final Iterable<? extends JsonField> iterable) {
        return new JsonObjectAssert((JsonObject) iterable);
    }

    /**
     * Verifies that the actual JSON object contains the expected value for the specified key.
     *
     * @param key the key of the expected value.
     * @param expectedValue the expected value to be associated with {@code key}.
     * @return this assert to allow method chaining.
     */
    public JsonObjectAssert contains(final JsonKey key, final int expectedValue) {
        return contains(key, JsonFactory.newValue(expectedValue));
    }

    /**
     * Verifies that the actual JSON object contains the expected value for the specified key.
     *
     * @param key the key of the expected value.
     * @param expectedValue the expected value to be associated with {@code key}.
     * @return this assert to allow method chaining.
     */
    public JsonObjectAssert contains(final JsonKey key, final long expectedValue) {
        return contains(key, JsonFactory.newValue(expectedValue));
    }

    /**
     * Verifies that the actual JSON object contains the expected value for the specified key.
     *
     * @param key the key of the expected value.
     * @param expectedValue the expected value to be associated with {@code key}.
     * @return this assert to allow method chaining.
     */
    public JsonObjectAssert contains(final JsonKey key, final double expectedValue) {
        return contains(key, JsonFactory.newValue(expectedValue));
    }

    /**
     * Verifies that the actual JSON object contains the expected value for the specified key.
     *
     * @param key the key of the expected value.
     * @param expectedValue the expected value to be associated with {@code key}.
     * @return this assert to allow method chaining.
     */
    public JsonObjectAssert contains(final JsonKey key, final boolean expectedValue) {
        return contains(key, JsonFactory.newValue(expectedValue));
    }

    /**
     * Verifies that the actual JSON object contains the expected value for the specified key.
     *
     * @param key the key of the expected value.
     * @param expectedValue the expected value to be associated with {@code key}.
     * @return this assert to allow method chaining.
     */
    public JsonObjectAssert contains(final JsonKey key, final String expectedValue) {
        return contains(key, JsonFactory.newValue(expectedValue));
    }

    /**
     * Verifies that the actual JSON object contains the expected value for the specified key.
     *
     * @param key the key of the expected value.
     * @param expectedValue the expected value to be associated with {@code key}.
     * @return this assert to allow method chaining.
     */
    public JsonObjectAssert contains(final JsonKey key, final JsonValue expectedValue) {
        isNotNull();
        final List<JsonKey> actualNames = actual.getKeys();

        Assertions.assertThat(actualNames)
                .overridingErrorMessage("Expected JSON object to contain a field with name <%s> but it did not.",
                        key)
                .contains(key);

        final Optional<JsonValue> actualValue = actual.getValue(key);

        assertThat(actualValue)
                .overridingErrorMessage("Expected JSON object to contain value <%s> for key <%s> but the actual value" +
                        " was <%s>", expectedValue, key, actualValue.orElse(null))
                .contains(expectedValue);

        return this;
    }

    /**
     * Verifies that the actual JSON does not contain a value for the specified key.
     *
     * @param key the key which should not be contained in the JSON object.
     * @return this assert to allow method chaining.
     */
    public JsonObjectAssert doesNotContain(final JsonKey key) {
        isNotNull();

        assertThat(actual.getValue(key))
                .overridingErrorMessage("Expected JSON object not to contain a value for key <%s> but it did.",
                        key.toString())
                .isEmpty();

        return this;
    }

    /**
     * Verifies that the actual JSON does <em>not</em> contain a value for pointer of the specified JSON field definition.
     *
     * @param jsonFieldDefinition provides a JsonPointer which should not be contained in the checked JSON object.
     * @return this assert to allow method chaining.
     */
    public JsonObjectAssert doesNotContain(final JsonFieldDefinition<?> jsonFieldDefinition) {
        isNotNull();
        final JsonPointer pointer = jsonFieldDefinition.getPointer();
        final Optional<JsonValue> valueOptional = actual.getValue(pointer);
        Assertions.assertThat(valueOptional)
                .overridingErrorMessage("Expected JSON object not to contain a value for <%s> but it contained <%s>",
                        pointer, valueOptional.orElse(null))
                .isEmpty();
        return myself;
    }

    public JsonObjectAssert isEqualTo(final Iterable<JsonField> expectedJsonFields) {
        isEqualTo(expectedJsonFields, true);
        return myself;
    }

    private void isEqualTo(final Iterable<JsonField> expectedFields, final boolean compareFieldDefinitions) {
        isNotNull();

        final Collection<JsonField> missingFields = new ArrayList<>();

        for (final JsonField expectedField : expectedFields) {
            final Optional<JsonField> actualFieldOptional = actual.getField(expectedField.getKey());
            if (!actualFieldOptional.isPresent()) {
                missingFields.add(expectedField);
            } else {
                compareFields(expectedField, actualFieldOptional.get(), compareFieldDefinitions);
            }
        }

        Assertions.assertThat(missingFields)
                .overridingErrorMessage("Expected JSON object to contain \n<%s> but it did not contain \n<%s>",
                        expectedFields, missingFields)
                .isEmpty();

        final Collection<JsonField> unexpectedActualFields = new ArrayList<>();

        for (final JsonField actualField : actual) {
            final Optional<JsonField> expectedFieldOptional = getField(expectedFields, actualField.getKey());
            if (!expectedFieldOptional.isPresent()) {
                unexpectedActualFields.add(actualField);
            }
        }

        Assertions.assertThat(unexpectedActualFields)
                .overridingErrorMessage("Expected JSON object not to contain\n<%s> but it did", unexpectedActualFields)
                .isEmpty();
    }

    private static void compareFields(final JsonField expectedField, final JsonField actualField,
            final boolean compareFieldDefinitions) {

        compareFieldValues(expectedField, actualField, compareFieldDefinitions);
        if (compareFieldDefinitions) {
            compareFieldDefinitions(expectedField, actualField);
        }
    }

    private static void compareFieldValues(final JsonField expectedField, final JsonField actualField,
            final boolean compareFieldDefinitions) {

        compareValuesWithFieldKey(expectedField.getKey(), expectedField.getValue(),
                actualField.getValue(), compareFieldDefinitions);
    }

    /*
     * Recursively compare complex JSON values. The flag 'compareFieldDefinitions' is applied recursively to all
     * parts of the JSON values.
     */
    private static void compareValuesWithFieldKey(final JsonKey key,
            final JsonValue expectedFieldValue,
            final JsonValue actualFieldValue,
            final boolean compareFieldDefinitions) {

        if (expectedFieldValue.isObject() && actualFieldValue.isObject() && !compareFieldDefinitions) {
            DittoJsonAssertions.assertThat(actualFieldValue.asObject())
                    .isEqualToIgnoringFieldDefinitions(expectedFieldValue.asObject());
        } else if (areArraysOfEqualSize(expectedFieldValue, actualFieldValue) && !compareFieldDefinitions) {
            final JsonArray expectedArray = expectedFieldValue.asArray();
            final JsonArray actualArray = actualFieldValue.asArray();
            IntStream.range(0, expectedArray.getSize())
                    .forEach(i ->
                            expectedArray.get(i).ifPresent(expectedElement ->
                                    actualArray.get(i).ifPresent(actualElement ->
                                            compareValuesWithFieldKey(key, expectedElement, actualElement, false))));
        } else {
            Assertions.assertThat(actualFieldValue)
                    .as("Values of JsonField <%s> are equal", key)
                    .isEqualTo(expectedFieldValue);
        }
    }

    private static boolean areArraysOfEqualSize(final JsonValue expected, final JsonValue actual) {
        return expected.isArray() && actual.isArray() &&
                expected.asArray().getSize() == actual.asArray().getSize();
    }

    private static void compareFieldDefinitions(final JsonField expectedField, final JsonField actualField) {
        expectedField.getDefinition().ifPresent(expectedFieldDefinition -> {
            Assertions.assertThat(actualField.getDefinition())
                    .overridingErrorMessage("Expected JsonField <%s> to have definition <%s> but it had " +
                            "none", expectedField.getKey(), expectedFieldDefinition)
                    .isPresent();

            Assertions.assertThat(expectedFieldDefinition)
                    .as("Definitions of JsonField <%s> are equal", expectedField.getKey())
                    .isEqualTo(actualField.getDefinition().get());
        });
    }

    private static Optional<JsonField> getField(final Iterable<JsonField> jsonFields, final JsonKey key) {
        return StreamSupport.stream(jsonFields.spliterator(), false)
                .filter(jsonField -> Objects.equals(jsonField.getKey(), key))
                .findAny();
    }

    public JsonObjectAssert isEqualToIgnoringFieldDefinitions(final Iterable<JsonField> expectedJsonFields) {
        isEqualTo(expectedJsonFields, false);
        return myself;
    }

    /**
     * Verifies that the JSON object contains a field which is denoted by the specified JSON pointer and which has the
     * specified value.
     *
     * @param jsonPointer points to a field which is expected to be a part of the checked JSON object.
     * @param expectedValue the expected value of the field which is denoted by {@code jsonPointer}.
     * @return this assertion object.
     */
    public JsonObjectAssert contains(final JsonPointer jsonPointer, final JsonValue expectedValue) {
        isNotNull();
        final Optional<JsonValue> valueOptional = actual.getValue(jsonPointer);
        Assertions.assertThat(valueOptional)
                .overridingErrorMessage("Expected JSON object to contain a field for <%s> but it did not", jsonPointer)
                .isPresent();
        Assertions.assertThat(valueOptional).contains(expectedValue);
        return this;
    }

    /**
     * Verifies that the JSON object contains a field which is denoted by the specified JSON field definition and which has
     * the specified value.
     *
     * @param jsonFieldDefinition points to a field which is expected to be a part of the checked JSON object.
     * @param expectedValue the expected value of the field which is denoted by {@code jsonPointer}.
     * @return this assertion object.
     */
    public JsonObjectAssert contains(final JsonFieldDefinition<?> jsonFieldDefinition, final JsonValue expectedValue) {
        return contains(jsonFieldDefinition.getPointer(), expectedValue);
    }

    /**
     * Verifies that the JSON object contains a field which is denoted by the specified JSON pointer and which has the
     * specified value.
     *
     * @param jsonPointer points to a field which is expected to be a part of the checked JSON object.
     * @param expectedValue the expected value of the field which is denoted by {@code jsonPointer}.
     * @return this assertion object.
     */
    public JsonObjectAssert contains(final JsonPointer jsonPointer, final String expectedValue) {
        isNotNull();
        final Optional<JsonValue> valueOptional = actual.getValue(jsonPointer);
        Assertions.assertThat(valueOptional)
                .overridingErrorMessage("Expected JSON object to contain a field for <%s> but it did not", jsonPointer)
                .isPresent();
        final JsonValue jsonValue = valueOptional.get();
        DittoJsonAssertions.assertThat(jsonValue)
                .isString()
                .hasSimpleString(expectedValue);
        return this;
    }

    /**
     * Verifies that the JSON object contains a field which is denoted by the specified JSON field definition and which has
     * the specified value.
     *
     * @param jsonFieldDefinition points to a field which is expected to be a part of the checked JSON object.
     * @param expectedValue the expected value of the field which is denoted by {@code jsonPointer}.
     * @return this assertion object.
     */
    public JsonObjectAssert contains(final JsonFieldDefinition<?> jsonFieldDefinition, final String expectedValue) {
        return contains(jsonFieldDefinition.getPointer(), expectedValue);
    }

    /**
     * Verifies that the JSON object contains all specified keys.
     *
     * @param expectedJsonKey the mandatory key whose existence is checked.
     * @param furtherExpectedJsonKeys further optional keys whose existence in the JSON object is checked.
     * @return this assertion object.
     */
    public JsonObjectAssert containsKey(final CharSequence expectedJsonKey,
            final CharSequence... furtherExpectedJsonKeys) {
        isNotNull();

        final Collection<CharSequence> allExpectedJsonKeys = new ArrayList<>(1 + furtherExpectedJsonKeys.length);
        allExpectedJsonKeys.add(expectedJsonKey);
        Collections.addAll(allExpectedJsonKeys, furtherExpectedJsonKeys);

        final List<CharSequence> missingKeys = new ArrayList<>();
        for (final CharSequence jsonKey : allExpectedJsonKeys) {
            if (!actual.contains(jsonKey)) {
                missingKeys.add(jsonKey);
            }
        }
        Assertions.assertThat(missingKeys)
                .overridingErrorMessage("Expected JSON object to contain key(s) <%s> but it did not contain <%s>",
                        allExpectedJsonKeys, missingKeys)
                .isEmpty();
        return myself;
    }

    @Override
    public JsonObjectAssert isArray() {
        jsonValueAssert.isArray();
        return myself;
    }

    @Override
    public JsonObjectAssert isNotArray() {
        jsonValueAssert.isNotArray();
        return myself;
    }

    @Override
    public JsonObjectAssert isObject() {
        jsonValueAssert.isObject();
        return myself;
    }

    @Override
    public JsonObjectAssert isNotObject() {
        jsonValueAssert.isNotObject();
        return myself;
    }

    @Override
    public JsonObjectAssert isBoolean() {
        jsonValueAssert.isBoolean();
        return myself;
    }

    @Override
    public JsonObjectAssert isNotBoolean() {
        jsonValueAssert.isNotBoolean();
        return myself;
    }

    @Override
    public JsonObjectAssert isNullLiteral() {
        jsonValueAssert.isNullLiteral();
        return myself;
    }

    @Override
    public JsonObjectAssert isNotNullLiteral() {
        jsonValueAssert.isNotNullLiteral();
        return myself;
    }

    @Override
    public JsonObjectAssert isNumber() {
        jsonValueAssert.isNumber();
        return myself;
    }

    @Override
    public JsonObjectAssert isNotNumber() {
        jsonValueAssert.isNotNumber();
        return myself;
    }

    @Override
    public JsonObjectAssert isString() {
        jsonValueAssert.isString();
        return myself;
    }

    @Override
    public JsonObjectAssert isNotString() {
        jsonValueAssert.isNotString();
        return myself;
    }

    @Override
    public JsonObjectAssert doesNotSupport(final Consumer<JsonValue> consumer) {
        jsonValueAssert.doesNotSupport(consumer);
        return myself;
    }

}
