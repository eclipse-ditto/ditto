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
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

/**
 * Tests the factories which take over the fields/values of new JSON objects/arrays instead of copying them, and that
 * the others still copy.
 */
public final class TakingOwnershipTest {

    private static final JsonField FOO = JsonField.newInstance("foo", JsonValue.of(1));
    private static final JsonField BAR = JsonField.newInstance("bar", JsonValue.of("x"));

    @Test
    public void objectTakingOwnershipEqualsCopyingObject() {
        final JsonObject takingOwnership = JsonFactory.createJsonObjectTakingOwnership(fields());

        assertThat(takingOwnership).isEqualTo(ImmutableJsonObject.of(fields()));
        assertThat(takingOwnership.toString()).isEqualTo("{\"foo\":1,\"bar\":\"x\"}");
        assertThat(takingOwnership.getKeys()).containsExactly(JsonKey.of("foo"), JsonKey.of("bar"));
    }

    @Test
    public void objectTakingOwnershipDoesNotCopyFields() {
        final Map<String, JsonField> fields = fields();
        final JsonObject takingOwnership = JsonFactory.createJsonObjectTakingOwnership(fields);

        // only to prove that the map was not copied - callers must never do this
        fields.remove("foo");

        assertThat(takingOwnership.getSize()).isEqualTo(1);
    }

    @Test
    public void copyingObjectFactoriesStillCopyFields() {
        final Map<String, JsonField> fields = fields();
        final JsonObject viaOf = ImmutableJsonObject.of(fields);
        final JsonObject viaCreateJsonObject = JsonFactory.createJsonObject(fields, null);

        fields.remove("foo");

        assertThat(viaOf.getSize()).isEqualTo(2);
        assertThat(viaCreateJsonObject.getSize()).isEqualTo(2);
    }

    @Test
    public void objectTakingOwnershipIsImmutable() {
        final JsonObject takingOwnership = JsonFactory.createJsonObjectTakingOwnership(fields());
        final java.util.Iterator<JsonField> iterator = takingOwnership.iterator();
        iterator.next();

        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(iterator::remove);
    }

    @Test
    public void arrayTakingOwnershipEqualsCopyingArray() {
        final JsonArray takingOwnership = JsonFactory.createJsonArrayTakingOwnership(values());

        assertThat(takingOwnership).isEqualTo(ImmutableJsonArray.of(values()));
        assertThat(takingOwnership.toString()).isEqualTo("[1,\"x\"]");
    }

    @Test
    public void arrayTakingOwnershipDoesNotCopyValues() {
        final List<JsonValue> values = values();
        final JsonArray takingOwnership = JsonFactory.createJsonArrayTakingOwnership(values);

        // only to prove that the list was not copied - callers must never do this
        values.remove(0);

        assertThat(takingOwnership.getSize()).isEqualTo(1);
    }

    @Test
    public void copyingArrayFactoriesStillCopyValues() {
        final List<JsonValue> values = values();
        final JsonArray viaOf = ImmutableJsonArray.of(values);
        final JsonArray viaCreateJsonArray = JsonFactory.createJsonArray(values, null);

        values.remove(0);

        assertThat(viaOf.getSize()).isEqualTo(2);
        assertThat(viaCreateJsonArray.getSize()).isEqualTo(2);
    }

    @Test
    public void arrayTakingOwnershipIsImmutable() {
        final JsonArray takingOwnership = JsonFactory.createJsonArrayTakingOwnership(values());
        final java.util.Iterator<JsonValue> iterator = takingOwnership.iterator();
        iterator.next();

        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(iterator::remove);
    }

    @Test
    public void parsedJsonStringIsUnchanged() {
        final String json = "{\"a\":{\"b\":[1,1.50,\"c\",{\"d\":null}]},\"e\":[]}";

        final JsonObject parsed = JsonObject.of(json);

        // the string representation of parsed JSON keeps the original number format
        assertThat(parsed.toString()).isEqualTo(json);
        assertThat(parsed).isEqualTo(JsonObject.newBuilder()
                .set("a", JsonObject.newBuilder()
                        .set("b", JsonArray.of(JsonValue.of(1), JsonValue.of(1.5), JsonValue.of("c"),
                                JsonObject.newBuilder().set("d", JsonValue.nullLiteral()).build()))
                        .build())
                .set("e", JsonArray.empty())
                .build());
    }

    private static Map<String, JsonField> fields() {
        final Map<String, JsonField> result = new LinkedHashMap<>();
        result.put(FOO.getKeyName(), FOO);
        result.put(BAR.getKeyName(), BAR);
        return result;
    }

    private static List<JsonValue> values() {
        final List<JsonValue> result = new ArrayList<>();
        result.add(JsonValue.of(1));
        result.add(JsonValue.of("x"));
        return result;
    }

}
