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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.assertj.core.data.MapEntry;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Unit test for {@link org.eclipse.ditto.json.FieldMapJsonHandlerTest}.
 * This class is indirectly tested which reflects the real use case.
 */
public final class FieldMapJsonHandlerTest {

    private static JsonArray knownJsonArray;
    private static JsonObject knownJsonObject;

    private ImmutableJsonObject.FieldMapJsonHandler underTest;
    private Consumer<String> parser;

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
                        .set("int", Integer.MAX_VALUE)
                        .set("boolean", true)
                        .set("double", 23.42D)
                        .set("long", Long.MAX_VALUE)
                        .set("object", JsonObject.newBuilder()
                                .set("unu", "asdf")
                                .set("du", "jkl;")
                                .build()
                        )
                        .build()
                )
                .build();
    }

    @Before
    public void setUp() {
        underTest = new ImmutableJsonObject.FieldMapJsonHandler();
        parser = JsonValueParser.fromString(underTest);
    }

    @Test
    public void tryToParseNullString() {
        assertThatExceptionOfType(JsonParseException.class)
                .isThrownBy(() -> parser.accept(null))
                .withCauseExactlyInstanceOf(NullPointerException.class);
    }

    @Test
    public void parseEmptyObjectStringToEmptyMap() {
        parser.accept(JsonObject.empty().toString());

        assertThat(underTest.getValue()).isEmpty();
    }

    @Test
    public void parseObjectString() {
        final Map<String, JsonField> expected = new LinkedHashMap<>();
        expected.put("foo", JsonField.newInstance("foo", JsonValue.of("bar")));
        expected.put("bar", JsonField.newInstance("bar", knownJsonArray));
        expected.put("baz", JsonField.newInstance("baz", JsonObject.newBuilder()
                .set("int", Integer.MAX_VALUE)
                .set("boolean", true)
                .set("double", 23.42D)
                .set("long", Long.MAX_VALUE)
                .set("object", JsonObject.newBuilder()
                        .set("unu", "asdf")
                        .set("du", "jkl;")
                        .build()
                )
                .build()));

        parser.accept(knownJsonObject.toString());
        final Map<String, JsonField> actual = underTest.getValue();

        assertThat(actual).isEqualTo(expected);
    }

    private static Map.Entry<String, JsonField> getEntry(final String key, final JsonValue value) {
        return MapEntry.entry(key, JsonField.newInstance(key, value));
    }

}
