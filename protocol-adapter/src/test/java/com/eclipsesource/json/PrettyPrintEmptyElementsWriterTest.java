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
package com.eclipsesource.json;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link PrettyPrintEmptyElementsWriter}.
 */
public final class PrettyPrintEmptyElementsWriterTest {

    private static final String NAME_FOO = "foo";
    private static final String NAME_BAR = "bar";
    private static final String NAME_NESTED = "nested";
    private static final String VALUE_FOO = "Karl-Heinz Winkler";
    private static final String VALUE_BAR = "Witz-Fachmann";

    private JsonObject emptyJsonObject;
    private JsonObject jsonObjectWithValues;
    private JsonObject jsonObjectWithNestedObject;
    private JsonArray emptyJsonArray;
    private WriterConfig underTest;

    @Before
    public void setUp() {
        emptyJsonObject = Json.object();
        jsonObjectWithValues = Json.object().add(NAME_FOO, VALUE_FOO).add(NAME_BAR, VALUE_BAR);
        jsonObjectWithNestedObject = Json.object()
                .add(NAME_FOO, VALUE_FOO)
                .add(NAME_BAR, VALUE_BAR)
                .add(NAME_NESTED, jsonObjectWithValues);
        emptyJsonArray = Json.array();
        underTest = PrettyPrintEmptyElementsWriter.indentWithSpaces(2);
    }

    @Test
    public void stringOfEmptyJsonObjectIsExpected() {
        final String expected = "{}";

        final String actual = emptyJsonObject.toString(underTest);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void stringOfJsonObjectWithValuesIsExpected() {
        final String expected = "{\n" +
                "  \"" + NAME_FOO + "\": " + "\"" + VALUE_FOO + "\",\n" +
                "  \"" + NAME_BAR + "\": " + "\"" + VALUE_BAR + "\"\n" +
                "}";

        final String actual = jsonObjectWithValues.toString(underTest);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void stringOfJsonObjectWithNestedEmptyObjectIsExpected() {
        final String expected = "{\n" +
                "  \"" + NAME_FOO + "\": " + "\"" + VALUE_FOO + "\",\n" +
                "  \"" + NAME_BAR + "\": " + "\"" + VALUE_BAR + "\",\n" +
                "  \"" + NAME_NESTED + "\": " + "{}\n" +
                "}";

        final JsonValue jsonObject = Json.object()
                .add(NAME_FOO, VALUE_FOO)
                .add(NAME_BAR, VALUE_BAR)
                .add(NAME_NESTED, emptyJsonObject);
        final String actual = jsonObject.toString(underTest);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void stringOfJsonObjectWithNestedObjectIsExpected() {
        final String expected = "{\n" +
                "  \"" + NAME_FOO + "\": " + "\"" + VALUE_FOO + "\",\n" +
                "  \"" + NAME_BAR + "\": " + "\"" + VALUE_BAR + "\",\n" +
                "  \"" + NAME_NESTED + "\": " + "{\n" +
                "    \"" + NAME_FOO + "\": " + "\"" + VALUE_FOO + "\",\n" +
                "    \"" + NAME_BAR + "\": " + "\"" + VALUE_BAR + "\"\n" +
                "  }\n" +
                "}";

        final String actual = jsonObjectWithNestedObject.toString(underTest);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void stringOfJsonObjectWithNestedObjectAndArrayIsExpected() {
        final String nameValueArray = "theArray";
        final String nameEmptyArray = "emptyArray";
        final int number = 42;
        final String expected = "{\n" +
                "  \"" + NAME_FOO + "\": " + "\"" + VALUE_FOO + "\",\n" +
                "  \"" + nameValueArray + "\": " + "[\n" +
                "    " + number + ",\n" +
                "    \"" + VALUE_BAR + "\"\n" +
                "  ],\n" +
                "  \"" + NAME_BAR + "\": " + "\"" + VALUE_BAR + "\",\n" +
                "  \"" + NAME_NESTED + "\": " + "{\n" +
                "    \"" + NAME_FOO + "\": " + "\"" + VALUE_FOO + "\",\n" +
                "    \"" + NAME_BAR + "\": " + "\"" + VALUE_BAR + "\"\n" +
                "  },\n" +
                "  \"" + nameEmptyArray + "\": " + "[]\n" +
                "}";

        final JsonObject jsonObject = Json.object()
                .add(NAME_FOO, VALUE_FOO)
                .add(nameValueArray, Json.array().add(number).add(VALUE_BAR))
                .add(NAME_BAR, VALUE_BAR)
                .add(NAME_NESTED, jsonObjectWithValues)
                .add(nameEmptyArray, Json.array());

        final String actual = jsonObject.toString(underTest);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void stringOfEmptyJsonArrayIsExpected() {
        final String expected = "[]";

        final String actual = emptyJsonArray.toString(underTest);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void stringOfArrayWithOneStringValueIsExpected() {
        final String expected = "[\n" +
                "  \"" + VALUE_FOO + "\"\n" +
                "]";
        final JsonArray jsonArray = Json.array(VALUE_FOO);

        final String actual = jsonArray.toString(underTest);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void stringOfArrayWithMultipleStringValuesIsExpected() {
        final String expected = "[\n" +
                "  \"" + VALUE_FOO + "\",\n" +
                "  \"" + VALUE_BAR + "\"\n" +
                "]";
        final JsonArray jsonArray = Json.array(VALUE_FOO, VALUE_BAR);

        final String actual = jsonArray.toString(underTest);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void stringOfArrayWithOneNumberValueIsExpected() {
        final int number = 23;
        final String expected = "[\n" +
                "  " + number + "\n" +
                "]";
        final JsonArray jsonArray = Json.array(number);

        final String actual = jsonArray.toString(underTest);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void stringOfArrayWithMultipleValuesIsExpected() {
        final int number = 23;
        final String expected = "[\n" +
                "  " + number + ",\n" +
                "  \"" + VALUE_BAR + "\",\n" +
                "  {\n" +
                "    \"" + NAME_FOO + "\": " + "\"" + VALUE_FOO + "\",\n" +
                "    \"" + NAME_BAR + "\": " + "\"" + VALUE_BAR + "\",\n" +
                "    \"" + NAME_NESTED + "\": " + "{\n" +
                "      \"" + NAME_FOO + "\": " + "\"" + VALUE_FOO + "\",\n" +
                "      \"" + NAME_BAR + "\": " + "\"" + VALUE_BAR + "\"\n" +
                "    }\n" +
                "  }\n" +
                "]";
        final JsonArray jsonArray = Json.array()
                .add(number)
                .add(VALUE_BAR)
                .add(jsonObjectWithNestedObject);

        final String actual = jsonArray.toString(underTest);

        assertThat(actual).isEqualTo(expected);
    }

}
