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
package org.eclipse.ditto.internal.utils.persistence.postgres.payload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Instant;

import org.eclipse.ditto.json.JsonFactory;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

/**
 * Unit tests for {@link SnapshotKey}: the cursor round-trips the primary key of a snapshot row exactly — any
 * character in the pid, microsecond timestamps — and a cursor the store did not produce is rejected naming the
 * cursor.
 */
public final class SnapshotKeyTest {

    private static final Instant MICROS = Instant.parse("2026-11-06T10:15:30.123456Z");

    @Test
    public void equalsAndHashCode() {
        // the compact constructor rejects null components, so EqualsVerifier must not probe them with null
        EqualsVerifier.forClass(SnapshotKey.class).suppress(Warning.NULL_FIELDS).verify();
    }

    @Test
    public void cursorRoundTripsThePrimaryKey() {
        final SnapshotKey key = new SnapshotKey("connection:ns:id", 7L, MICROS);

        assertThat(SnapshotKey.fromCursor(key.toCursor())).isEqualTo(key);
    }

    @Test
    public void cursorRoundTripsAPidWithAnyCharacter() {
        final SnapshotKey key = new SnapshotKey("connection:a|b\"c\\d/e ünïcödé ☃ {\"x\":1}", 1L, MICROS);

        assertThat(SnapshotKey.fromCursor(key.toCursor())).isEqualTo(key);
    }

    @Test
    public void cursorKeepsTheMicrosecondPrecisionAndTheNanosecondsAlike() {
        final SnapshotKey micros = new SnapshotKey("p", 1L, MICROS);
        final SnapshotKey nanos = new SnapshotKey("p", 1L, Instant.parse("2026-11-06T10:15:30.123456789Z"));

        assertThat(SnapshotKey.fromCursor(micros.toCursor()).writtenAt()).isEqualTo(MICROS);
        assertThat(SnapshotKey.fromCursor(nanos.toCursor())).isEqualTo(nanos);
        assertThat(SnapshotKey.fromCursor(micros.toCursor())).isNotEqualTo(nanos);
    }

    @Test
    public void cursorIsAJsonObjectWithTheThreeFields() {
        final String cursor = new SnapshotKey("connection:x", 3L, MICROS).toCursor();

        assertThat(JsonFactory.newObject(cursor)).isEqualTo(JsonFactory.newObjectBuilder()
                .set("sn", 3L)
                .set("writtenAt", "2026-11-06T10:15:30.123456Z")
                .set("pid", "connection:x")
                .build());
    }

    @Test
    public void aCursorThatIsNoJsonObjectIsRejectedNamingIt() {
        for (final String cursor : new String[]{"not a cursor", "[1,2]", "42", "\"string\"", "{"}) {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .as(cursor)
                    .isThrownBy(() -> SnapshotKey.fromCursor(cursor))
                    .withMessageContaining(cursor)
                    .withMessageContaining("PostgreSQL payload store");
        }
    }

    @Test
    public void aCursorMissingAFieldOrWithTheWrongTypeIsRejected() {
        for (final String cursor : new String[]{
                "{\"sn\":1,\"writtenAt\":\"2026-11-06T10:15:30Z\"}",
                "{\"sn\":1,\"pid\":\"p\"}",
                "{\"writtenAt\":\"2026-11-06T10:15:30Z\",\"pid\":\"p\"}",
                "{\"sn\":\"1\",\"writtenAt\":\"2026-11-06T10:15:30Z\",\"pid\":\"p\"}",
                "{\"sn\":1.5,\"writtenAt\":\"2026-11-06T10:15:30Z\",\"pid\":\"p\"}",
                "{\"sn\":1,\"writtenAt\":\"yesterday\",\"pid\":\"p\"}",
                "{\"sn\":1,\"writtenAt\":\"2026-11-06T10:15:30Z\",\"pid\":7}"}) {
            assertThatExceptionOfType(IllegalArgumentException.class)
                    .as(cursor)
                    .isThrownBy(() -> SnapshotKey.fromCursor(cursor))
                    .withMessageContaining(cursor);
        }
    }

    @Test
    public void nullIsRejected() {
        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> SnapshotKey.fromCursor(null));
        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> new SnapshotKey(null, 1L, MICROS));
        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> new SnapshotKey("p", 1L, null));
    }

}
