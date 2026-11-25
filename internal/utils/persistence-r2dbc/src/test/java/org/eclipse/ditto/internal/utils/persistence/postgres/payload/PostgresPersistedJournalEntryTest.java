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

import java.util.List;

import org.eclipse.ditto.internal.utils.persistence.api.payload.PersistedJournalEntry;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit tests for {@link PostgresPersistedJournalEntry}: one event per row, the cursor is the {@code seq}, and
 * {@code withEventPayloads} takes exactly one payload, leaves an equal one untouched and carries a changed one.
 */
public final class PostgresPersistedJournalEntryTest {

    private static final String EVENT = "{\"type\":\"connectivity.events:connectionCreated\","
            + "\"connection\":{\"uri\":\"amqps://user:secret@host:5671\"}}";

    @Test
    public void equalsAndHashCode() {
        EqualsVerifier.forClass(PostgresPersistedJournalEntry.class)
                .withNonnullFields("pid", "storedEventJson")
                .verify();
    }

    @Test
    public void cursorIsTheSeqAndTheEntryHoldsOneEventPayload() {
        final PostgresPersistedJournalEntry underTest = entry();

        assertThat(underTest.cursor()).isEqualTo("17");
        assertThat(underTest.pid()).isEqualTo("connection:c");
        assertThat(underTest.sn()).isEqualTo(2L);
        assertThat(underTest.seq()).isEqualTo(17L);
        assertThat(underTest.eventPayloads()).containsExactly(JsonFactory.newObject(EVENT));
        assertThat(underTest.rewrittenEventJson()).isEmpty();
    }

    @Test
    public void withEventPayloadsRequiresExactlyOnePayload() {
        final PostgresPersistedJournalEntry underTest = entry();
        final JsonObject payload = JsonFactory.newObject(EVENT);

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> underTest.withEventPayloads(List.of()))
                .withMessageContaining("<17>")
                .withMessageContaining("1 event payload")
                .withMessageContaining("0 were given");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> underTest.withEventPayloads(List.of(payload, payload)))
                .withMessageContaining("2 were given");
        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> underTest.withEventPayloads(null));
    }

    @Test
    public void anEqualPayloadLeavesTheRowUntouched() {
        final PostgresPersistedJournalEntry underTest = entry();

        final PostgresPersistedJournalEntry unchanged =
                (PostgresPersistedJournalEntry) underTest.withEventPayloads(underTest.eventPayloads());

        assertThat(unchanged.rewrittenEventJson()).as("no statement is issued for it").isEmpty();
        assertThat(unchanged.eventPayloads()).isEqualTo(underTest.eventPayloads());
        assertThat(unchanged.cursor()).isEqualTo("17");
        assertThat(unchanged).isEqualTo(underTest);
    }

    @Test
    public void aChangedPayloadIsCarriedForTheRewriteLeavingThisEntryUnchanged() {
        final PostgresPersistedJournalEntry underTest = entry();
        final JsonObject rewritten = JsonFactory.newObject(EVENT).setValue("/connection/uri",
                "amqps://user:encrypted_x@host:5671");

        final PersistedJournalEntry result = underTest.withEventPayloads(List.of(rewritten));

        assertThat(result).isInstanceOf(PostgresPersistedJournalEntry.class);
        assertThat(result.cursor()).isEqualTo("17");
        assertThat(result.pid()).isEqualTo("connection:c");
        assertThat(result.eventPayloads()).containsExactly(rewritten);
        assertThat(((PostgresPersistedJournalEntry) result).rewrittenEventJson()).contains(rewritten.toString());
        assertThat(((PostgresPersistedJournalEntry) result).storedEventJson()).isEqualTo(EVENT);
        assertThat(underTest.rewrittenEventJson()).isEmpty();
        assertThat(result).isNotEqualTo(underTest);
    }

    private static PostgresPersistedJournalEntry entry() {
        return PostgresPersistedJournalEntry.of("connection:c", 2L, 17L, EVENT);
    }

    @Test
    public void nullIsRejected() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> PostgresPersistedJournalEntry.of(null, 1L, 1L, EVENT));
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> PostgresPersistedJournalEntry.of("p", 1L, 1L, null));
    }

    @Test
    public void toStringNamesTheRowAndNotThePayload() {
        final PostgresPersistedJournalEntry underTest = entry();

        assertThat(underTest.toString())
                .contains("pid=connection:c", "sn=2", "seq=17", "rewritten=false")
                .doesNotContain("secret");
    }

}
