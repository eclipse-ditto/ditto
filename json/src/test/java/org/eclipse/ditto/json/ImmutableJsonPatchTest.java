/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.json;

import static org.eclipse.ditto.json.JsonPatch.Operation.ADD;
import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;

import org.junit.Test;

/**
 * Unit test for {@link ImmutableJsonPatch}.
 */
public final class ImmutableJsonPatchTest {

    private static final JsonPatch.Operation OPERATION = ADD;
    private static final JsonPointer PATH = JsonFactory.newPointer("new");
    private static final JsonValue VALUE = JsonFactory.newValue(2);

    /** */
    @Test
    public void testOf() {
        final ImmutableJsonPatch jsonPatch = ImmutableJsonPatch.of(OPERATION, PATH, VALUE);

        assertThat(jsonPatch.getOperation()).isEqualTo(OPERATION);
        assertThat(jsonPatch.getPath()).isEqualTo(PATH);
        assertThat(jsonPatch.getValue()).contains(VALUE);
    }

    /** */
    @Test
    public void testFromJson() {
        final JsonObject jsonObject = JsonFactory.newObjectBuilder()
                .set(JsonPatch.JsonFields.OPERATION, OPERATION.toString())
                .set(JsonPatch.JsonFields.PATH, PATH.toString())
                .set(JsonPatch.JsonFields.VALUE, VALUE)
                .build();

        final ImmutableJsonPatch jsonPatch = ImmutableJsonPatch.fromJson(jsonObject.toString());

        assertThat(jsonPatch.getOperation()).isEqualTo(OPERATION);
        assertThat(jsonPatch.getPath()).isEqualTo(PATH);
        assertThat(jsonPatch.getValue()).contains(VALUE);
    }

    /** */
    @Test
    public void testToJson() {
        final ImmutableJsonPatch jsonPatch = ImmutableJsonPatch.of(OPERATION, PATH, VALUE);
        final JsonObject jsonObject = jsonPatch.toJson();

        assertThat(jsonObject)
                .contains(JsonPatch.JsonFields.OPERATION, OPERATION.toString())
                .contains(JsonPatch.JsonFields.PATH, PATH.toString())
                .contains(JsonPatch.JsonFields.VALUE, VALUE);
    }

    // ------------------ similar tests with null as value ----------------

    /** */
    @Test
    public void testOfWithNull() {
        final ImmutableJsonPatch jsonPatch = ImmutableJsonPatch.of(OPERATION, PATH, null);

        assertThat(jsonPatch.getOperation()).isEqualTo(OPERATION);
        assertThat(jsonPatch.getPath()).isEqualTo(PATH);
        assertThat(jsonPatch.getValue()).isEmpty();
    }

    /** */
    @Test
    public void testFromJsonWithNull() {
        final JsonObject jsonObject = JsonFactory.newObjectBuilder()
                .set(JsonPatch.JsonFields.OPERATION, OPERATION.toString())
                .set(JsonPatch.JsonFields.PATH, PATH.toString())
                .build();

        final ImmutableJsonPatch jsonPatch = ImmutableJsonPatch.fromJson(jsonObject.toString());

        assertThat(jsonPatch.getOperation()).isEqualTo(OPERATION);
        assertThat(jsonPatch.getPath()).isEqualTo(PATH);
        assertThat(jsonPatch.getValue()).isEmpty();
    }

    /** */
    @Test
    public void testFromJsonWithNullLiteral() {
        final JsonObject jsonObject = JsonFactory.newObjectBuilder()
                .set(JsonPatch.JsonFields.OPERATION, OPERATION.toString())
                .set(JsonPatch.JsonFields.PATH, PATH.toString())
                .set(JsonPatch.JsonFields.VALUE, JsonFactory.nullLiteral())
                .build();

        final ImmutableJsonPatch jsonPatch = ImmutableJsonPatch.fromJson(jsonObject.toString());

        assertThat(jsonPatch.getOperation()).isEqualTo(OPERATION);
        assertThat(jsonPatch.getPath()).isEqualTo(PATH);
        assertThat(jsonPatch.getValue()).contains(JsonFactory.nullLiteral());
    }

    /** */
    @Test
    public void testToJsonWithNull() {
        final ImmutableJsonPatch jsonPatch = ImmutableJsonPatch.of(OPERATION, PATH, null);
        final JsonObject jsonObject = jsonPatch.toJson();

        assertThat(jsonObject)
                .contains(JsonPatch.JsonFields.OPERATION, OPERATION.toString())
                .contains(JsonPatch.JsonFields.PATH, PATH.toString())
                .doesNotContain(JsonPatch.JsonFields.VALUE);
    }

    /** */
    @Test
    public void testToJsonWithNullLiteral() {
        final ImmutableJsonPatch jsonPatch = ImmutableJsonPatch.of(OPERATION, PATH, JsonFactory.nullLiteral());
        final JsonObject jsonObject = jsonPatch.toJson();

        assertThat(jsonObject)
                .contains(JsonPatch.JsonFields.OPERATION, OPERATION.toString())
                .contains(JsonPatch.JsonFields.PATH, PATH.toString())
                .contains(JsonPatch.JsonFields.VALUE, JsonFactory.nullLiteral());
    }

    /** */
    @Test
    public void testToString() {
        final ImmutableJsonPatch jsonPatch = ImmutableJsonPatch.of(OPERATION, PATH, VALUE);
        final String actual = jsonPatch.toString();
        final String expected = "{\"op\":\"" + OPERATION + "\",\"path\":\"" + PATH + "\",\"value\":" + VALUE + "}";

        assertThat(actual).isEqualTo(expected);
    }

    /** */
    @Test
    public void testToStringWithNullLiteral() {
        final ImmutableJsonPatch jsonPatch = ImmutableJsonPatch.of(OPERATION, PATH, JsonFactory.nullLiteral());
        final String actual = jsonPatch.toString();
        final String expected =
                "{\"op\":\"" + OPERATION + "\",\"path\":\"" + PATH + "\",\"value\":" + JsonFactory.nullLiteral() + "}";

        assertThat(actual).isEqualTo(expected);
    }

}
