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
package org.eclipse.ditto.base.model.entity.metadata;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.junit.Test;

/**
 * Tests that JsonObjects holding {@link Metadata} have the same hash code as equal JsonObjects holding the plain JSON
 * instead, as required by the {@code equals}/{@code hashCode} contract.
 */
public final class MetadataHashCodeTest {

    private static final JsonObject JSON = JsonObject.newBuilder()
            .set("issuedAt", "2026-11-24T10:00:00Z")
            .set("sources", JsonArray.of(JsonValue.of("sensor-1")))
            .build();

    @Test
    public void metadataHasTheHashCodeOfTheWrappedJson() {
        final Metadata metadata = MetadataModelFactory.newMetadata(JSON);

        assertThat(metadata).hasSameHashCodeAs(JSON);
        assertEqualWithSameHashCodeWhenHeld(metadata, JSON);
    }

    @Test
    public void emptyMetadataHasTheHashCodeOfAnEmptyObject() {
        final Metadata metadata = MetadataModelFactory.emptyMetadata();

        assertThat(metadata).hasSameHashCodeAs(JsonObject.empty());
        assertEqualWithSameHashCodeWhenHeld(metadata, JsonObject.empty());
    }

    @Test
    public void nullMetadataHasTheHashCodeOfJsonNull() {
        final Metadata nullMetadata = MetadataModelFactory.nullMetadata();

        assertThat(nullMetadata).hasSameHashCodeAs(JsonFactory.nullLiteral());
        assertEqualWithSameHashCodeWhenHeld(nullMetadata, JsonFactory.nullLiteral());
        assertEqualWithSameHashCodeWhenHeld(nullMetadata, JsonFactory.nullObject());
    }

    @Test
    public void metadataOfOtherJsonIsNotEqualWhenHeld() {
        final Metadata otherMetadata = MetadataModelFactory.newMetadata(JSON.setValue("issuedAt", "other"));

        assertThat(object("_metadata", otherMetadata)).isNotEqualTo(object("_metadata", JSON));
        assertThat(object("_metadata", MetadataModelFactory.nullMetadata()))
                .isNotEqualTo(object("_metadata", JsonObject.empty()));
    }

    /**
     * Asserts that JsonObjects holding {@code wrapper} or {@code json} directly, nested in another object and inside
     * an array are mutually equal and have the same hash code.
     */
    private static void assertEqualWithSameHashCodeWhenHeld(final JsonValue wrapper, final JsonValue json) {
        assertMutuallyEqualWithSameHashCode(object("value", wrapper), object("value", json));
        assertMutuallyEqualWithSameHashCode(object("outer", object("value", wrapper)),
                object("outer", object("value", json)));
        assertMutuallyEqualWithSameHashCode(object("list", JsonArray.of(object("value", wrapper))),
                object("list", JsonArray.of(object("value", json))));
    }

    private static void assertMutuallyEqualWithSameHashCode(final JsonObject one, final JsonObject other) {
        assertThat(one).isEqualTo(other);
        assertThat(other).isEqualTo(one);
        assertThat(one).hasSameHashCodeAs(other);
    }

    private static JsonObject object(final String key, final JsonValue value) {
        return JsonObject.newBuilder().set(key, value).build();
    }

}
