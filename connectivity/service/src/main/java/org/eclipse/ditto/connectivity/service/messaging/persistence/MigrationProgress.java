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
package org.eclipse.ditto.connectivity.service.messaging.persistence;

import java.time.Instant;

import javax.annotation.Nullable;

/**
 * Immutable progress tracker for the encryption migration process.
 * <p>
 * Tracks the current migration phase, counters for processed/skipped/failed documents,
 * last processed IDs for resume functionality, and timing information.
 */
public record MigrationProgress(
        String phase,
        @Nullable String lastProcessedSnapshotId,
        @Nullable String lastProcessedSnapshotPid,
        @Nullable String lastProcessedJournalId,
        @Nullable String lastProcessedJournalPid,
        long snapshotsProcessed,
        long snapshotsSkipped,
        long snapshotsFailed,
        long journalProcessed,
        long journalSkipped,
        long journalFailed,
        String startedAt
) {

    /**
     * Creates a new MigrationProgress with default values starting in the snapshots phase.
     */
    public MigrationProgress() {
        this("snapshots", null, null, null, null,
                0L, 0L, 0L, 0L, 0L, 0L,
                Instant.now().toString());
    }

    MigrationProgress withPhase(final String newPhase) {
        return new MigrationProgress(newPhase, lastProcessedSnapshotId, lastProcessedSnapshotPid,
                lastProcessedJournalId, lastProcessedJournalPid,
                snapshotsProcessed, snapshotsSkipped, snapshotsFailed,
                journalProcessed, journalSkipped, journalFailed, startedAt);
    }

    MigrationProgress withLastSnapshotId(final String id) {
        return new MigrationProgress(phase, id, lastProcessedSnapshotPid,
                lastProcessedJournalId, lastProcessedJournalPid,
                snapshotsProcessed, snapshotsSkipped, snapshotsFailed,
                journalProcessed, journalSkipped, journalFailed, startedAt);
    }

    MigrationProgress withLastSnapshotPid(final String pid) {
        return new MigrationProgress(phase, lastProcessedSnapshotId, pid,
                lastProcessedJournalId, lastProcessedJournalPid,
                snapshotsProcessed, snapshotsSkipped, snapshotsFailed,
                journalProcessed, journalSkipped, journalFailed, startedAt);
    }

    MigrationProgress withLastJournalId(final String id) {
        return new MigrationProgress(phase, lastProcessedSnapshotId, lastProcessedSnapshotPid,
                id, lastProcessedJournalPid,
                snapshotsProcessed, snapshotsSkipped, snapshotsFailed,
                journalProcessed, journalSkipped, journalFailed, startedAt);
    }

    MigrationProgress withLastJournalPid(final String pid) {
        return new MigrationProgress(phase, lastProcessedSnapshotId, lastProcessedSnapshotPid,
                lastProcessedJournalId, pid,
                snapshotsProcessed, snapshotsSkipped, snapshotsFailed,
                journalProcessed, journalSkipped, journalFailed, startedAt);
    }

    MigrationProgress incrementSnapshotsProcessed() {
        return new MigrationProgress(phase, lastProcessedSnapshotId, lastProcessedSnapshotPid,
                lastProcessedJournalId, lastProcessedJournalPid,
                snapshotsProcessed + 1, snapshotsSkipped, snapshotsFailed,
                journalProcessed, journalSkipped, journalFailed, startedAt);
    }

    MigrationProgress incrementSnapshotsSkipped() {
        return new MigrationProgress(phase, lastProcessedSnapshotId, lastProcessedSnapshotPid,
                lastProcessedJournalId, lastProcessedJournalPid,
                snapshotsProcessed, snapshotsSkipped + 1, snapshotsFailed,
                journalProcessed, journalSkipped, journalFailed, startedAt);
    }

    MigrationProgress incrementSnapshotsFailed() {
        return new MigrationProgress(phase, lastProcessedSnapshotId, lastProcessedSnapshotPid,
                lastProcessedJournalId, lastProcessedJournalPid,
                snapshotsProcessed, snapshotsSkipped, snapshotsFailed + 1,
                journalProcessed, journalSkipped, journalFailed, startedAt);
    }

    MigrationProgress incrementJournalProcessed() {
        return new MigrationProgress(phase, lastProcessedSnapshotId, lastProcessedSnapshotPid,
                lastProcessedJournalId, lastProcessedJournalPid,
                snapshotsProcessed, snapshotsSkipped, snapshotsFailed,
                journalProcessed + 1, journalSkipped, journalFailed, startedAt);
    }

    MigrationProgress incrementJournalSkipped() {
        return new MigrationProgress(phase, lastProcessedSnapshotId, lastProcessedSnapshotPid,
                lastProcessedJournalId, lastProcessedJournalPid,
                snapshotsProcessed, snapshotsSkipped, snapshotsFailed,
                journalProcessed, journalSkipped + 1, journalFailed, startedAt);
    }

    MigrationProgress incrementJournalFailed() {
        return new MigrationProgress(phase, lastProcessedSnapshotId, lastProcessedSnapshotPid,
                lastProcessedJournalId, lastProcessedJournalPid,
                snapshotsProcessed, snapshotsSkipped, snapshotsFailed,
                journalProcessed, journalSkipped, journalFailed + 1, startedAt);
    }

    /**
     * Adjusts counters when a bulk write fails: moves the "processed" count back to "failed"
     * for the documents that were counted as processed but whose write did not persist.
     *
     * @param failedWriteCount number of documents in the failed bulk write
     * @param isSnapshot true for snapshot counters, false for journal counters
     * @return adjusted progress
     */
    MigrationProgress adjustForBulkWriteFailure(final int failedWriteCount, final boolean isSnapshot) {
        if (isSnapshot) {
            final long adjustedProcessed = Math.max(0, snapshotsProcessed - failedWriteCount);
            return new MigrationProgress(phase, lastProcessedSnapshotId, lastProcessedSnapshotPid,
                    lastProcessedJournalId, lastProcessedJournalPid,
                    adjustedProcessed, snapshotsSkipped, snapshotsFailed + failedWriteCount,
                    journalProcessed, journalSkipped, journalFailed, startedAt);
        } else {
            final long adjustedProcessed = Math.max(0, journalProcessed - failedWriteCount);
            return new MigrationProgress(phase, lastProcessedSnapshotId, lastProcessedSnapshotPid,
                    lastProcessedJournalId, lastProcessedJournalPid,
                    snapshotsProcessed, snapshotsSkipped, snapshotsFailed,
                    adjustedProcessed, journalSkipped, journalFailed + failedWriteCount, startedAt);
        }
    }

    @Override
    public String toString() {
        return "MigrationProgress[" +
                "phase=" + phase +
                ", snapshots(processed=" + snapshotsProcessed +
                ", skipped=" + snapshotsSkipped +
                ", failed=" + snapshotsFailed + ")" +
                ", journal(processed=" + journalProcessed +
                ", skipped=" + journalSkipped +
                ", failed=" + journalFailed + ")" +
                "]";
    }
}
