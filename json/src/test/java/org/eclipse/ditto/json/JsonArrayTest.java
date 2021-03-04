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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.Arrays;
import java.util.Collection;

import org.junit.BeforeClass;
import org.junit.Test;

import com.eclipsesource.json.ParseException;

/**
 * Unit test for {@link org.eclipse.ditto.json.JsonArray}.
 */
public final class JsonArrayTest {

    private static JsonObject knownJsonObject;

    @BeforeClass
    public static void initTestConstants() {
        knownJsonObject = JsonObject.newBuilder()
                .set("foo", "bar")
                .set("year", 1995)
                .build();
    }

    @Test
    public void emptyReturnsAnEmptyArray() {
        assertThat(JsonObject.empty()).isEmpty();
    }

    @Test
    public void getInstanceOfVarargs() {
        final JsonArray expected = JsonArray.newBuilder()
                .add("foo")
                .add(1)
                .add(false)
                .add(JsonFactory.nullLiteral())
                .add(knownJsonObject)
                .build();

        final JsonArray actual = JsonArray.of("foo", 1, false, JsonFactory.nullLiteral(), knownJsonObject);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void tryToGetInstanceOfInvalidValue() {
        final String[] strings = {"Hallo", "Welt"};

        assertThatExceptionOfType(JsonParseException.class)
                .isThrownBy(() -> JsonArray.of(strings))
                .withMessage("Failed to parse JSON string '%s'!", strings.toString())
                .withCauseInstanceOf(ParseException.class);
    }

    @Test
    public void getInstanceOfMixedObjectIterable() {
        final Collection<Object> javaObjects = Arrays.asList("foo", 1, false, null, knownJsonObject);
        final JsonArray expected = JsonArray.newBuilder()
                .add("foo")
                .add(1)
                .add(false)
                .add(JsonFactory.nullLiteral())
                .add(knownJsonObject)
                .build();

        final JsonArray actual = JsonArray.of(javaObjects);

        assertThat(actual).isEqualTo(expected);
    }

}
