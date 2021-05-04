/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.thingsearch.service.persistence.write.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonNumber;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.junit.Test;

/**
 * Tests {@link JsonObjectVisitor}.
 */
public final class JsonObjectVisitorTest implements JsonObjectVisitor<List<String>> {

    private static String render(final JsonPointer key, final Object value) {
        return String.format("%s=%s", key, value);
    }

    @Override
    public List<String> nullValue(final JsonPointer key) {
        return Collections.singletonList(render(key, null));
    }

    @Override
    public List<String> bool(final JsonPointer key, final boolean value) {
        return Collections.singletonList(render(key, value));
    }

    @Override
    public List<String> string(final JsonPointer key, final String value) {
        return Collections.singletonList(render(key, value));
    }

    @Override
    public List<String> number(final JsonPointer key, final JsonNumber value) {
        return Collections.singletonList(render(key, value));
    }

    @Override
    public List<String> array(final JsonPointer key, final Stream<List<String>> values) {
        return values.flatMap(List::stream).collect(Collectors.toList());
    }

    @Override
    public List<String> object(final JsonPointer key, final Stream<List<String>> values) {
        return values.flatMap(List::stream).collect(Collectors.toList());
    }

    @Test
    public void testJsonObjectVisitor() {
        final String jsonString = "{\n" +
                "  \"a\": [ {\"b\": \"c\"}, true, 5 ],\n" +
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

        final List<String> expected =
                Arrays.asList(
                        "/a/b=c",
                        "/a=true",
                        "/a=5",
                        "/d/e/f=g",
                        "/d/e/h=i",
                        "/d/j=5",
                        "/d/k=6.0",
                        "/d/l=123456789012");

        assertThat(eval(jsonObject)).isEqualTo(expected);
    }
}
