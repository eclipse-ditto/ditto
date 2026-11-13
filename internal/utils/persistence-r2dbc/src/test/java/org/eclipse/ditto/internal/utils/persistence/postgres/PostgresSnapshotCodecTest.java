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
package org.eclipse.ditto.internal.utils.persistence.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.nio.charset.StandardCharsets;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.junit.Test;

/**
 * Unit test for {@link PostgresSnapshotCodec}: encode→decode round-trip identity across the JSONB shapes the r2dbc
 * driver surfaces (JsonObject / String / byte[]) and the {@code IllegalArgumentException} on an undecodable shape.
 * Runs fully offline.
 */
public final class PostgresSnapshotCodecTest {

    private static final PostgresSnapshotCodec CODEC = PostgresSnapshotCodec.INSTANCE;

    private static final JsonObject SNAPSHOT = JsonFactory.newObjectBuilder()
            .set("thingId", "org.eclipse:device-1")
            .set("policyId", "org.eclipse:policy-1")
            .set("_revision", 42)
            .set("attributes", JsonFactory.newObjectBuilder().set("location", "kitchen").build())
            .build();

    @Test
    public void encodeProducesJsonbText() {
        final Object encoded = CODEC.encode(SNAPSHOT);

        // The JSONB store value the r2dbc driver binds to the ::jsonb column is JSON text.
        assertThat(encoded).isInstanceOf(String.class);
        assertThat(JsonFactory.newObject((String) encoded)).isEqualTo(SNAPSHOT);
    }

    @Test
    public void roundTripViaEncodedStringIsIdentity() {
        final Object encoded = CODEC.encode(SNAPSHOT);

        final JsonObject decoded = CODEC.decode(encoded);

        assertThat(decoded).isEqualTo(SNAPSHOT);
    }

    @Test
    public void decodeAcceptsStringShape() {
        assertThat(CODEC.decode(SNAPSHOT.toString())).isEqualTo(SNAPSHOT);
    }

    @Test
    public void decodeAcceptsByteArrayShape() {
        final byte[] jsonbBytes = SNAPSHOT.toString().getBytes(StandardCharsets.UTF_8);

        assertThat(CODEC.decode(jsonbBytes)).isEqualTo(SNAPSHOT);
    }

    @Test
    public void decodeAcceptsAlreadyParsedJsonObjectShape() {
        // The same instance is returned as-is when the driver already surfaces a JsonObject.
        assertThat(CODEC.decode(SNAPSHOT)).isSameAs(SNAPSHOT);
    }

    @Test
    public void decodeRejectsUndecodableShapeWithIllegalArgumentException() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> CODEC.decode(42))
                .withMessageContaining("JSONB-decodable");
    }

    @Test
    public void encodeRejectsNull() {
        assertThatNullPointerException().isThrownBy(() -> CODEC.encode(null));
    }

    @Test
    public void decodeRejectsNull() {
        assertThatNullPointerException().isThrownBy(() -> CODEC.decode(null));
    }
}
