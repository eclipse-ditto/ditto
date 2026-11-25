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

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;

import org.eclipse.ditto.internal.utils.persistence.api.payload.PersistedPayloadStore;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles persistence of encryption migration progress through the persistence backend's
 * {@link PersistedPayloadStore}.
 * <p>
 * Provides methods to save, load, and delete migration progress, enabling resume functionality after service restarts
 * or migration aborts. The progress is stored as a flat JSON record whose field names are the ones the migration has
 * always stored, so a record saved before the backend became pluggable is read unchanged.
 */
public final class MigrationProgressTracker {

    private static final Logger LOGGER = LoggerFactory.getLogger(MigrationProgressTracker.class);

    static final String PHASE = "phase";
    static final String LAST_PROCESSED_SNAPSHOT_ID = "lastProcessedSnapshotId";
    static final String LAST_PROCESSED_SNAPSHOT_PID = "lastProcessedSnapshotPid";
    static final String LAST_PROCESSED_JOURNAL_ID = "lastProcessedJournalId";
    static final String LAST_PROCESSED_JOURNAL_PID = "lastProcessedJournalPid";
    static final String SNAPSHOTS_PROCESSED = "snapshotsProcessed";
    static final String SNAPSHOTS_SKIPPED = "snapshotsSkipped";
    static final String SNAPSHOTS_FAILED = "snapshotsFailed";
    static final String JOURNAL_PROCESSED = "journalProcessed";
    static final String JOURNAL_SKIPPED = "journalSkipped";
    static final String JOURNAL_FAILED = "journalFailed";
    static final String STARTED_AT = "startedAt";
    static final String UPDATED_AT = "updatedAt";

    private final PersistedPayloadStore store;

    /**
     * Creates a new MigrationProgressTracker.
     *
     * @param store the persistence backend's payload store that keeps the progress record
     */
    public MigrationProgressTracker(final PersistedPayloadStore store) {
        this.store = store;
    }

    /**
     * Saves migration progress.
     * <p>
     * Creates or replaces the progress record. The record includes all counters, last processed IDs, and timestamp
     * information.
     *
     * @param progress the migration progress to save
     * @return a completion stage that completes when the save operation finishes
     */
    public CompletionStage<Void> saveProgress(final MigrationProgress progress) {
        return store.saveProgress(toJson(progress));
    }

    /**
     * Saves migration progress with retry logic for handling transient failures.
     * <p>
     * Used primarily when saving abort progress to ensure it persists even if
     * there are temporary database connection issues.
     *
     * @param progress the migration progress to save
     * @param maxRetries maximum number of retry attempts
     * @return a completion stage that completes when the save operation succeeds or all retries are exhausted
     */
    public CompletionStage<Void> saveProgressWithRetry(final MigrationProgress progress, final int maxRetries) {
        return saveProgress(progress).exceptionallyCompose(err -> {
            if (maxRetries > 0) {
                LOGGER.warn("Retrying progress save (remaining retries={}): {}", maxRetries, err.getMessage());
                return saveProgressWithRetry(progress, maxRetries - 1);
            }
            return CompletableFuture.failedFuture(err);
        });
    }

    /**
     * Loads migration progress.
     * <p>
     * Used when resuming a migration after a service restart or to check the status
     * of a previous migration.
     *
     * @return a completion stage with an optional containing the progress if it exists
     */
    public CompletionStage<Optional<MigrationProgress>> loadProgress() {
        return store.loadProgress().thenApply(optJson -> optJson.map(MigrationProgressTracker::fromJson));
    }

    /**
     * Deletes migration progress.
     * <p>
     * Used when starting a fresh migration (not resuming) to clear any previously
     * saved progress.
     *
     * @return a completion stage that completes when the delete operation finishes
     */
    public CompletionStage<Void> deleteProgress() {
        return store.deleteProgress();
    }

    /**
     * Renders the progress as the flat JSON record that is stored: the phase, the last processed ids and pids (JSON
     * null when there is none yet), the six counters, {@code startedAt} and {@code updatedAt} (now).
     *
     * @param progress the progress to render
     * @return the record
     */
    static JsonObject toJson(final MigrationProgress progress) {
        return JsonFactory.newObjectBuilder()
                .set(PHASE, progress.phase().getValue())
                .set(LAST_PROCESSED_SNAPSHOT_ID, progress.lastProcessedSnapshotId())
                .set(LAST_PROCESSED_SNAPSHOT_PID, progress.lastProcessedSnapshotPid())
                .set(LAST_PROCESSED_JOURNAL_ID, progress.lastProcessedJournalId())
                .set(LAST_PROCESSED_JOURNAL_PID, progress.lastProcessedJournalPid())
                .set(SNAPSHOTS_PROCESSED, progress.snapshotsProcessed())
                .set(SNAPSHOTS_SKIPPED, progress.snapshotsSkipped())
                .set(SNAPSHOTS_FAILED, progress.snapshotsFailed())
                .set(JOURNAL_PROCESSED, progress.journalProcessed())
                .set(JOURNAL_SKIPPED, progress.journalSkipped())
                .set(JOURNAL_FAILED, progress.journalFailed())
                .set(STARTED_AT, progress.startedAt())
                .set(UPDATED_AT, Instant.now().toString())
                .build();
    }

    /**
     * Rebuilds the progress from a stored record. A missing or non-string phase counts as completed, missing or
     * non-string ids and pids as {@code null}, missing or non-numeric counters as 0.
     *
     * @param json the stored record
     * @return the progress
     */
    static MigrationProgress fromJson(final JsonObject json) {
        final MigrationPhase phase = MigrationPhase.fromValue(getString(json, PHASE))
                .orElse(MigrationPhase.COMPLETED);
        return new MigrationProgress(
                phase,
                getString(json, LAST_PROCESSED_SNAPSHOT_ID),
                getString(json, LAST_PROCESSED_SNAPSHOT_PID),
                getString(json, LAST_PROCESSED_JOURNAL_ID),
                getString(json, LAST_PROCESSED_JOURNAL_PID),
                getLong(json, SNAPSHOTS_PROCESSED),
                getLong(json, SNAPSHOTS_SKIPPED),
                getLong(json, SNAPSHOTS_FAILED),
                getLong(json, JOURNAL_PROCESSED),
                getLong(json, JOURNAL_SKIPPED),
                getLong(json, JOURNAL_FAILED),
                getString(json, STARTED_AT)
        );
    }

    @Nullable
    private static String getString(final JsonObject json, final String key) {
        return json.getValue(key).filter(JsonValue::isString).map(JsonValue::asString).orElse(null);
    }

    private static long getLong(final JsonObject json, final String key) {
        return json.getValue(key).filter(JsonValue::isNumber).map(JsonValue::asLong).orElse(0L);
    }
}
