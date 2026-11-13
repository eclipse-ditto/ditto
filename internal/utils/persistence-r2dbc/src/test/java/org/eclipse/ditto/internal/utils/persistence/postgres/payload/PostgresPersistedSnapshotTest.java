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

import org.eclipse.ditto.internal.utils.persistence.api.payload.PersistedSnapshot;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonParseException;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit tests for {@link PostgresPersistedSnapshot}: the cursor is the row's primary key, the payload is the parsed
 * JSON text, and {@code withPayload} keeps the key, takes the new text through the JSON text codec and rejects U+0000.
 */
public final class PostgresPersistedSnapshotTest {

    private static final Instant WRITTEN_AT = Instant.parse("2026-11-06T10:15:30.123456Z");
    private static final String JSON = "{\"uri\":\"amqps://user:secret@host:5671\",\"name\":\"c\"}";

    @Test
    public void equalsAndHashCode() {
        EqualsVerifier.forClass(PostgresPersistedSnapshot.class)
                .withNonnullFields("key", "snapshotJson")
                .verify();
    }

    @Test
    public void cursorIsThePrimaryKeyAndPidIsTheRowsPid() {
        final PostgresPersistedSnapshot underTest =
                PostgresPersistedSnapshot.of("connection:c", 4L, WRITTEN_AT, JSON);

        assertThat(SnapshotKey.fromCursor(underTest.cursor()))
                .isEqualTo(new SnapshotKey("connection:c", 4L, WRITTEN_AT));
        assertThat(underTest.pid()).isEqualTo("connection:c");
        assertThat(underTest.sn()).isEqualTo(4L);
        assertThat(underTest.writtenAt()).isEqualTo(WRITTEN_AT);
    }

    @Test
    public void payloadIsTheParsedJsonText() {
        final PostgresPersistedSnapshot underTest = PostgresPersistedSnapshot.of("connection:c", 1L, WRITTEN_AT, JSON);

        assertThat(underTest.payload()).isEqualTo(JsonFactory.newObject(JSON));
    }

    @Test
    public void payloadThatIsNoJsonObjectFailsAsTheContractAllows() {
        final PostgresPersistedSnapshot underTest =
                PostgresPersistedSnapshot.of("connection:c", 1L, WRITTEN_AT, "not json");

        assertThatExceptionOfType(JsonParseException.class).isThrownBy(underTest::payload);
    }

    @Test
    public void withPayloadKeepsTheKeyAndCarriesTheNewTextLeavingThisEntryUnchanged() {
        final PostgresPersistedSnapshot underTest = PostgresPersistedSnapshot.of("connection:c", 1L, WRITTEN_AT, JSON);
        final JsonObject rewritten = JsonFactory.newObjectBuilder().set("uri", "encrypted_x").set("name", "c").build();

        final PersistedSnapshot result = underTest.withPayload(rewritten);

        assertThat(result).isInstanceOf(PostgresPersistedSnapshot.class);
        assertThat(result.cursor()).isEqualTo(underTest.cursor());
        assertThat(result.pid()).isEqualTo("connection:c");
        assertThat(result.payload()).isEqualTo(rewritten);
        assertThat(((PostgresPersistedSnapshot) result).snapshotJson()).isEqualTo(rewritten.toString());
        assertThat(underTest.snapshotJson()).isEqualTo(JSON);
    }

    @Test
    public void withPayloadCarriesAnEscapedNulForTheDatabaseToReject() {
        // Ditto's JSON renderer escapes a NUL in a string value as the six-character escape, so the codec's raw-NUL
        // guard never sees one from a JsonObject; PostgreSQL rejects the escaped form when the row is written
        // (SQLSTATE 22P05), which fails the batch as a whole — see PostgresPersistedPayloadStoreIT
        final PostgresPersistedSnapshot underTest = PostgresPersistedSnapshot.of("connection:c", 1L, WRITTEN_AT, JSON);
        final JsonObject withNul = JsonFactory.newObjectBuilder().set("uri", "a\u0000b").build();

        final PostgresPersistedSnapshot result = (PostgresPersistedSnapshot) underTest.withPayload(withNul);

        assertThat(result.snapshotJson()).contains("\\u0000").doesNotContain("\u0000");
        assertThat(result.payload()).isEqualTo(withNul);
    }

    @Test
    public void nullIsRejected() {
        final PostgresPersistedSnapshot underTest = PostgresPersistedSnapshot.of("connection:c", 1L, WRITTEN_AT, JSON);

        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> underTest.withPayload(null));
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> PostgresPersistedSnapshot.of(null, 1L, WRITTEN_AT, JSON));
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> PostgresPersistedSnapshot.of("p", 1L, null, JSON));
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> PostgresPersistedSnapshot.of("p", 1L, WRITTEN_AT, null));
    }

    @Test
    public void toStringNamesTheKeyAndNotThePayload() {
        final PostgresPersistedSnapshot underTest = PostgresPersistedSnapshot.of("connection:c", 1L, WRITTEN_AT, JSON);

        assertThat(underTest.toString())
                .contains("pid=connection:c", "sn=1", "writtenAt=2026-11-06T10:15:30.123456Z")
                .doesNotContain("secret");
    }

}
