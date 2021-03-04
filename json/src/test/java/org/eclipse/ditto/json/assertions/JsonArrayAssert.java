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

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonValue;

/**
 * Specific assertion for {@link JsonArray} objects.
 */
public final class JsonArrayAssert extends AbstractJsonValueAssert<JsonArrayAssert, JsonArray> {

    JsonArrayAssert(final JsonArray actual) {
        super(actual, JsonArrayAssert.class);
    }

    /**
     * Checks if the actual JSON array has the expected size.
     *
     * @param expectedSize the expected size.
     * @return this assert to allow method chaining.
     */
    public JsonArrayAssert hasSize(final int expectedSize) {
        isNotNull();
        final int actualSize = actual.getSize();

        Assertions.assertThat(actualSize).overridingErrorMessage("Expected JSON array to have size <%d> but was <%d>",
                expectedSize, actualSize).isEqualTo(expectedSize);

        return this;
    }

    /**
     * Checks if the actual JSON array is empty.
     *
     * @return this assert to allow method chaining.
     */
    public JsonArrayAssert isEmpty() {
        isNotNull();

        Assertions.assertThat(actual.isEmpty())
                .overridingErrorMessage("Expected JSON array to be empty but it was not.")
                .isTrue();

        return this;
    }

    /**
     * Checks if the actual JSON array is not empty.
     *
     * @return this assert to allow method chaining.
     */
    public JsonArrayAssert isNotEmpty() {
        isNotNull();

        Assertions.assertThat(actual.isEmpty())
                .overridingErrorMessage("Expected JSON array not to be empty but it was.")
                .isFalse();

        return this;
    }

    /**
     * Checks if the actual JSON array is really an array.
     *
     * @return this assert to allow method chaining.
     */
    @Override
    public JsonArrayAssert isArray() {
        isNotNull();

        Assertions.assertThat(actual.isArray())
                .overridingErrorMessage("Expected JSON array to be an array but it was not.")
                .isTrue();

        return this;
    }

    /**
     * Checks if the actual JSON array contains the expected value.
     *
     * @param expectedValue the expected value to be contained in the JSON array.
     * @return this assert to allow method chaining.
     */
    public JsonArrayAssert contains(final JsonValue expectedValue) {
        isNotNull();

        Assertions.assertThat(actual.contains(expectedValue)).overridingErrorMessage(
                "Expected JSON array to contain <%s> but it did not.", expectedValue).isTrue();

        return this;
    }

    /**
     * Checks if the actual JSON array contains the expected value.
     *
     * @param expectedValue the expected value to be contained in the JSON array.
     * @return this assert to allow method chaining.
     */
    public JsonArrayAssert contains(final int expectedValue) {
        return contains(JsonFactory.newValue(expectedValue));
    }

    /**
     * Checks if the actual JSON array contains the expected value.
     *
     * @param expectedValue the expected value to be contained in the JSON array.
     * @return this assert to allow method chaining.
     */
    public JsonArrayAssert contains(final long expectedValue) {
        return contains(JsonFactory.newValue(expectedValue));
    }

    /**
     * Checks if the actual JSON array contains the expected value.
     *
     * @param expectedValue the expected value to be contained in the JSON array.
     * @return this assert to allow method chaining.
     */
    public JsonArrayAssert contains(final double expectedValue) {
        return contains(JsonFactory.newValue(expectedValue));
    }

    /**
     * Checks if the actual JSON array contains the expected value.
     *
     * @param expectedValue the expected value to be contained in the JSON array.
     * @return this assert to allow method chaining.
     */
    public JsonArrayAssert contains(final boolean expectedValue) {
        return contains(JsonFactory.newValue(expectedValue));
    }

    /**
     * Checks if the actual JSON array contains the expected value.
     *
     * @param expectedValue the expected value to be contained in the JSON array.
     * @return this assert to allow method chaining.
     */
    public JsonArrayAssert contains(final String expectedValue) {
        return contains(JsonFactory.newValue(expectedValue));
    }

    /**
     * Checks if the actual JSON does not contain the specified value.
     *
     * @param value the value which should not be contained in the JSON array.
     * @return this assert to allow method chaining.
     */
    public JsonArrayAssert doesNotContain(final JsonValue value) {
        isNotNull();

        Assertions.assertThat(actual.contains(value))
                .overridingErrorMessage("Expected JSON array not to contain <%s> but it did.",
                        value)
                .isFalse();

        return this;
    }

    /**
     * Checks if the actual JSON does not contain the specified value.
     *
     * @param value the value which should not be contained in the JSON array.
     * @return this assert to allow method chaining.
     */
    public JsonArrayAssert doesNotContain(final int value) {
        return doesNotContain(JsonFactory.newValue(value));
    }

    /**
     * Checks if the actual JSON does not contain the specified value.
     *
     * @param value the value which should not be contained in the JSON array.
     * @return this assert to allow method chaining.
     */
    public JsonArrayAssert doesNotContain(final long value) {
        return doesNotContain(JsonFactory.newValue(value));
    }

    /**
     * Checks if the actual JSON does not contain the specified value.
     *
     * @param value the value which should not be contained in the JSON array.
     * @return this assert to allow method chaining.
     */
    public JsonArrayAssert doesNotContain(final double value) {
        return doesNotContain(JsonFactory.newValue(value));
    }

    /**
     * Checks if the actual JSON does not contain the specified value.
     *
     * @param value the value which should not be contained in the JSON array.
     * @return this assert to allow method chaining.
     */
    public JsonArrayAssert doesNotContain(final boolean value) {
        return doesNotContain(JsonFactory.newValue(value));
    }

    /**
     * Checks if the actual JSON does not contain the specified value.
     *
     * @param value the value which should not be contained in the JSON array.
     * @return this assert to allow method chaining.
     */
    public JsonArrayAssert doesNotContain(final String value) {
        return doesNotContain(JsonFactory.newValue(value));
    }

}
