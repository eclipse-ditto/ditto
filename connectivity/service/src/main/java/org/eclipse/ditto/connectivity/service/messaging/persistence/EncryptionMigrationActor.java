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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;

import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.Props;
import org.apache.pekko.actor.Status;
import org.apache.pekko.japi.pf.ReceiveBuilder;
import org.apache.pekko.stream.KillSwitches;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.SharedKillSwitch;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.bson.Document;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.connectivity.service.config.FieldsEncryptionConfig;
import org.eclipse.ditto.connectivity.service.messaging.persistence.migration.DocumentProcessingResult;
import org.eclipse.ditto.connectivity.service.messaging.persistence.migration.DocumentProcessor;
import org.eclipse.ditto.connectivity.service.messaging.persistence.migration.MigrationContext;
import org.eclipse.ditto.connectivity.service.messaging.persistence.migration.MigrationProgressTracker;
import org.eclipse.ditto.connectivity.service.messaging.persistence.migration.MigrationStreamFactory;
import org.eclipse.ditto.internal.utils.pekko.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.internal.utils.pekko.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.persistence.mongo.MongoClientWrapper;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.MongoDbConfig;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.WriteModel;
import com.mongodb.reactivestreams.client.MongoCollection;

/**
 * Actor that orchestrates encryption key migration for persisted connection data.
 * <p>
 * This actor handles message routing and stream execution for encrypting, re-encrypting,
 * or decrypting connection data stored in MongoDB. It supports three migration modes:
 * <ul>
 *   <li><b>Initial encryption</b>: Encrypt plaintext data with a new key</li>
 *   <li><b>Key rotation</b>: Re-encrypt data from old key to new key</li>
 *   <li><b>Disable encryption</b>: Decrypt data back to plaintext</li>
 * </ul>
 * <p>
 * The actor delegates to helper classes for focused responsibilities:
 * <ul>
 *   <li>{@link DocumentProcessor} - Document transformation and re-encryption logic</li>
 *   <li>{@link MigrationStreamFactory} - Stream construction with filters and throttling</li>
 *   <li>{@link MigrationProgressTracker} - Progress persistence for resume support</li>
 * </ul>
 * <p>
 * <b>Migration Flow:</b>
 * <ol>
 *   <li>Receives {@link MigrateConnectionEncryption} command</li>
 *   <li>Validates encryption configuration</li>
 *   <li>Processes snapshot collection (connection_snaps)</li>
 *   <li>Processes journal collection (connection_journal)</li>
 *   <li>Saves progress after each batch for resume capability</li>
 *   <li>Returns {@link MigrateConnectionEncryptionResponse} immediately (202 Accepted)</li>
 * </ol>
 * <p>
 * <b>Features:</b>
 * <ul>
 *   <li>Dry-run mode for validation without modifying data</li>
 *   <li>Resume support after abort or service restart</li>
 *   <li>Throttling to limit database load</li>
 *   <li>Abort capability via {@link MigrateConnectionEncryptionAbort}</li>
 *   <li>Status queries via {@link MigrateConnectionEncryptionStatus}</li>
 * </ul>
 *
 * @see DocumentProcessor
 * @see MigrationStreamFactory
 * @see MigrationProgressTracker
 * @see MigrationProgress
 */
public final class EncryptionMigrationActor extends AbstractActor {

    /**
     * The name of this actor.
     */
    public static final String ACTOR_NAME = "encryptionMigration";


    private static final String SNAPSHOT_COLLECTION = "connection_snaps";
    private static final String JOURNAL_COLLECTION = "connection_journal";
    private static final String PROGRESS_COLLECTION = "connection_encryption_migration";

    private static final String PHASE_SNAPSHOTS = "snapshots";
    private static final String PHASE_JOURNAL = "journal";
    private static final String PHASE_COMPLETED = "completed";
    private static final String PHASE_ABORTED_PREFIX = "aborted:";

    // MongoDB field name for document ID
    private static final String ID_FIELD = "_id";

    private final DittoDiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);

    private final FieldsEncryptionConfig encryptionConfig;
    private final Materializer materializer;
    private final MongoCollection<Document> snapshotCollection;
    private final MongoCollection<Document> journalCollection;
    private final MigrationProgressTracker progressTracker;
    private final int batchSize;
    private final int maxDocumentsPerMinute;

    private boolean migrationInProgress = false;
    private boolean currentDryRun = false;
    private volatile boolean abortRequested = false;
    private SharedKillSwitch activeKillSwitch;
    @Nullable
    private MigrationProgress currentProgress;

    private EncryptionMigrationActor(final ConnectivityConfig connectivityConfig, final MongoClientWrapper mongoClient) {
        this.encryptionConfig = connectivityConfig.getConnectionConfig().getFieldsEncryptionConfig();
        this.materializer = Materializer.createMaterializer(this::getContext);
        this.batchSize = encryptionConfig.getMigrationBatchSize();
        this.maxDocumentsPerMinute = encryptionConfig.getMigrationMaxDocumentsPerMinute();

        final var db = mongoClient.getDefaultDatabase();
        this.snapshotCollection = db.getCollection(SNAPSHOT_COLLECTION);
        this.journalCollection = db.getCollection(JOURNAL_COLLECTION);
        final MongoCollection<Document> progressCollection = db.getCollection(PROGRESS_COLLECTION);
        this.progressTracker = new MigrationProgressTracker(progressCollection, materializer);
    }

    /**
     * Creates Props for this actor.
     *
     * @param connectivityConfig the connectivity configuration.
     * @return the Props.
     */
    public static Props props(final ConnectivityConfig connectivityConfig, final MongoClientWrapper mongoClient) {
        return Props.create(EncryptionMigrationActor.class, connectivityConfig, mongoClient);
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(MigrateConnectionEncryption.class, this::handleMigration)
                .match(MigrateConnectionEncryptionAbort.class, this::handleAbort)
                .match(MigrateConnectionEncryptionStatus.class, this::handleStatus)
                .match(ProgressUpdate.class, this::handleProgressUpdate)
                .match(MigrationCompleted.class, this::handleMigrationCompleted)
                .build();
    }

    private void handleProgressUpdate(final ProgressUpdate update) {
        this.currentProgress = update.progress;
    }

    private void handleMigrationCompleted(final MigrationCompleted completed) {
        migrationInProgress = false;
        activeKillSwitch = null;
        final boolean wasAborted = abortRequested;
        abortRequested = false;
        final MigrationProgress progress = completed.progress;
        final boolean dryRun = completed.dryRun;

        if (completed.error != null && !wasAborted) {
            log.error("Encryption migration failed", completed.error);
        } else {
            final String finalPhase = wasAborted
                    ? "aborted:" + (progress != null ? progress.phase() : "unknown")
                    : (progress != null ? progress.phase() : "unknown");
            log.info("Encryption migration {} (dryRun={}): snapshots(p={}/s={}/f={}), " +
                            "journal(p={}/s={}/f={}), finalPhase={}",
                    wasAborted ? "aborted" : "completed", dryRun,
                    progress != null ? progress.snapshotsProcessed() : 0,
                    progress != null ? progress.snapshotsSkipped() : 0,
                    progress != null ? progress.snapshotsFailed() : 0,
                    progress != null ? progress.journalProcessed() : 0,
                    progress != null ? progress.journalSkipped() : 0,
                    progress != null ? progress.journalFailed() : 0,
                    finalPhase);
            if (progress != null) {
                currentProgress = progress.withPhase(finalPhase);
                if (wasAborted && !dryRun) {
                    progressTracker.saveProgressWithRetry(progress.withPhase(finalPhase), 2)
                            .whenComplete((v, saveErr) -> {
                                if (saveErr != null) {
                                    log.error("Failed to save abort progress after retries: {}",
                                            saveErr.getMessage());
                                } else {
                                    log.info("Abort progress saved successfully: phase={}",
                                            finalPhase);
                                }
                            });
                }
            }
        }
    }

    private void handleStatus(final MigrateConnectionEncryptionStatus command) {
        final ActorRef sender = getSender();
        final MigrationProgress inMemory = currentProgress;
        if (inMemory != null) {
            // Use in-memory progress (available during and after migration, including dry-run)
            final String phase = migrationInProgress
                    ? "in_progress:" + inMemory.phase()
                    : inMemory.phase();
            sender.tell(MigrateConnectionEncryptionStatusResponse.of(
                    phase, inMemory, currentDryRun, migrationInProgress,
                    command.getDittoHeaders()), getSelf());
        } else {
            // Fall back to MongoDB (e.g. after service restart)
            final ActorRef self = getSelf();
            progressTracker.loadProgress().thenApply(optProgress -> {
                final MigrationProgress progress = optProgress.orElseGet(MigrationProgress::new);
                return MigrateConnectionEncryptionStatusResponse.of(
                        progress.phase(), progress, false, false,
                        command.getDittoHeaders());
            }).whenComplete((response, error) -> {
                if (error != null) {
                    sender.tell(new Status.Failure(error), self);
                } else {
                    sender.tell(response, self);
                }
            });
        }
    }

    private void handleAbort(final MigrateConnectionEncryptionAbort command) {
        final ActorRef sender = getSender();

        if (!migrationInProgress) {
            sender.tell(new Status.Failure(new IllegalStateException(
                    "No migration is currently running.")), getSelf());
            return;
        }

        log.info("Abort requested for running migration");
        abortRequested = true;
        if (activeKillSwitch != null) {
            activeKillSwitch.shutdown();
        }

        final MigrationProgress progress = currentProgress != null ? currentProgress : new MigrationProgress();
        sender.tell(MigrateConnectionEncryptionAbortResponse.of(
                "aborted:" + progress.phase(),
                progress.snapshotsProcessed(), progress.snapshotsSkipped(), progress.snapshotsFailed(),
                progress.journalProcessed(), progress.journalSkipped(), progress.journalFailed(),
                Instant.now().toString(),
                command.getDittoHeaders()), getSelf());
    }

    private void handleMigration(final MigrateConnectionEncryption command) {
        final ActorRef sender = getSender();

        if (migrationInProgress) {
            sender.tell(new Status.Failure(new IllegalStateException(
                    "Migration already in progress. Use migrateEncryptionStatus to check progress " +
                    "or migrateEncryptionAbort to cancel.")),
                    getSelf());
            return;
        }

        // Migration Logic:
        // - Encryption enabled + both keys set → Key rotation (decrypt with old, encrypt with new)
        // - Encryption enabled + only current key → Initial encryption (encrypt plaintext with key)
        // - Encryption disabled + old key set → Disable workflow (decrypt with old, write plaintext)
        // - Encryption disabled + no keys → Error (cannot migrate)

        final Optional<String> oldKeyOpt = encryptionConfig.getOldSymmetricalKey();
        final boolean isDisableWorkflow = !encryptionConfig.isEncryptionEnabled() && oldKeyOpt.isPresent();
        final boolean isInitialEncryption = encryptionConfig.isEncryptionEnabled() && oldKeyOpt.isEmpty();

        if (!encryptionConfig.isEncryptionEnabled() && !isDisableWorkflow) {
            sender.tell(new Status.Failure(new IllegalStateException(
                    "Encryption is not enabled and no old key configured. " +
                    "Cannot migrate without encryption keys.")),
                    getSelf());
            return;
        }

        final String newKey;
        final String oldKey;
        if (isDisableWorkflow) {
            // Decrypt with old key, write plaintext
            newKey = null;
            oldKey = oldKeyOpt.get();
        } else if (isInitialEncryption) {
            // Encrypt plaintext data with current key (no old key needed)
            newKey = encryptionConfig.getSymmetricalKey();
            oldKey = null;
        } else {
            // Key rotation: decrypt with old key, encrypt with new key
            newKey = encryptionConfig.getSymmetricalKey();
            oldKey = oldKeyOpt.get();
        }
        final List<String> pointers = encryptionConfig.getJsonPointers();
        final boolean dryRun = command.isDryRun();
        final boolean resume = command.isResume();

        migrationInProgress = true;
        currentDryRun = dryRun;
        abortRequested = false;
        currentProgress = null;
        activeKillSwitch = KillSwitches.shared("encryption-migration");
        final String mode = isDisableWorkflow ? "disable" : isInitialEncryption ? "initial-encryption" : "key-rotation";
        log.info("Starting encryption migration (mode={}, dryRun={}, resume={})", mode, dryRun, resume);

        final CompletionStage<MigrationProgress> migrationResult;
        if (resume) {
            migrationResult = progressTracker.loadProgress().thenCompose(optProgress -> {
                if (optProgress.isEmpty() || PHASE_COMPLETED.equals(optProgress.get().phase())) {
                    // No previous migration exists or it already completed — nothing to resume
                    final String reason = optProgress.isEmpty()
                            ? "no previous migration found" : "previous migration already completed";
                    log.info("Resume requested but {}, nothing to do", reason);
                    migrationInProgress = false;
                    final MigrationProgress completed = optProgress.orElseGet(MigrationProgress::new)
                            .withPhase(PHASE_COMPLETED);
                    currentProgress = completed;
                    sender.tell(MigrateConnectionEncryptionResponse.alreadyCompleted(
                            Instant.now().toString(), command.getDittoHeaders()), getSelf());
                    return CompletableFuture.completedFuture(completed);
                }
                final MigrationProgress progress = optProgress.get();
                log.info("Resuming migration from saved progress: phase={}, lastSnapshotId={}, " +
                                "lastJournalId={}", progress.phase(),
                        progress.lastProcessedSnapshotId(), progress.lastProcessedJournalId());
                sender.tell(MigrateConnectionEncryptionResponse.accepted(
                        true, Instant.now().toString(), dryRun, command.getDittoHeaders()), getSelf());
                return runMigration(progress, oldKey, newKey, pointers, dryRun);
            });
        } else {
            migrationResult = progressTracker.deleteProgress().thenCompose(v ->
                    runMigration(new MigrationProgress(), oldKey, newKey, pointers, dryRun));
            // Reply immediately with 202 Accepted
            sender.tell(MigrateConnectionEncryptionResponse.accepted(
                    false, Instant.now().toString(), dryRun, command.getDittoHeaders()), getSelf());
        }

        final ActorRef self = getSelf();
        migrationResult.whenComplete((progress, error) ->
                self.tell(new MigrationCompleted(progress, error, dryRun), ActorRef.noSender()));
    }

    /**
     * Runs the encryption migration process.
     * <p>
     * Processes both snapshot and journal collections in sequence. Handles resume logic
     * by checking the initial progress phase and skipping completed phases.
     *
     * @param initialProgress the starting progress (from resume or fresh start)
     * @param oldKey the old encryption key (null for initial encryption)
     * @param newKey the new encryption key (null for disable workflow)
     * @param pointers the JSON pointers to encrypt/decrypt
     * @param dryRun if true, validate without writing changes
     * @return completion stage with final migration progress
     */
    private CompletionStage<MigrationProgress> runMigration(final MigrationProgress initialProgress,
            final String oldKey, final String newKey, final List<String> pointers, final boolean dryRun) {

        log.info("runMigration: loaded progress phase={}, lastSnapshotId={}, lastJournalId={}, " +
                        "snapshots(p={}/s={}/f={}), journal(p={}/s={}/f={})",
                initialProgress.phase(), initialProgress.lastProcessedSnapshotId(),
                initialProgress.lastProcessedJournalId(),
                initialProgress.snapshotsProcessed(), initialProgress.snapshotsSkipped(),
                initialProgress.snapshotsFailed(),
                initialProgress.journalProcessed(), initialProgress.journalSkipped(),
                initialProgress.journalFailed());

        // Strip "aborted:" prefix to get the effective phase for resume
        final String effectivePhase;
        if (initialProgress.phase().startsWith(PHASE_ABORTED_PREFIX)) {
            effectivePhase = initialProgress.phase().substring(PHASE_ABORTED_PREFIX.length());
            log.info("Stripped aborted prefix: effective phase={}", effectivePhase);
        } else {
            effectivePhase = initialProgress.phase();
        }

        final CompletionStage<MigrationProgress> afterSnapshots;
        if (PHASE_JOURNAL.equals(effectivePhase) || initialProgress.lastProcessedJournalId() != null) {
            // Resume from journal phase — snapshots already done.
            // Belt-and-suspenders: also skip snapshots if journal progress exists (handles legacy "aborted" format)
            if (initialProgress.lastProcessedJournalId() != null && !PHASE_JOURNAL.equals(effectivePhase)) {
                log.info("Skipping snapshots: lastProcessedJournalId is set (legacy aborted format)");
            }
            afterSnapshots = CompletableFuture.completedFuture(initialProgress);
        } else if (PHASE_COMPLETED.equals(effectivePhase)) {
            return CompletableFuture.completedFuture(initialProgress);
        } else {
            afterSnapshots = migrateSnapshots(initialProgress, oldKey, newKey, pointers, dryRun);
        }

        return afterSnapshots.thenCompose(progress -> {
            if (abortRequested) {
                return CompletableFuture.completedFuture(progress);
            }
            final MigrationProgress journalProgress = progress.withPhase(PHASE_JOURNAL);
            return migrateJournal(journalProgress, oldKey, newKey, pointers, dryRun);
        }).thenCompose(progress -> {
            if (abortRequested) {
                return CompletableFuture.completedFuture(progress);
            }
            final MigrationProgress completed = progress.withPhase(PHASE_COMPLETED);
            if (!dryRun) {
                return progressTracker.saveProgress(completed).thenApply(v -> completed);
            }
            return CompletableFuture.completedFuture(completed);
        });
    }

    /**
     * Migrates the snapshot collection (connection_snaps).
     * <p>
     * Builds a stream from {@link MigrationStreamFactory}, applies the kill switch for abort
     * support, groups documents into batches, and processes each batch via
     * {@link #processSnapshotBatch}.
     *
     * @param progress the current migration progress
     * @param oldKey the old encryption key
     * @param newKey the new encryption key
     * @param pointers the JSON pointers to encrypt/decrypt
     * @param dryRun if true, validate without writing changes
     * @return completion stage with updated progress after snapshot migration
     */
    private CompletionStage<MigrationProgress> migrateSnapshots(final MigrationProgress progress,
            final String oldKey, final String newKey, final List<String> pointers, final boolean dryRun) {

        log.info("Starting snapshot migration (dryRun={}, throttling={} docs/min)", dryRun,
                maxDocumentsPerMinute > 0 ? maxDocumentsPerMinute : "disabled");

        final Source<Document, NotUsed> source = MigrationStreamFactory.buildSnapshotStream(
                snapshotCollection, progress, pointers, batchSize, maxDocumentsPerMinute);

        return source
                .via(activeKillSwitch.flow())
                .grouped(batchSize)
                .runWith(Sink.foldAsync(progress, (currentProgress, batch) ->
                        processSnapshotBatch(currentProgress, batch, oldKey, newKey, pointers, dryRun)),
                        materializer)
                .thenApply(finalProgress -> {
                    log.info("Snapshot migration {}: processed={}, skipped={}, failed={}",
                            abortRequested ? "aborted" : "done",
                            finalProgress.snapshotsProcessed(), finalProgress.snapshotsSkipped(),
                            finalProgress.snapshotsFailed());
                    return finalProgress;
                });
    }

    /**
     * Processes a batch of snapshot documents.
     * <p>
     * Iterates through each document, delegates transformation to {@link DocumentProcessor},
     * updates progress counters, collects write models, and delegates batch write to
     * {@link #executeBatchWriteAndSaveProgress}.
     *
     * @param progress the current progress
     * @param batch the batch of documents to process
     * @param oldKey the old encryption key
     * @param newKey the new encryption key
     * @param pointers the JSON pointers to encrypt/decrypt
     * @param dryRun if true, skip database writes
     * @return completion stage with updated progress
     */
    private CompletionStage<MigrationProgress> processSnapshotBatch(final MigrationProgress progress,
            final List<Document> batch, final String oldKey, final String newKey,
            final List<String> pointers, final boolean dryRun) {

        log.debug("processSnapshotBatch: batchSize={}, firstId={}, lastId={}",
                batch.size(),
                batch.isEmpty() ? "N/A" : batch.get(0).get(ID_FIELD),
                batch.isEmpty() ? "N/A" : batch.get(batch.size() - 1).get(ID_FIELD));
        MigrationProgress currentProgress = progress;
        final List<WriteModel<Document>> writeModels = new ArrayList<>();

        for (final Document doc : batch) {
            final String docId = doc.get(ID_FIELD).toString();
            final String pid = doc.getString("pid");
            final DocumentProcessingResult result = processSnapshotDocument(doc, oldKey, newKey, pointers, dryRun);
            switch (result.outcome()) {
                case PROCESSED -> currentProgress = currentProgress.incrementSnapshotsProcessed();
                case SKIPPED -> currentProgress = currentProgress.incrementSnapshotsSkipped();
                case FAILED -> currentProgress = currentProgress.incrementSnapshotsFailed();
            }
            if (result.writeModel() != null) {
                writeModels.add(result.writeModel());
            }
            currentProgress = currentProgress
                    .withLastSnapshotId(docId)
                    .withLastSnapshotPid(pid);
        }

        return executeBatchWriteAndSaveProgress(
                currentProgress, writeModels, snapshotCollection, PHASE_SNAPSHOTS, dryRun, true);
    }

    private DocumentProcessingResult processSnapshotDocument(final Document doc, final String oldKey,
            final String newKey, final List<String> pointers, final boolean dryRun) {
        final MigrationContext context = MigrationContext.forSnapshots(oldKey, newKey, pointers);
        return DocumentProcessor.processSnapshotDocument(doc, context, dryRun);
    }

    /**
     * Migrates the journal collection (connection_journal).
     * <p>
     * Similar to {@link #migrateSnapshots} but processes journal event documents which
     * contain arrays of events with payloads to re-encrypt.
     *
     * @param progress the current migration progress
     * @param oldKey the old encryption key
     * @param newKey the new encryption key
     * @param pointers the JSON pointers to encrypt/decrypt
     * @param dryRun if true, validate without writing changes
     * @return completion stage with updated progress after journal migration
     */
    private CompletionStage<MigrationProgress> migrateJournal(final MigrationProgress progress,
            final String oldKey, final String newKey, final List<String> pointers, final boolean dryRun) {

        log.info("Starting journal migration (dryRun={}, throttling={} docs/min)", dryRun,
                maxDocumentsPerMinute > 0 ? maxDocumentsPerMinute : "disabled");

        final Source<Document, NotUsed> source = MigrationStreamFactory.buildJournalStream(
                journalCollection, progress, pointers, batchSize, maxDocumentsPerMinute);

        return source
                .via(activeKillSwitch.flow())
                .grouped(batchSize)
                .runWith(Sink.foldAsync(progress, (currentProgress, batch) ->
                        processJournalBatch(currentProgress, batch, oldKey, newKey, pointers, dryRun)),
                        materializer)
                .thenApply(finalProgress -> {
                    log.info("Journal migration {}: processed={}, skipped={}, failed={}",
                            abortRequested ? "aborted" : "done",
                            finalProgress.journalProcessed(), finalProgress.journalSkipped(),
                            finalProgress.journalFailed());
                    return finalProgress;
                });
    }

    private CompletionStage<MigrationProgress> processJournalBatch(final MigrationProgress progress,
            final List<Document> batch, final String oldKey, final String newKey,
            final List<String> pointers, final boolean dryRun) {

        log.debug("processJournalBatch: batchSize={}, firstId={}, lastId={}",
                batch.size(),
                batch.isEmpty() ? "N/A" : batch.getFirst().get(ID_FIELD),
                batch.isEmpty() ? "N/A" : batch.getLast().get(ID_FIELD));
        MigrationProgress currentProgress = progress;
        final List<WriteModel<Document>> writeModels = new ArrayList<>();

        for (final Document doc : batch) {
            final String docIdStr = doc.get(ID_FIELD).toString();
            final String pid = doc.getString("pid");
            final DocumentProcessingResult result = processJournalDocument(doc, oldKey, newKey, pointers, dryRun);
            switch (result.outcome()) {
                case PROCESSED -> currentProgress = currentProgress.incrementJournalProcessed();
                case SKIPPED -> currentProgress = currentProgress.incrementJournalSkipped();
                case FAILED -> currentProgress = currentProgress.incrementJournalFailed();
            }
            if (result.writeModel() != null) {
                writeModels.add(result.writeModel());
            }
            currentProgress = currentProgress.withLastJournalId(docIdStr);
            currentProgress = currentProgress.withLastJournalPid(pid);
        }

        return executeBatchWriteAndSaveProgress(
                currentProgress, writeModels, journalCollection, PHASE_JOURNAL, dryRun, false);
    }

    private DocumentProcessingResult processJournalDocument(final Document doc, final String oldKey,
            final String newKey, final List<String> pointers, final boolean dryRun) {
        final MigrationContext context = MigrationContext.forJournal(oldKey, newKey, pointers);
        return DocumentProcessor.processJournalDocument(doc, context, dryRun);
    }

    /**
     * Executes a batch write to MongoDB and saves migration progress.
     * <p>
     * Coordinates the batch write operation, handles failures by adjusting progress counters,
     * saves progress via {@link MigrationProgressTracker}, and sends progress updates to
     * the actor for in-memory tracking.
     *
     * @param progress the current progress
     * @param writeModels the MongoDB write models for bulk update
     * @param collection the MongoDB collection to write to
     * @param phase the current migration phase (for logging and progress tracking)
     * @param dryRun if true, skip database writes
     * @param isSnapshot true for snapshot collection, false for journal collection
     * @return completion stage with progress after write and save
     */
    private CompletionStage<MigrationProgress> executeBatchWriteAndSaveProgress(
            final MigrationProgress progress,
            final List<WriteModel<Document>> writeModels,
            final MongoCollection<Document> collection,
            final String phase,
            final boolean dryRun,
            final boolean isSnapshot) {

        final int batchWriteCount = writeModels.size();
        log.debug("executeBatchWrite: phase={}, writeModels={}, dryRun={}", phase, batchWriteCount, dryRun);
        final CompletionStage<MigrationProgress> writeStage;
        if (!dryRun && !writeModels.isEmpty()) {
            writeStage = Source.fromPublisher(collection.bulkWrite(writeModels,
                            new BulkWriteOptions().ordered(false)))
                    .runWith(Sink.head(), materializer)
                    .thenApply(r -> {
                        log.debug("Bulk write completed for {} batch: {} documents written",
                                phase, r.getModifiedCount() + r.getInsertedCount());
                        return progress;
                    })
                    .exceptionally(e -> {
                        log.error("Bulk write failed for {} batch ({} documents): {}",
                                phase, batchWriteCount, e.getMessage());
                        return progress.adjustForBulkWriteFailure(batchWriteCount, isSnapshot);
                    });
        } else {
            writeStage = CompletableFuture.completedFuture(progress);
        }

        final ActorRef self = getSelf();
        return writeStage.thenCompose(progressAfterWrite -> {
            if (!dryRun) {
                final MigrationProgress progressToSave = progressAfterWrite.withPhase(phase);
                return progressTracker.saveProgress(progressToSave)
                        .thenApply(_ -> progressAfterWrite)
                        .exceptionally(e -> {
                            log.error("Failed to save progress after {} batch: {}", phase, e.getMessage());
                            return progressAfterWrite;
                        });
            }
            return CompletableFuture.completedFuture(progressAfterWrite);
        }).thenApply(p -> {
            self.tell(new ProgressUpdate(p), ActorRef.noSender());
            return p;
        });
    }

    /**
     * Internal message used to pipe progress updates back to the actor thread.
     */
    private record ProgressUpdate(MigrationProgress progress) {}

    /**
     * Internal message used to pipe migration completion back to the actor thread.
     */
    private record MigrationCompleted(@Nullable MigrationProgress progress, @Nullable Throwable error, boolean dryRun) {}

}
