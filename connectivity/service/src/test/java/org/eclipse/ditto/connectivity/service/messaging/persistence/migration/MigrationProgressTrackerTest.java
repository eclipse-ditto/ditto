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
package org.eclipse.ditto.connectivity.service.messaging.persistence.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.junit.Test;

/**
 * Tests for {@link MigrationProgressTracker}: the progress is rendered to, and rebuilt from, a flat JSON record with
 * exactly the field names (and order) the migration has always stored, and the three store operations plus the retry
 * are delegated to the {@code PersistedPayloadStore}.
 */
public final class MigrationProgressTrackerTest {

    /** The stored field names, in the order the migration has always written them (after the backend's own id). */
    private static final List<String> FIELD_NAMES = List.of(
            "phase",
            "lastProcessedSnapshotId", "lastProcessedSnapshotPid",
            "lastProcessedJournalId", "lastProcessedJournalPid",
            "snapshotsProcessed", "snapshotsSkipped", "snapshotsFailed",
            "journalProcessed", "journalSkipped", "journalFailed",
            "startedAt", "updatedAt");

    @Test
    public void toJsonHasExactlyTheStoredFieldNamesInOrder() {
        final JsonObject json = MigrationProgressTracker.toJson(sampleProgress());

        assertThat(json.stream().map(JsonField::getKeyName).toList()).isEqualTo(FIELD_NAMES);
    }

    @Test
    public void toJsonRendersStringsCountersAndNulls() {
        final Instant before = Instant.now();

        final JsonObject json = MigrationProgressTracker.toJson(sampleProgress());

        assertThat(json.getValue("phase").map(JsonValue::asString)).contains("snapshots");
        assertThat(json.getValue("lastProcessedSnapshotId").map(JsonValue::asString))
                .contains("507f1f77bcf86cd799439011");
        assertThat(json.getValue("lastProcessedSnapshotPid").map(JsonValue::asString))
                .contains("connection:mqtt-prod-sensor-01");
        // ids not processed yet are JSON null, not absent
        assertThat(json.getValue("lastProcessedJournalId").map(JsonValue::isNull)).contains(true);
        assertThat(json.getValue("lastProcessedJournalPid").map(JsonValue::isNull)).contains(true);
        assertThat(json.getValue("snapshotsProcessed").map(JsonValue::asLong)).contains(150L);
        assertThat(json.getValue("snapshotsSkipped").map(JsonValue::asLong)).contains(10L);
        assertThat(json.getValue("snapshotsFailed").map(JsonValue::asLong)).contains(2L);
        assertThat(json.getValue("journalProcessed").map(JsonValue::asLong)).contains(0L);
        assertThat(json.getValue("startedAt").map(JsonValue::asString)).contains("2026-02-16T10:00:00Z");
        // updatedAt is the time of rendering
        final Instant updatedAt = Instant.parse(json.getValue("updatedAt").map(JsonValue::asString).orElseThrow());
        assertThat(updatedAt).isBetween(before, Instant.now());
    }

    @Test
    public void fromJsonRestoresWhatToJsonRendered() {
        final MigrationProgress progress = sampleProgress().withPhase(MigrationPhase.JOURNAL)
                .withLastJournal("507f1f77bcf86cd799439012", "connection:mqtt-prod-sensor-02")
                .incrementJournalProcessed()
                .incrementJournalSkipped()
                .incrementJournalFailed();

        final MigrationProgress restored = MigrationProgressTracker.fromJson(MigrationProgressTracker.toJson(progress));

        assertThat(restored).isEqualTo(progress);
    }

    @Test
    public void fromJsonDefaultsMissingFieldsAsBefore() {
        final MigrationProgress restored = MigrationProgressTracker.fromJson(JsonObject.empty());

        // a missing phase was always read as completed, missing ids as null and missing counters as 0
        assertThat(restored.phase()).isEqualTo(MigrationPhase.COMPLETED);
        assertThat(restored.lastProcessedSnapshotId()).isNull();
        assertThat(restored.lastProcessedSnapshotPid()).isNull();
        assertThat(restored.lastProcessedJournalId()).isNull();
        assertThat(restored.lastProcessedJournalPid()).isNull();
        assertThat(restored.snapshotsProcessed()).isZero();
        assertThat(restored.snapshotsSkipped()).isZero();
        assertThat(restored.snapshotsFailed()).isZero();
        assertThat(restored.journalProcessed()).isZero();
        assertThat(restored.journalSkipped()).isZero();
        assertThat(restored.journalFailed()).isZero();
        assertThat(restored.startedAt()).isNull();
    }

    @Test
    public void fromJsonReadsAnAbortedOrInProgressPhase() {
        assertThat(MigrationProgressTracker.fromJson(JsonFactory.newObjectBuilder().set("phase", "aborted:journal")
                .build()).phase()).isEqualTo(MigrationPhase.JOURNAL);
        assertThat(MigrationProgressTracker.fromJson(JsonFactory.newObjectBuilder()
                .set("phase", "in_progress:snapshots").build()).phase()).isEqualTo(MigrationPhase.SNAPSHOTS);
        assertThat(MigrationProgressTracker.fromJson(JsonFactory.newObjectBuilder().set("phase", "unknown")
                .build()).phase()).isEqualTo(MigrationPhase.COMPLETED);
    }

    @Test
    public void fromJsonIgnoresValuesOfTheWrongType() {
        final JsonObject odd = JsonFactory.newObjectBuilder()
                .set("phase", 7)
                .set("lastProcessedSnapshotId", true)
                .set("snapshotsProcessed", "many")
                .set("journalProcessed", 3)
                .build();

        final MigrationProgress restored = MigrationProgressTracker.fromJson(odd);

        assertThat(restored.phase()).isEqualTo(MigrationPhase.COMPLETED);
        assertThat(restored.lastProcessedSnapshotId()).isNull();
        assertThat(restored.snapshotsProcessed()).isZero();
        assertThat(restored.journalProcessed()).isEqualTo(3L);
    }

    @Test
    public void saveLoadAndDeleteGoThroughTheStore() throws Exception {
        final InMemoryPersistedPayloadStore store = new InMemoryPersistedPayloadStore();
        final MigrationProgressTracker underTest = new MigrationProgressTracker(store);
        final MigrationProgress progress = sampleProgress();

        assertThat(underTest.loadProgress().toCompletableFuture().get(5, TimeUnit.SECONDS)).isEmpty();

        underTest.saveProgress(progress).toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertThat(store.savedProgressRecords).hasSize(1);
        assertThat(store.savedProgress().map(j -> j.stream().map(JsonField::getKeyName).toList()))
                .contains(FIELD_NAMES);

        final Optional<MigrationProgress> loaded =
                underTest.loadProgress().toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertThat(loaded).contains(progress);

        underTest.deleteProgress().toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertThat(store.deleteProgressCalls.get()).isEqualTo(1);
        assertThat(underTest.loadProgress().toCompletableFuture().get(5, TimeUnit.SECONDS)).isEmpty();
    }

    @Test
    public void saveProgressWithRetryRetriesAndEventuallyGivesUp() {
        final InMemoryPersistedPayloadStore store = new InMemoryPersistedPayloadStore().failingProgressOperations();
        final MigrationProgressTracker underTest = new MigrationProgressTracker(store);

        assertThatThrownBy(() -> underTest.saveProgressWithRetry(sampleProgress(), 2)
                .toCompletableFuture().get(5, TimeUnit.SECONDS))
                .hasCauseInstanceOf(IllegalStateException.class)
                .hasMessageContaining("progress unavailable");
        // the first attempt plus two retries
        assertThat(store.saveProgressAttempts.get()).isEqualTo(3);
    }

    @Test
    public void saveProgressWithRetrySucceedsWithoutARetryWhenTheStoreWorks() throws Exception {
        final InMemoryPersistedPayloadStore store = new InMemoryPersistedPayloadStore();
        final MigrationProgressTracker underTest = new MigrationProgressTracker(store);

        underTest.saveProgressWithRetry(sampleProgress(), 2).toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertThat(store.saveProgressAttempts.get()).isEqualTo(1);
        assertThat(store.savedProgressRecords).hasSize(1);
    }

    @Test
    public void loadProgressFailsWhenTheStoreFails() {
        final MigrationProgressTracker underTest =
                new MigrationProgressTracker(new InMemoryPersistedPayloadStore().failingProgressOperations());

        assertThatThrownBy(() -> underTest.loadProgress().toCompletableFuture().join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    private static MigrationProgress sampleProgress() {
        return new MigrationProgress(
                MigrationPhase.SNAPSHOTS,
                "507f1f77bcf86cd799439011", "connection:mqtt-prod-sensor-01",
                null, null,
                150, 10, 2,
                0, 0, 0,
                "2026-02-16T10:00:00Z");
    }
}
