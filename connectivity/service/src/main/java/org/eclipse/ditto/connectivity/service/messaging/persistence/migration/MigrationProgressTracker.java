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

import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.reactivestreams.client.MongoCollection;

/**
 * Handles persistence of encryption migration progress to MongoDB.
 * <p>
 * Provides methods to save, load, and delete migration progress, enabling resume
 * functionality after service restarts or migration aborts.
 */
public final class MigrationProgressTracker {

    private static final Logger LOGGER = LoggerFactory.getLogger(MigrationProgressTracker.class);

    private static final String PROGRESS_ID = "current";
    private static final String ID_FIELD = "_id";

    private final MongoCollection<Document> progressCollection;
    private final Materializer materializer;

    /**
     * Creates a new MigrationProgressTracker.
     *
     * @param progressCollection the MongoDB collection for storing progress
     * @param materializer the Pekko Streams materializer
     */
    public MigrationProgressTracker(final MongoCollection<Document> progressCollection,
            final Materializer materializer) {
        this.progressCollection = progressCollection;
        this.materializer = materializer;
    }

    /**
     * Saves migration progress to MongoDB.
     * <p>
     * Uses upsert to create or update the progress document. The progress document
     * includes all counters, last processed IDs, and timestamp information.
     *
     * @param progress the migration progress to save
     * @return a completion stage that completes when the save operation finishes
     */
    public CompletionStage<Void> saveProgress(final MigrationProgress progress) {
        final Document progressDoc = new Document()
                .append(ID_FIELD, PROGRESS_ID)
                .append("phase", progress.phase().getValue())
                .append("lastProcessedSnapshotId", progress.lastProcessedSnapshotId())
                .append("lastProcessedSnapshotPid", progress.lastProcessedSnapshotPid())
                .append("lastProcessedJournalId", progress.lastProcessedJournalId())
                .append("lastProcessedJournalPid", progress.lastProcessedJournalPid())
                .append("snapshotsProcessed", progress.snapshotsProcessed())
                .append("snapshotsSkipped", progress.snapshotsSkipped())
                .append("snapshotsFailed", progress.snapshotsFailed())
                .append("journalProcessed", progress.journalProcessed())
                .append("journalSkipped", progress.journalSkipped())
                .append("journalFailed", progress.journalFailed())
                .append("startedAt", progress.startedAt())
                .append("updatedAt", Instant.now().toString());

        return Source.fromPublisher(
                progressCollection.replaceOne(
                        Filters.eq(ID_FIELD, PROGRESS_ID),
                        progressDoc,
                        new ReplaceOptions().upsert(true)))
                .runWith(Sink.head(), materializer)
                .thenApply(result -> {
                    LOGGER.info("Progress saved: modified={}, upserted={}",
                            result.getModifiedCount(), result.getUpsertedId() != null ? 1 : 0);
                    return (Void) null;
                });
    }

    /**
     * Saves migration progress with retry logic for handling transient failures.
     * <p>
     * Used primarily when saving abort progress to ensure it persists even if
     * there are temporary MongoDB connection issues.
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
     * Loads migration progress from MongoDB.
     * <p>
     * Used when resuming a migration after a service restart or to check the status
     * of a previous migration.
     *
     * @return a completion stage with an optional containing the progress if it exists
     */
    public CompletionStage<Optional<MigrationProgress>> loadProgress() {
        return Source.fromPublisher(
                progressCollection.find(Filters.eq(ID_FIELD, PROGRESS_ID)).first())
                .runWith(Sink.headOption(), materializer)
                .thenApply(optDoc -> optDoc.map(doc -> {
                        final String phaseStr = doc.getString("phase");
                        final MigrationPhase phase = MigrationPhase.fromValue(phaseStr)
                                .orElse(MigrationPhase.COMPLETED);
                        return new MigrationProgress(
                            phase,
                            doc.getString("lastProcessedSnapshotId"),
                            doc.getString("lastProcessedSnapshotPid"),
                            doc.getString("lastProcessedJournalId"),
                            doc.getString("lastProcessedJournalPid"),
                            doc.getLong("snapshotsProcessed") != null
                                    ? doc.getLong("snapshotsProcessed") : 0L,
                            doc.getLong("snapshotsSkipped") != null
                                    ? doc.getLong("snapshotsSkipped") : 0L,
                            doc.getLong("snapshotsFailed") != null
                                    ? doc.getLong("snapshotsFailed") : 0L,
                            doc.getLong("journalProcessed") != null
                                    ? doc.getLong("journalProcessed") : 0L,
                            doc.getLong("journalSkipped") != null
                                    ? doc.getLong("journalSkipped") : 0L,
                            doc.getLong("journalFailed") != null
                                    ? doc.getLong("journalFailed") : 0L,
                            doc.getString("startedAt")
                        );
                }));
    }

    /**
     * Deletes migration progress from MongoDB.
     * <p>
     * Used when starting a fresh migration (not resuming) to clear any previously
     * saved progress.
     *
     * @return a completion stage that completes when the delete operation finishes
     */
    public CompletionStage<Void> deleteProgress() {
        return Source.fromPublisher(
                progressCollection.deleteOne(Filters.eq(ID_FIELD, PROGRESS_ID)))
                .runWith(Sink.ignore(), materializer)
                .thenApply(done -> null);
    }
}
