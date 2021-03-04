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
import org.eclipse.ditto.json.JsonValue;
import org.json.JSONException;
import org.skyscreamer.jsonassert.JSONAssert;

/**
 * Specific assertion for {@link JsonValue} objects.
 */
public final class JsonValueAssert extends AbstractJsonValueAssert<JsonValueAssert, JsonValue> {

    JsonValueAssert(final JsonValue actual) {
        super(actual, JsonValueAssert.class);
    }

    /**
     * Verifies that the actual value returns the expected JSON string. <em>Note:</em> this method applies real JSON
     * comparison. Use {@link #hasSimpleString(String)} if only a simple string should be asserted.
     *
     * @param expectedJsonString the JSON string the actual {@link JsonValue} is expected to return when
     * {@link JsonValue#toString()} is called.
     * @return this assert to allow method chaining.
     */
    public JsonValueAssert hasJsonString(final String expectedJsonString) {
        isNotNull();
        try {
            final String actualString = actual.toString();
            JSONAssert.assertEquals(expectedJsonString, actualString, false);
        } catch (final JSONException e) {
            throw new AssertionError("JSONAssert failed to assert equality of actual and expected JSON string.", e);
        }
        return this;
    }

    /**
     * Verifies that the actual value returns the expected simple string. If the check should apply real JSON semantics
     * use {@link #hasJsonString(String)} instead.
     *
     * @param expectedString the simple string the actual {@link JsonValue} is expected to return when
     * {@link JsonValue#toString()} is called.
     * @return this assert to allow method chaining.
     */
    public JsonValueAssert hasSimpleString(final String expectedString) {
        isNotNull();
        Assertions.assertThat(actual.asString()).isEqualTo(expectedString);
        return this;
    }

}
