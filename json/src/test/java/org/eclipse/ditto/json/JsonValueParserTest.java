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
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.math.BigDecimal;
import java.util.function.Consumer;
import java.util.function.Function;

import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Unit test for {@link org.eclipse.ditto.json.JsonValueParser}.
 */
public final class JsonValueParserTest {

    private static JsonArray knownJsonArray;
    private static JsonObject knownJsonObject;

    @BeforeClass
    public static void initTestConstants() {
        knownJsonArray = JsonArray.newBuilder()
                .add("hubbl")
                .add("fubbl")
                .add(false)
                .add(3)
                .build();

        knownJsonObject = JsonObject.newBuilder()
                .set("foo", "bar")
                .set("bar", knownJsonArray)
                .set("baz", JsonObject.newBuilder()
                        .set("hubbl", 1)
                        .set("buggl", true)
                        .build()
                )
                .build();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(JsonValueParser.class, areImmutable());
    }

    @Test
    public void checkExceptionHandlingWithCustomDittoJsonHandler() {
        final String invalidJsonObjectString = "{\"foo\":123";
        final DittoJsonHandler<?, ?, ?> jsonHandlerMock = Mockito.mock(DittoJsonHandler.class);

        final Consumer<String> underTest = JsonValueParser.fromString(jsonHandlerMock);

        assertThatExceptionOfType(JsonParseException.class)
                .isThrownBy(() -> underTest.accept(invalidJsonObjectString))
                .withMessage("Failed to parse JSON string '%s'!", invalidJsonObjectString)
                .withCauseExactlyInstanceOf(com.eclipsesource.json.ParseException.class);
    }

    @Test
    public void parseStringToArray() {
        final Function<String, JsonValue> underTest = JsonValueParser.fromString();

        final JsonValue actual = underTest.apply(knownJsonArray.toString());

        assertThat(actual).isEqualTo(knownJsonArray);
    }

    @Test
    public void parseStringToObject() {
        final Function<String, JsonValue> underTest = JsonValueParser.fromString();

        final JsonValue actual = underTest.apply(knownJsonObject.toString());

        assertThat(actual).isEqualTo(knownJsonObject);
    }

    @Test
    public void parseStringToBoolean() {
        final JsonValue jsonBoolean = JsonValue.of(true);
        final Function<String, JsonValue> underTest = JsonValueParser.fromString();

        final JsonValue actual = underTest.apply(jsonBoolean.toString());

        assertThat(actual).isEqualTo(jsonBoolean);
    }

    @Test
    public void parseStringToInt() {
        final JsonValue jsonInt = JsonValue.of(Integer.MAX_VALUE);
        final Function<String, JsonValue> underTest = JsonValueParser.fromString();

        final JsonValue actual = underTest.apply(jsonInt.toString());

        assertThat(actual).isEqualTo(jsonInt);
    }

    @Test
    public void parseStringToLong() {
        final JsonValue jsonLong = JsonValue.of(Integer.MAX_VALUE + 1L);
        final Function<String, JsonValue> underTest = JsonValueParser.fromString();

        final JsonValue actual = underTest.apply(jsonLong.toString());

        assertThat(actual).isEqualTo(jsonLong);
    }

    @Test
    public void parseStringToDecimalNumber() {
        final JsonValue jsonDouble = JsonValue.of(23.42F);
        final Function<String, JsonValue> underTest = JsonValueParser.fromString();

        final JsonValue actual = underTest.apply(jsonDouble.toString());

        assertThat(actual).isEqualTo(jsonDouble);
    }

    @Test
    public void parseStringToString() {
        final JsonValue jsonString = JsonValue.of("Insprinc haftbandun, infar wîgandun!");
        final Function<String, JsonValue> underTest = JsonValueParser.fromString();

        final JsonValue actual = underTest.apply(jsonString.toString());

        assertThat(actual).isEqualTo(jsonString);
    }

    @Test
    public void testParseNullLiteralToNull() {
        final JsonValue jsonNullLiteral = JsonFactory.nullLiteral();
        final Function<String, JsonValue> underTest = JsonValueParser.fromString();

        final JsonValue actual = underTest.apply("null");

        assertThat(actual).isEqualTo(jsonNullLiteral);
    }

    @Test
    public void wrapsNumberFormatException() {
        final String jsonLong = BigDecimal.valueOf(Long.MAX_VALUE).add(BigDecimal.TEN).toPlainString();
        final Function<String, JsonValue> underTest = JsonValueParser.fromString();

        assertThatExceptionOfType(JsonParseException.class)
                .isThrownBy(() -> underTest.apply(jsonLong))
                .withMessage("Failed to parse JSON string '%s'!", jsonLong)
                .withCauseExactlyInstanceOf(java.lang.NumberFormatException.class);
    }

    @Test
    public void wrapsNullPointerException() {
        final Function<String, JsonValue> underTest = JsonValueParser.fromString();

        //noinspection ConfusingArgumentToVarargsMethod
        assertThatExceptionOfType(JsonParseException.class)
                .isThrownBy(() -> underTest.apply(null))
                .withMessage("Failed to parse JSON string '%s'!", null)
                .withCauseExactlyInstanceOf(java.lang.NullPointerException.class);
    }

}
