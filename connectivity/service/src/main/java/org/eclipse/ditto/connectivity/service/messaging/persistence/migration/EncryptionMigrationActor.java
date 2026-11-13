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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

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
import org.eclipse.ditto.connectivity.model.ConnectivityConstants;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.connectivity.service.config.FieldsEncryptionConfig;
import org.eclipse.ditto.internal.utils.pekko.logging.DittoDiagnosticLoggingAdapter;
import org.eclipse.ditto.internal.utils.pekko.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.persistence.api.PersistenceBackendProvider;
import org.eclipse.ditto.internal.utils.persistence.api.payload.PersistedJournalEntry;
import org.eclipse.ditto.internal.utils.persistence.api.payload.PersistedPayloadStore;
import org.eclipse.ditto.internal.utils.persistence.api.payload.PersistedSnapshot;

/**
 * Actor that orchestrates encryption key migration for persisted connection data.
 * <p>
 * This actor handles message routing and stream execution for encrypting, re-encrypting,
 * or decrypting persisted connection data. It supports three migration modes:
 * <ul>
 *   <li><b>Initial encryption</b>: Encrypt plaintext data with a new key</li>
 *   <li><b>Key rotation</b>: Re-encrypt data from old key to new key</li>
 *   <li><b>Disable encryption</b>: Decrypt data back to plaintext</li>
 * </ul>
 * <p>
 * The actor reaches the persisted data through the active persistence backend's {@link PersistedPayloadStore}, which
 * it obtains from the {@link PersistenceBackendProvider} in {@link #preStart()} and closes in {@link #postStop()}, so
 * only the node that runs the singleton holds the backend client the store owns. The actor itself sees the payloads as
 * JSON only and never names a backend.
 * <p>
 * The actor delegates to helper classes for focused responsibilities:
 * <ul>
 *   <li>{@link PayloadReEncryptor} - Payload transformation and re-encryption logic</li>
 *   <li>{@link MigrationStreamFactory} - Stream construction with filters and throttling</li>
 *   <li>{@link MigrationProgressTracker} - Progress persistence for resume support</li>
 * </ul>
 * <p>
 * <b>Migration Flow:</b>
 * <ol>
 *   <li>Receives {@link MigrateConnectionEncryption} command</li>
 *   <li>Validates encryption configuration</li>
 *   <li>Processes the connection snapshots</li>
 *   <li>Processes the connection journal entries</li>
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
 * @see PayloadReEncryptor
 * @see MigrationStreamFactory
 * @see MigrationProgressTracker
 * @see MigrationProgress
 */
public final class EncryptionMigrationActor extends AbstractActor {

    /**
     * The name of this actor.
     */
    public static final String ACTOR_NAME = "encryptionMigration";

    private final DittoDiagnosticLoggingAdapter log = DittoLoggerFactory.getDiagnosticLoggingAdapter(this);

    private final FieldsEncryptionConfig encryptionConfig;
    private final PersistenceBackendProvider backendProvider;
    private final Materializer materializer;
    private final int batchSize;
    private final int maxDocumentsPerMinute;

    // Obtained in preStart and closed in postStop: the store (and the backend client it owns) exists only while this
    // singleton runs, on one node.
    @Nullable
    private PersistedPayloadStore payloadStore;
    private MigrationProgressTracker progressTracker;

    private boolean migrationInProgress = false;
    private boolean currentDryRun = false;
    private boolean abortRequested = false;
    private SharedKillSwitch activeKillSwitch;
    @Nullable
    private MigrationProgress currentProgress;

    private EncryptionMigrationActor(final ConnectivityConfig connectivityConfig,
            final PersistenceBackendProvider backendProvider) {
        this.encryptionConfig = connectivityConfig.getConnectionConfig().getFieldsEncryptionConfig();
        this.backendProvider = backendProvider;
        this.materializer = Materializer.createMaterializer(this::getContext);
        this.batchSize = encryptionConfig.getMigrationBatchSize();
        this.maxDocumentsPerMinute = encryptionConfig.getMigrationMaxDocumentsPerMinute();
    }

    /**
     * Creates Props for this actor. The actor obtains its {@link PersistedPayloadStore} for the {@code connection}
     * entity type from the provider when it starts and closes it when it stops.
     *
     * @param connectivityConfig the connectivity configuration.
     * @param backendProvider the active persistence backend's provider.
     * @return the Props.
     */
    public static Props props(final ConnectivityConfig connectivityConfig,
            final PersistenceBackendProvider backendProvider) {
        return Props.create(EncryptionMigrationActor.class, connectivityConfig, backendProvider);
    }

    @Override
    public void preStart() throws Exception {
        super.preStart();
        final PersistedPayloadStore store = backendProvider.payloadStore(ConnectivityConstants.ENTITY_TYPE.toString());
        payloadStore = store;
        progressTracker = new MigrationProgressTracker(store);
    }

    @Override
    public void postStop() throws Exception {
        if (activeKillSwitch != null) {
            activeKillSwitch.shutdown();
        }
        final PersistedPayloadStore store = payloadStore;
        payloadStore = null;
        if (store != null) {
            store.close();
        }
        super.postStop();
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
        migrationInProgress = update.progress.phase() != MigrationPhase.COMPLETED;
    }

    private static Throwable unwrapCompletionException(final Throwable error) {
        if (error instanceof CompletionException && error.getCause() != null) {
            return error.getCause();
        }
        return error;
    }

    private void handleMigrationCompleted(final MigrationCompleted completed) {
        migrationInProgress = false;
        activeKillSwitch = null;
        final boolean wasAborted = abortRequested;
        abortRequested = false;
        final MigrationProgress progress = completed.progress;
        final boolean dryRun = completed.dryRun;
        final Throwable error = completed.error != null ? unwrapCompletionException(completed.error) : null;

        if (error != null && !wasAborted) {
            log.error(error, "Encryption migration failed");
        } else {
            if (error != null) {
                log.error(error, "Encryption migration stream failed while abort was requested");
            }
            final String finalPhase = wasAborted
                    ? MigrationPhase.getAbortedPrefix() + (progress != null ? progress.phase().getValue() : "unknown")
                    : (progress != null ? progress.phase().getValue() : "unknown");
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
                currentProgress = progress;
                if (wasAborted && !dryRun) {
                    progressTracker.saveProgressWithRetry(progress, 2)
                            .whenComplete((v, saveErr) -> {
                                if (saveErr != null) {
                                    log.error(unwrapCompletionException(saveErr),
                                            "Failed to save abort progress after retries");
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
                    ? inMemory.phase().toInProgressString()
                    : inMemory.phase().getValue();
            sender.tell(MigrateConnectionEncryptionStatusResponse.of(
                    phase, inMemory, currentDryRun, migrationInProgress,
                    command.getDittoHeaders()), getSelf());
        } else {
            // Fall back to the stored progress (e.g. after service restart)
            final ActorRef self = getSelf();
            progressTracker.loadProgress().thenApply(optProgress -> {
                final MigrationProgress progress = optProgress.orElseGet(MigrationProgress::new);
                return MigrateConnectionEncryptionStatusResponse.of(
                        progress.phase().getValue(), progress, false, false,
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
                progress.phase().toAbortedString(),
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

        final String newKey = encryptionConfig.getSymmetricalKey().orElse(null);
        final String oldKey = oldKeyOpt.orElse(null);
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
                if (optProgress.isEmpty() || optProgress.get().phase() == MigrationPhase.COMPLETED) {
                    // No previous migration exists or it already completed — nothing to resume
                    final String reason = optProgress.isEmpty()
                            ? "no previous migration found" : "previous migration already completed";
                    log.info("Resume requested but {}, nothing to do", reason);
                    final MigrationProgress completed = optProgress.orElseGet(MigrationProgress::new)
                            .withPhase(MigrationPhase.COMPLETED);
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
        migrationResult.whenComplete((progress, error) -> {
            try {
                self.tell(new MigrationCompleted(progress, error, dryRun), ActorRef.noSender());
            } catch (final Exception e) {
                log.error(e, "Failed to send MigrationCompleted message, forcing cleanup");
                self.tell(new MigrationCompleted(null, e, dryRun), ActorRef.noSender());
            }
        });
    }

    /**
     * Runs the encryption migration process.
     * <p>
     * Processes the snapshots and the journal in sequence. Handles resume logic
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
            @Nullable final String oldKey, @Nullable final String newKey, final List<String> pointers, final boolean dryRun) {

        log.info("runMigration: loaded progress phase={}, lastSnapshotId={}, lastJournalId={}, " +
                        "snapshots(p={}/s={}/f={}), journal(p={}/s={}/f={})",
                initialProgress.phase(), initialProgress.lastProcessedSnapshotId(),
                initialProgress.lastProcessedJournalId(),
                initialProgress.snapshotsProcessed(), initialProgress.snapshotsSkipped(),
                initialProgress.snapshotsFailed(),
                initialProgress.journalProcessed(), initialProgress.journalSkipped(),
                initialProgress.journalFailed());

        // Get the effective phase for resume
        final MigrationPhase effectivePhase = initialProgress.phase();

        final CompletionStage<MigrationProgress> afterSnapshots;
        if (effectivePhase == MigrationPhase.COMPLETED) {
            return CompletableFuture.completedFuture(initialProgress);
        } else if (effectivePhase == MigrationPhase.JOURNAL) {
            // Resume from journal phase — snapshots already done
            afterSnapshots = CompletableFuture.completedFuture(initialProgress);
        } else {
            afterSnapshots = migrateSnapshots(initialProgress, oldKey, newKey, pointers, dryRun);
        }

        return afterSnapshots.thenCompose(progress -> {
            if (abortRequested) {
                return CompletableFuture.completedFuture(progress);
            }
            final MigrationProgress journalProgress = progress.withPhase(MigrationPhase.JOURNAL);
            return migrateJournal(journalProgress, oldKey, newKey, pointers, dryRun);
        }).thenCompose(progress -> {
            if (abortRequested) {
                return CompletableFuture.completedFuture(progress);
            }
            final MigrationProgress completed = progress.withPhase(MigrationPhase.COMPLETED);
            if (!dryRun) {
                return progressTracker.saveProgress(completed).thenApply(v -> completed);
            }
            return CompletableFuture.completedFuture(completed);
        });
    }

    /**
     * Migrates the connection snapshots.
     * <p>
     * Builds a stream from {@link MigrationStreamFactory}, applies the kill switch for abort
     * support, groups snapshots into batches, and processes each batch via
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
            @Nullable final String oldKey, @Nullable final String newKey, final List<String> pointers, final boolean dryRun) {

        log.info("Starting snapshot migration (dryRun={}, throttling={} docs/min)", dryRun,
                maxDocumentsPerMinute > 0 ? maxDocumentsPerMinute : "disabled");

        final Source<PersistedSnapshot, NotUsed> source = MigrationStreamFactory.buildSnapshotStream(
                payloadStore(), progress, pointers, batchSize, maxDocumentsPerMinute);

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
     * Processes a batch of snapshots.
     * <p>
     * Iterates through each snapshot, delegates transformation to {@link PayloadReEncryptor},
     * updates progress counters, collects the rewritten snapshots, and delegates the batch write to
     * {@link #executeBatchWriteAndSaveProgress}.
     *
     * @param progress the current progress
     * @param batch the batch of snapshots to process
     * @param oldKey the old encryption key
     * @param newKey the new encryption key
     * @param pointers the JSON pointers to encrypt/decrypt
     * @param dryRun if true, skip database writes
     * @return completion stage with updated progress
     */
    private CompletionStage<MigrationProgress> processSnapshotBatch(final MigrationProgress progress,
            final List<PersistedSnapshot> batch, @Nullable final String oldKey, @Nullable final String newKey,
            final List<String> pointers, final boolean dryRun) {

        log.debug("processSnapshotBatch: batchSize={}, firstId={}, lastId={}",
                batch.size(),
                batch.isEmpty() ? "N/A" : batch.getFirst().cursor(),
                batch.isEmpty() ? "N/A" : batch.getLast().cursor());
        MigrationProgress currentProgress = progress;
        final List<PersistedSnapshot> rewritten = new ArrayList<>();

        for (final PersistedSnapshot snapshot : batch) {
            final String docId = snapshot.cursor();
            final String pid = snapshot.pid();
            final PayloadProcessingResult<PersistedSnapshot> result =
                    processSnapshot(snapshot, oldKey, newKey, pointers, dryRun);
            switch (result.outcome()) {
                case PROCESSED -> currentProgress = currentProgress.incrementSnapshotsProcessed();
                case SKIPPED -> currentProgress = currentProgress.incrementSnapshotsSkipped();
                case FAILED -> {
                    currentProgress = currentProgress.incrementSnapshotsFailed();
                    log.warning("Failed to process snapshot document {} (pid={}), skipping", docId, pid);
                }
            }
            if (result.rewritten() != null) {
                rewritten.add(result.rewritten());
            }
            currentProgress = currentProgress.withLastSnapshot(docId, pid);
        }

        return executeBatchWriteAndSaveProgress(
                currentProgress, rewritten, payloadStore()::rewriteSnapshots, MigrationPhase.SNAPSHOTS, dryRun,
                true);
    }

    private PayloadProcessingResult<PersistedSnapshot> processSnapshot(final PersistedSnapshot snapshot,
            @Nullable final String oldKey, @Nullable final String newKey, final List<String> pointers,
            final boolean dryRun) {
        final MigrationContext context = MigrationContext.forSnapshots(oldKey, newKey, pointers);
        return PayloadReEncryptor.processSnapshot(snapshot, context, dryRun);
    }

    /**
     * Migrates the connection journal.
     * <p>
     * Similar to {@link #migrateSnapshots} but processes journal entries which
     * carry one or more event payloads to re-encrypt.
     *
     * @param progress the current migration progress
     * @param oldKey the old encryption key
     * @param newKey the new encryption key
     * @param pointers the JSON pointers to encrypt/decrypt
     * @param dryRun if true, validate without writing changes
     * @return completion stage with updated progress after journal migration
     */
    private CompletionStage<MigrationProgress> migrateJournal(final MigrationProgress progress,
            @Nullable final String oldKey, @Nullable final String newKey, final List<String> pointers, final boolean dryRun) {

        log.info("Starting journal migration (dryRun={}, throttling={} docs/min)", dryRun,
                maxDocumentsPerMinute > 0 ? maxDocumentsPerMinute : "disabled");

        final Source<PersistedJournalEntry, NotUsed> source = MigrationStreamFactory.buildJournalStream(
                payloadStore(), progress, pointers, batchSize, maxDocumentsPerMinute);

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
            final List<PersistedJournalEntry> batch, @Nullable final String oldKey, @Nullable final String newKey,
            final List<String> pointers, final boolean dryRun) {

        log.debug("processJournalBatch: batchSize={}, firstId={}, lastId={}",
                batch.size(),
                batch.isEmpty() ? "N/A" : batch.getFirst().cursor(),
                batch.isEmpty() ? "N/A" : batch.getLast().cursor());
        MigrationProgress currentProgress = progress;
        final List<PersistedJournalEntry> rewritten = new ArrayList<>();

        for (final PersistedJournalEntry entry : batch) {
            final String docIdStr = entry.cursor();
            final String pid = entry.pid();
            final PayloadProcessingResult<PersistedJournalEntry> result =
                    processJournalEntry(entry, oldKey, newKey, pointers, dryRun);
            switch (result.outcome()) {
                case PROCESSED -> currentProgress = currentProgress.incrementJournalProcessed();
                case SKIPPED -> currentProgress = currentProgress.incrementJournalSkipped();
                case FAILED -> {
                    currentProgress = currentProgress.incrementJournalFailed();
                    log.warning("Failed to process journal document {} (pid={}), skipping", docIdStr, pid);
                }
            }
            if (result.rewritten() != null) {
                rewritten.add(result.rewritten());
            }
            currentProgress = currentProgress.withLastJournal(docIdStr, pid);
        }

        return executeBatchWriteAndSaveProgress(
                currentProgress, rewritten, payloadStore()::rewriteJournalEntries, MigrationPhase.JOURNAL, dryRun,
                false);
    }

    private PayloadProcessingResult<PersistedJournalEntry> processJournalEntry(final PersistedJournalEntry entry,
            @Nullable final String oldKey, @Nullable final String newKey, final List<String> pointers,
            final boolean dryRun) {
        final MigrationContext context = MigrationContext.forJournal(oldKey, newKey, pointers);
        return PayloadReEncryptor.processJournalEntry(entry, context, dryRun);
    }

    /**
     * Executes a batch write through the payload store and saves migration progress.
     * <p>
     * Coordinates the batch write operation, handles failures by adjusting progress counters,
     * saves progress via {@link MigrationProgressTracker}, and sends progress updates to
     * the actor for in-memory tracking.
     *
     * @param progress the current progress
     * @param rewrittenEntries the rewritten entries to write back
     * @param rewrite the store operation that writes the entries back and yields the number actually written
     * @param phase the current migration phase (for logging and progress tracking)
     * @param dryRun if true, skip database writes
     * @param isSnapshot true for the snapshots, false for the journal
     * @param <E> the entry type
     * @return completion stage with progress after write and save
     */
    private <E> CompletionStage<MigrationProgress> executeBatchWriteAndSaveProgress(
            final MigrationProgress progress,
            final List<E> rewrittenEntries,
            final Function<List<E>, CompletionStage<Long>> rewrite,
            final MigrationPhase phase,
            final boolean dryRun,
            final boolean isSnapshot) {

        final int batchWriteCount = rewrittenEntries.size();
        log.debug("executeBatchWrite: phase={}, entries={}, dryRun={}", phase, batchWriteCount, dryRun);
        final CompletionStage<MigrationProgress> writeStage;
        if (!dryRun && !rewrittenEntries.isEmpty()) {
            writeStage = rewrite.apply(rewrittenEntries)
                    .thenApply(written -> {
                        if (written < batchWriteCount) {
                            log.warning("Partial bulk write for {} batch: {}/{} documents written",
                                    phase, written, batchWriteCount);
                        } else {
                            log.debug("Bulk write completed for {} batch: {} documents written",
                                    phase, written);
                        }
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

    private PersistedPayloadStore payloadStore() {
        final PersistedPayloadStore store = payloadStore;
        if (store == null) {
            throw new IllegalStateException("The payload store is not available: the actor is not started.");
        }
        return store;
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
