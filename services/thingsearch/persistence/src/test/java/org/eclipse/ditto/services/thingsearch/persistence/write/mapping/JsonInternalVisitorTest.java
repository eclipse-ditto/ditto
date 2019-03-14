/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.thingsearch.persistence.write.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonNumber;
import org.eclipse.ditto.json.JsonObject;
import org.junit.Test;

/**
 * Tests {@link JsonInternalVisitor}.
 */
public final class JsonInternalVisitorTest implements JsonInternalVisitor<String> {

    @Override
    public String nullValue() {
        return "null";
    }

    @Override
    public String bool(final boolean value) {
        return Boolean.toString(value);
    }

    @Override
    public String string(final String value) {
        return String.format("\"%s\"", value.replaceAll("\"", "\\\""));
    }

    @Override
    public String number(final JsonNumber value) {
        return value.toString();
    }

    @Override
    public String array(final Stream<String> values) {
        return values.collect(Collectors.joining(",", "[", "]"));
    }

    @Override
    public String object(final Stream<Map.Entry<String, String>> values) {
        return values.map(entry -> String.format("%s:%s", string(entry.getKey()), entry.getValue()))
                .collect(Collectors.joining(",", "{", "}"));
    }

    @Test
    public void testJsonInternalVisitor() {
        final String jsonString = "{\n" +
                "  \"a\": [ {\"b\": \"c\"}, true ],\n" +
                "  \"d\": {\n" +
                "    \"e\": {\n" +
                "      \"f\": \"g\",\n" +
                "      \"h\": \"i\"\n" +
                "    },\n" +
                "    \"j\": 5,\n" +
                "    \"k\": 6.0,\n" +
                "    \"l\": 123456789012\n" +
                "  }\n" +
                "}";

        final JsonObject jsonObject = JsonFactory.newObject(jsonString);

        assertThat(eval(jsonObject)).isEqualTo(jsonObject.toString());
    }
}
