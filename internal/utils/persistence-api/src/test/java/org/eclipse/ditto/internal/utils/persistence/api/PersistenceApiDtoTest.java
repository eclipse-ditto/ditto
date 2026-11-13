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
package org.eclipse.ditto.internal.utils.persistence.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.OptionalLong;

import org.eclipse.ditto.json.JsonObject;
import org.junit.Test;

/**
 * Unit tests for the backend-neutral DTOs {@link JournalEntry}, {@link SnapshotEntry} and {@link DeleteOutcome}.
 */
public final class PersistenceApiDtoTest {

    @Test
    public void journalEntryExposesTypedFields() {
        final JsonObject json = JsonObject.of("{\"pid\":\"thing:p:id\",\"manifest\":\"created\"}");
        final JournalEntry entry = JournalEntry.of("thing:p:id", "created", json);

        assertThat(entry.getPid()).contains("thing:p:id");
        assertThat(entry.getManifest()).contains("created");
        assertThat(entry.getJson()).isEqualTo(json);
    }

    @Test
    public void journalEntryHandlesAbsentFields() {
        final JournalEntry entry = JournalEntry.of(null, null, JsonObject.empty());

        assertThat(entry.getPid()).isEmpty();
        assertThat(entry.getManifest()).isEmpty();
    }

    @Test
    public void journalEntryEqualityIsValueBased() {
        final JsonObject json = JsonObject.of("{\"a\":1}");
        assertThat(JournalEntry.of("p", "m", json)).isEqualTo(JournalEntry.of("p", "m", json));
        assertThat(JournalEntry.of("p", "m", json)).hasSameHashCodeAs(JournalEntry.of("p", "m", json));
        assertThat(JournalEntry.of("p", "m", json)).isNotEqualTo(JournalEntry.of("q", "m", json));
    }

    @Test
    public void snapshotEntryExposesTypedFields() {
        final JsonObject json = JsonObject.of("{\"_modified\":\"2020-01-01\"}");
        final SnapshotEntry entry = SnapshotEntry.of("thing:p:id", 42L, "DELETED", json);

        assertThat(entry.getPid()).contains("thing:p:id");
        assertThat(entry.getSequenceNumber()).isEqualTo(OptionalLong.of(42L));
        assertThat(entry.getLifecycle()).contains("DELETED");
        assertThat(entry.isDeleted()).isTrue();
        assertThat(entry.getJson()).isEqualTo(json);
    }

    @Test
    public void snapshotEntryHandlesAbsentFields() {
        final SnapshotEntry entry = SnapshotEntry.of(null, null, null, JsonObject.empty());

        assertThat(entry.getPid()).isEmpty();
        assertThat(entry.getSequenceNumber()).isEqualTo(OptionalLong.empty());
        assertThat(entry.getLifecycle()).isEmpty();
        assertThat(entry.isDeleted()).isFalse();
    }

    @Test
    public void snapshotEntryNonDeletedLifecycle() {
        final SnapshotEntry entry = SnapshotEntry.of("p", 1L, "ACTIVE", JsonObject.empty());

        assertThat(entry.getLifecycle()).contains("ACTIVE");
        assertThat(entry.isDeleted()).isFalse();
    }

    @Test
    public void snapshotEntryEqualityIsValueBased() {
        final JsonObject json = JsonObject.of("{\"a\":1}");
        assertThat(SnapshotEntry.of("p", 1L, "ACTIVE", json))
                .isEqualTo(SnapshotEntry.of("p", 1L, "ACTIVE", json));
        assertThat(SnapshotEntry.of("p", 1L, "ACTIVE", json))
                .isNotEqualTo(SnapshotEntry.of("p", 2L, "ACTIVE", json));
    }

    @Test
    public void deleteOutcomeAcknowledged() {
        final DeleteOutcome outcome = DeleteOutcome.acknowledged(7L);

        assertThat(outcome.isAcknowledged()).isTrue();
        assertThat(outcome.getDeletedCount()).isEqualTo(7L);
    }

    @Test
    public void deleteOutcomeUnacknowledged() {
        final DeleteOutcome outcome = DeleteOutcome.of(false, 0L);

        assertThat(outcome.isAcknowledged()).isFalse();
        assertThat(outcome.getDeletedCount()).isEqualTo(0L);
    }

    @Test
    public void deleteOutcomeEqualityIsValueBased() {
        assertThat(DeleteOutcome.acknowledged(3L)).isEqualTo(DeleteOutcome.of(true, 3L));
        assertThat(DeleteOutcome.acknowledged(3L)).hasSameHashCodeAs(DeleteOutcome.of(true, 3L));
        assertThat(DeleteOutcome.acknowledged(3L)).isNotEqualTo(DeleteOutcome.acknowledged(4L));
    }

    @Test
    public void constantsArePresent() {
        assertThat(DittoReadJournal.PRIORITY_TAG_PREFIX).isEqualTo("priority-");
        assertThat(DittoReadJournal.JOURNAL_TAG_ALWAYS_ALIVE).isEqualTo("always-alive");
        assertThat(DittoReadJournal.LIFECYCLE).isEqualTo("__lifecycle");
        assertThat(DittoReadJournal.S_ID).isEqualTo("_id");
        assertThat(DittoReadJournal.S_SN).isEqualTo("sn");
        assertThat(Optional.of(DittoReadJournal.S_SN)).isPresent();
    }
}
