/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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

import java.lang.reflect.Field;

import org.junit.Test;

/**
 * Tests the equality of {@link ImmutableJsonObject}s whose fields hold values of different classes which render the
 * same JSON, and that comparing different objects does not render them.
 */
public final class JsonObjectEqualityTest {

    @Test
    public void nullKindsAreEqual() {
        assertMutuallyEqual(object("a", JsonFactory.nullObject()), object("a", JsonFactory.nullLiteral()));
        assertMutuallyEqual(object("a", JsonFactory.nullArray()), object("a", JsonFactory.nullLiteral()));
    }

    @Test
    public void nullKindsInNestedArraysAndObjectsAreEqual() {
        assertMutuallyEqual(
                object("a", JsonArray.of(JsonFactory.nullObject(), JsonValue.of(1))),
                object("a", JsonArray.of(JsonFactory.nullLiteral(), JsonValue.of(1))));
        assertMutuallyEqual(
                object("a", object("b", object("c", JsonFactory.nullObject()))),
                object("a", object("b", object("c", JsonFactory.nullLiteral()))));
    }

    @Test
    public void nullKindIsNotEqualToEmptyObjectOrArray() {
        assertMutuallyNotEqual(object("a", JsonFactory.nullObject()), object("a", JsonObject.empty()));
        assertMutuallyNotEqual(object("a", JsonFactory.nullArray()), object("a", JsonArray.empty()));
    }

    @Test
    public void numbersAreComparedByValue() {
        assertMutuallyEqual(object("a", JsonValue.of(1)), object("a", JsonValue.of(1L)));
        assertMutuallyEqual(object("a", JsonValue.of(1)), object("a", JsonValue.of(1.0)));
        assertMutuallyNotEqual(object("a", JsonValue.of(1)), object("a", JsonValue.of(1.5)));
        assertMutuallyNotEqual(object("a", JsonValue.of(1)), object("a", JsonValue.of("1")));
    }

    @Test
    public void fieldOrderDoesNotMatter() {
        final JsonObject ab = JsonObject.newBuilder().set("a", 1).set("b", JsonFactory.nullObject()).build();
        final JsonObject ba = JsonObject.newBuilder().set("b", JsonFactory.nullLiteral()).set("a", 1).build();

        assertMutuallyEqual(ab, ba);
    }

    @Test
    public void objectsWithDifferentFieldsOrValuesAreNotEqual() {
        assertMutuallyNotEqual(object("a", JsonValue.of(1)), object("b", JsonValue.of(1)));
        assertMutuallyNotEqual(object("a", JsonValue.of(1)),
                JsonObject.newBuilder().set("a", 1).set("b", 2).build());
        assertMutuallyNotEqual(object("a", object("b", JsonValue.of(true))),
                object("a", object("b", JsonValue.of(false))));
        assertMutuallyNotEqual(object("a", JsonArray.of(JsonValue.of(1), JsonValue.of(2))),
                object("a", JsonArray.of(JsonValue.of(2), JsonValue.of(1))));
    }

    @Test
    public void comparingDifferentObjectsDoesNotRenderThem() throws Exception {
        final JsonObject one = object("properties", object("temperature", JsonValue.of(21.5)));
        final JsonObject other = object("properties", object("temperature", JsonValue.of(22.0)));

        assertThat(one).isNotEqualTo(other);

        for (final JsonObject jsonObject : new JsonObject[]{one, other,
                one.getValue("properties").orElseThrow(AssertionError::new).asObject(),
                other.getValue("properties").orElseThrow(AssertionError::new).asObject()}) {
            assertThat(stringRepresentationOf(jsonObject)).isNull();
        }
    }

    @Test
    public void replacingAnObjectFieldDoesNotRenderTheOldOrNewValue() throws Exception {
        final JsonObject oldProperties = object("temperature", JsonValue.of(21.5));
        final JsonObject newProperties = object("temperature", JsonValue.of(22.0));
        final JsonObject feature = object("properties", oldProperties);

        final JsonObject updated = feature.setValue("properties", newProperties);

        assertThat(updated.getValue("properties")).contains(newProperties);
        assertThat(stringRepresentationOf(oldProperties)).isNull();
        assertThat(stringRepresentationOf(newProperties)).isNull();
    }

    private static JsonObject object(final String key, final JsonValue value) {
        return JsonObject.newBuilder().set(key, value).build();
    }

    private static void assertMutuallyEqual(final JsonObject one, final JsonObject other) {
        assertThat(one).isEqualTo(other);
        assertThat(other).isEqualTo(one);
    }

    private static void assertMutuallyNotEqual(final JsonObject one, final JsonObject other) {
        assertThat(one).isNotEqualTo(other);
        assertThat(other).isNotEqualTo(one);
    }

    private static Object stringRepresentationOf(final JsonObject jsonObject) throws Exception {
        final Field fieldMapField = ImmutableJsonObject.class.getDeclaredField("fieldMap");
        fieldMapField.setAccessible(true);
        final Object fieldMap = fieldMapField.get(jsonObject);
        final Field representation = fieldMap.getClass().getDeclaredField("jsonObjectStringRepresentation");
        representation.setAccessible(true);
        return representation.get(fieldMap);
    }

}
