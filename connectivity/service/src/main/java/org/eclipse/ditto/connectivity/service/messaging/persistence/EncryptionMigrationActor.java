/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
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

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

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
import org.bson.BsonDocument;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.eclipse.ditto.connectivity.model.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.connectivity.service.config.FieldsEncryptionConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.DittoBsonJson;
import org.eclipse.ditto.internal.utils.persistence.mongo.MongoClientWrapper;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.MongoDbConfig;
import org.eclipse.ditto.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOneModel;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.WriteModel;
import com.mongodb.reactivestreams.client.MongoCollection;

/**
 * Actor that performs encryption key migration for persisted connection data.
 * <p>
 * Reads snapshots and journal events from MongoDB, decrypts with the old key,
 * re-encrypts with the new key, and batch-updates documents using Pekko Streams.
 * Supports dry-run, resumption, and progress tracking.
 */
public final class EncryptionMigrationActor extends AbstractActor {

    /**
     * The name of this actor.
     */
    public static final String ACTOR_NAME = "encryptionMigration";

    private static final Logger LOG = LoggerFactory.getLogger(EncryptionMigrationActor.class);

    private static final String SNAPSHOT_COLLECTION = "connection_snaps";
    private static final String JOURNAL_COLLECTION = "connection_journal";
    private static final String PROGRESS_COLLECTION = "connection_encryption_migration";

    private static final String PHASE_SNAPSHOTS = "snapshots";
    private static final String PHASE_JOURNAL = "journal";
    private static final String PHASE_COMPLETED = "completed";

    private static final String PROGRESS_ID = "current";

    // MongoDB field names for pekko-persistence-mongodb
    private static final String SNAPSHOT_SERIALIZED_FIELD = "s2";
    private static final String JOURNAL_EVENTS_FIELD = "events";
    private static final String JOURNAL_PAYLOAD_FIELD = "p";
    private static final String ID_FIELD = "_id";

    // Entity type prefix used for journal event encryption
    private static final String JOURNAL_ENTITY_TYPE_PREFIX = "connection";
    // Snapshot encryption uses empty prefix
    private static final String SNAPSHOT_ENTITY_TYPE_PREFIX = "";

    private final MongoClientWrapper mongoClient;
    private final FieldsEncryptionConfig encryptionConfig;
    private final Materializer materializer;
    private final MongoCollection<Document> snapshotCollection;
    private final MongoCollection<Document> journalCollection;
    private final MongoCollection<Document> progressCollection;
    private final int batchSize;
    private final int maxDocumentsPerMinute;

    private boolean migrationInProgress = false;
    private boolean currentDryRun = false;
    private volatile boolean abortRequested = false;
    @Nullable
    private SharedKillSwitch activeKillSwitch;
    @Nullable
    private volatile MigrationProgress currentProgress;

    @SuppressWarnings("unused")
    private EncryptionMigrationActor(final ConnectivityConfig connectivityConfig) {
        final MongoDbConfig mongoDbConfig = connectivityConfig.getMongoDbConfig();
        this.encryptionConfig = connectivityConfig.getConnectionConfig().getFieldsEncryptionConfig();
        this.mongoClient = MongoClientWrapper.newInstance(mongoDbConfig);
        this.materializer = Materializer.createMaterializer(this::getContext);
        this.batchSize = encryptionConfig.getMigrationBatchSize();
        this.maxDocumentsPerMinute = encryptionConfig.getMigrationMaxDocumentsPerMinute();

        final var db = mongoClient.getDefaultDatabase();
        this.snapshotCollection = db.getCollection(SNAPSHOT_COLLECTION);
        this.journalCollection = db.getCollection(JOURNAL_COLLECTION);
        this.progressCollection = db.getCollection(PROGRESS_COLLECTION);
    }

    /**
     * Creates Props for this actor.
     *
     * @param connectivityConfig the connectivity configuration.
     * @return the Props.
     */
    public static Props props(final ConnectivityConfig connectivityConfig) {
        return Props.create(EncryptionMigrationActor.class, connectivityConfig);
    }

    @Override
    public void postStop() throws Exception {
        mongoClient.close();
        super.postStop();
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(MigrateConnectionEncryption.class, this::handleMigration)
                .match(MigrateConnectionEncryptionAbort.class, this::handleAbort)
                .match(MigrateConnectionEncryptionStatus.class, this::handleStatus)
                .build();
    }

    private void handleStatus(final MigrateConnectionEncryptionStatus command) {
        final ActorRef sender = getSender();
        final MigrationProgress inMemory = currentProgress;
        if (inMemory != null) {
            // Use in-memory progress (available during and after migration, including dry-run)
            final String phase = migrationInProgress
                    ? "in_progress:" + inMemory.phase
                    : inMemory.phase;
            sender.tell(MigrateConnectionEncryptionStatusResponse.of(
                    phase,
                    inMemory.snapshotsProcessed, inMemory.snapshotsSkipped, inMemory.snapshotsFailed,
                    inMemory.journalProcessed, inMemory.journalSkipped, inMemory.journalFailed,
                    inMemory.lastProcessedSnapshotId, inMemory.lastProcessedSnapshotPid,
                    inMemory.lastProcessedJournalId, inMemory.lastProcessedJournalPid,
                    inMemory.startedAt, Instant.now().toString(),
                    currentDryRun,
                    migrationInProgress,
                    command.getDittoHeaders()), getSelf());
        } else {
            // Fall back to MongoDB (e.g. after service restart)
            loadProgress().whenComplete((optProgress, error) -> {
                if (error != null) {
                    sender.tell(new Status.Failure(error), getSelf());
                } else {
                    final MigrationProgress progress = optProgress.orElseGet(MigrationProgress::new);
                    sender.tell(MigrateConnectionEncryptionStatusResponse.of(
                            progress.phase,
                            progress.snapshotsProcessed, progress.snapshotsSkipped, progress.snapshotsFailed,
                            progress.journalProcessed, progress.journalSkipped, progress.journalFailed,
                            progress.lastProcessedSnapshotId, progress.lastProcessedSnapshotPid,
                            progress.lastProcessedJournalId, progress.lastProcessedJournalPid,
                            progress.startedAt, Instant.now().toString(),
                            false,
                            false,
                            command.getDittoHeaders()), getSelf());
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

        LOG.info("Abort requested for running migration");
        abortRequested = true;
        if (activeKillSwitch != null) {
            activeKillSwitch.shutdown();
        }

        final MigrationProgress progress = currentProgress != null ? currentProgress : new MigrationProgress();
        sender.tell(MigrateConnectionEncryptionAbortResponse.of(
                "aborted:" + progress.phase,
                progress.snapshotsProcessed, progress.snapshotsSkipped, progress.snapshotsFailed,
                progress.journalProcessed, progress.journalSkipped, progress.journalFailed,
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
        LOG.info("Starting encryption migration (mode={}, dryRun={}, resume={})", mode, dryRun, resume);

        final CompletionStage<MigrationProgress> migrationResult;
        if (resume) {
            migrationResult = loadProgress().thenCompose(optProgress -> {
                if (optProgress.isEmpty() || PHASE_COMPLETED.equals(optProgress.get().phase)) {
                    // No previous migration exists or it already completed — nothing to resume
                    final String reason = optProgress.isEmpty()
                            ? "no previous migration found" : "previous migration already completed";
                    LOG.info("Resume requested but {}, nothing to do", reason);
                    migrationInProgress = false;
                    final MigrationProgress completed = optProgress.orElseGet(MigrationProgress::new)
                            .withPhase(PHASE_COMPLETED);
                    currentProgress = completed;
                    sender.tell(MigrateConnectionEncryptionResponse.alreadyCompleted(
                            Instant.now().toString(), command.getDittoHeaders()), getSelf());
                    return java.util.concurrent.CompletableFuture.completedFuture(completed);
                }
                final MigrationProgress progress = optProgress.get();
                sender.tell(MigrateConnectionEncryptionResponse.accepted(
                        true, Instant.now().toString(), dryRun, command.getDittoHeaders()), getSelf());
                return runMigration(progress, oldKey, newKey, pointers, dryRun);
            });
        } else {
            migrationResult = deleteProgress().thenCompose(v ->
                    runMigration(new MigrationProgress(), oldKey, newKey, pointers, dryRun));
            // Reply immediately with 202 Accepted
            sender.tell(MigrateConnectionEncryptionResponse.accepted(
                    false, Instant.now().toString(), dryRun, command.getDittoHeaders()), getSelf());
        }

        migrationResult.whenComplete((progress, error) -> {
            migrationInProgress = false;
            activeKillSwitch = null;
            final boolean wasAborted = abortRequested;
            abortRequested = false;
            if (error != null && !wasAborted) {
                LOG.error("Encryption migration failed", error);
            } else {
                final String finalPhase = wasAborted
                        ? "aborted:" + (progress != null ? progress.phase : "unknown")
                        : progress.phase;
                LOG.info("Encryption migration {} (dryRun={}): {}",
                        wasAborted ? "aborted" : "completed", dryRun, progress);
                if (progress != null) {
                    currentProgress = progress.withPhase(finalPhase);
                    if (wasAborted && !dryRun) {
                        saveProgress(progress.withPhase("aborted")).toCompletableFuture().join();
                    }
                }
            }
        });
    }

    private CompletionStage<MigrationProgress> runMigration(final MigrationProgress initialProgress,
            final String oldKey, final String newKey, final List<String> pointers, final boolean dryRun) {

        final CompletionStage<MigrationProgress> afterSnapshots;
        if (PHASE_JOURNAL.equals(initialProgress.phase)) {
            // Resume from journal phase — snapshots already done
            afterSnapshots = java.util.concurrent.CompletableFuture.completedFuture(initialProgress);
        } else if (PHASE_COMPLETED.equals(initialProgress.phase)) {
            return java.util.concurrent.CompletableFuture.completedFuture(initialProgress);
        } else {
            afterSnapshots = migrateSnapshots(initialProgress, oldKey, newKey, pointers, dryRun);
        }

        return afterSnapshots.thenCompose(progress -> {
            if (abortRequested) {
                return java.util.concurrent.CompletableFuture.completedFuture(progress);
            }
            final MigrationProgress journalProgress = progress.withPhase(PHASE_JOURNAL);
            return migrateJournal(journalProgress, oldKey, newKey, pointers, dryRun);
        }).thenCompose(progress -> {
            if (abortRequested) {
                return java.util.concurrent.CompletableFuture.completedFuture(progress);
            }
            final MigrationProgress completed = progress.withPhase(PHASE_COMPLETED);
            if (!dryRun) {
                return saveProgress(completed).thenApply(v -> completed);
            }
            return java.util.concurrent.CompletableFuture.completedFuture(completed);
        });
    }

    private CompletionStage<MigrationProgress> migrateSnapshots(final MigrationProgress progress,
            final String oldKey, final String newKey, final List<String> pointers, final boolean dryRun) {

        LOG.info("Starting snapshot migration (dryRun={}, throttling={} docs/min)", dryRun,
                maxDocumentsPerMinute > 0 ? maxDocumentsPerMinute : "disabled");
        final Bson resumeFilter = progress.lastProcessedSnapshotId != null
                ? Filters.gt(ID_FIELD, progress.lastProcessedSnapshotId)
                : Filters.empty();
        final Bson encryptableFieldsFilter = buildEncryptableFieldsFilter(
                SNAPSHOT_SERIALIZED_FIELD, SNAPSHOT_ENTITY_TYPE_PREFIX, pointers);
        final Bson filter = Filters.and(resumeFilter, encryptableFieldsFilter);

        final Source<Document, NotUsed> source = Source.fromPublisher(
                snapshotCollection.find(filter)
                        .sort(Sorts.ascending(ID_FIELD))
                        .batchSize(batchSize));

        final Source<Document, NotUsed> throttledSource = applyThrottling(source, maxDocumentsPerMinute);

        return throttledSource
                .via(activeKillSwitch.flow())
                .grouped(batchSize)
                .runWith(Sink.fold(progress, (currentProgress, batch) ->
                        processSnapshotBatch(currentProgress, batch, oldKey, newKey, pointers, dryRun)),
                        materializer)
                .thenApply(finalProgress -> {
                    LOG.info("Snapshot migration {}: processed={}, skipped={}, failed={}",
                            abortRequested ? "aborted" : "done",
                            finalProgress.snapshotsProcessed, finalProgress.snapshotsSkipped,
                            finalProgress.snapshotsFailed);
                    return finalProgress;
                });
    }

    private MigrationProgress processSnapshotBatch(final MigrationProgress progress,
            final List<Document> batch, final String oldKey, final String newKey,
            final List<String> pointers, final boolean dryRun) {

        MigrationProgress currentProgress = progress;
        final List<WriteModel<Document>> writeModels = new ArrayList<>();

        for (final Document doc : batch) {
            final String docId = doc.get(ID_FIELD).toString();
            final String pid = doc.getString("pid");
            try {
                final Document s2 = doc.get(SNAPSHOT_SERIALIZED_FIELD, Document.class);
                if (s2 == null) {
                    currentProgress = currentProgress.incrementSnapshotsSkipped();
                    // Update last processed ID and PID even when skipped
                    currentProgress = currentProgress.withLastSnapshotId(docId);
                    currentProgress = currentProgress.withLastSnapshotPid(pid);
                    continue;
                }

                final BsonDocument bsonDoc = s2.toBsonDocument(Document.class,
                        com.mongodb.MongoClientSettings.getDefaultCodecRegistry());
                final JsonObject jsonObject = DittoBsonJson.getInstance().serialize(bsonDoc);

                final JsonObject reEncrypted = reEncryptFields(jsonObject, SNAPSHOT_ENTITY_TYPE_PREFIX,
                        pointers, oldKey, newKey);

                if (reEncrypted == null) {
                    // Already encrypted with new key
                    currentProgress = currentProgress.incrementSnapshotsSkipped();
                } else {
                    if (!dryRun) {
                        final BsonDocument newBson = DittoBsonJson.getInstance().parse(reEncrypted);
                        doc.put(SNAPSHOT_SERIALIZED_FIELD, Document.parse(newBson.toJson()));
                        writeModels.add(new ReplaceOneModel<>(
                                Filters.eq(ID_FIELD, doc.get(ID_FIELD)),
                                doc));
                    }
                    currentProgress = currentProgress.incrementSnapshotsProcessed();
                }
            } catch (final Exception e) {
                LOG.warn("Failed to process snapshot {} (pid={}): {}", docId, pid, e.getMessage());
                currentProgress = currentProgress.incrementSnapshotsFailed();
            }
            // Update last processed ID and PID for EVERY document
            currentProgress = currentProgress.withLastSnapshotId(docId);
            currentProgress = currentProgress.withLastSnapshotPid(pid);
        }

        // Perform bulk write if there are changes
        if (!dryRun && !writeModels.isEmpty()) {
            try {
                Source.fromPublisher(snapshotCollection.bulkWrite(writeModels,
                        new BulkWriteOptions().ordered(false)))
                        .runWith(Sink.head(), materializer)
                        .toCompletableFuture().join();
            } catch (final Exception e) {
                LOG.error("Bulk write failed for snapshot batch: {}", e.getMessage());
                // Continue to save progress even if bulk write fails
            }
        }

        // Save progress to MongoDB for non-dry-run; always update in-memory for status queries
        if (!dryRun) {
            final MigrationProgress progressToSave = currentProgress.withPhase(PHASE_SNAPSHOTS);
            try {
                saveProgress(progressToSave).toCompletableFuture().join();
            } catch (final Exception e) {
                LOG.error("Failed to save progress after snapshot batch: {}", e.getMessage());
            }
        }
        this.currentProgress = currentProgress;

        return currentProgress;
    }

    private CompletionStage<MigrationProgress> migrateJournal(final MigrationProgress progress,
            final String oldKey, final String newKey, final List<String> pointers, final boolean dryRun) {

        LOG.info("Starting journal migration (dryRun={}, throttling={} docs/min)", dryRun,
                maxDocumentsPerMinute > 0 ? maxDocumentsPerMinute : "disabled");
        final Bson resumeFilter = progress.lastProcessedJournalId != null
                ? Filters.gt(ID_FIELD, new ObjectId(progress.lastProcessedJournalId))
                : Filters.empty();
        final Bson encryptableFieldsFilter = buildEncryptableFieldsFilter(
                JOURNAL_EVENTS_FIELD + "." + JOURNAL_PAYLOAD_FIELD,
                JOURNAL_ENTITY_TYPE_PREFIX, pointers);
        final Bson filter = Filters.and(resumeFilter, encryptableFieldsFilter);

        final Source<Document, NotUsed> source = Source.fromPublisher(
                journalCollection.find(filter)
                        .sort(Sorts.ascending(ID_FIELD))
                        .batchSize(batchSize));

        final Source<Document, NotUsed> throttledSource = applyThrottling(source, maxDocumentsPerMinute);

        return throttledSource
                .via(activeKillSwitch.flow())
                .grouped(batchSize)
                .runWith(Sink.fold(progress, (currentProgress, batch) ->
                        processJournalBatch(currentProgress, batch, oldKey, newKey, pointers, dryRun)),
                        materializer)
                .thenApply(finalProgress -> {
                    LOG.info("Journal migration {}: processed={}, skipped={}, failed={}",
                            abortRequested ? "aborted" : "done",
                            finalProgress.journalProcessed, finalProgress.journalSkipped,
                            finalProgress.journalFailed);
                    return finalProgress;
                });
    }

    private MigrationProgress processJournalBatch(final MigrationProgress progress,
            final List<Document> batch, final String oldKey, final String newKey,
            final List<String> pointers, final boolean dryRun) {

        MigrationProgress currentProgress = progress;
        final List<WriteModel<Document>> writeModels = new ArrayList<>();

        for (final Document doc : batch) {
            final Object docId = doc.get(ID_FIELD);
            final String docIdStr = docId.toString();
            final String pid = doc.getString("pid");
            try {
                final List<Document> events = doc.getList(JOURNAL_EVENTS_FIELD, Document.class);
                if (events == null || events.isEmpty()) {
                    currentProgress = currentProgress.incrementJournalSkipped();
                    // Update last processed ID and PID even when skipped
                    currentProgress = currentProgress.withLastJournalId(docIdStr);
                    currentProgress = currentProgress.withLastJournalPid(pid);
                    continue;
                }

                boolean anyChanged = false;
                final List<Document> updatedEvents = new ArrayList<>(events.size());

                for (final Document event : events) {
                    final Document payload = event.get(JOURNAL_PAYLOAD_FIELD, Document.class);
                    if (payload == null) {
                        updatedEvents.add(event);
                        continue;
                    }

                    final BsonDocument bsonPayload = payload.toBsonDocument(Document.class,
                            com.mongodb.MongoClientSettings.getDefaultCodecRegistry());
                    final JsonObject jsonPayload = DittoBsonJson.getInstance().serialize(bsonPayload);

                    final JsonObject reEncrypted = reEncryptFields(jsonPayload, JOURNAL_ENTITY_TYPE_PREFIX,
                            pointers, oldKey, newKey);

                    if (reEncrypted != null) {
                        if (!dryRun) {
                            final BsonDocument newBson = DittoBsonJson.getInstance().parse(reEncrypted);
                            event.put(JOURNAL_PAYLOAD_FIELD, Document.parse(newBson.toJson()));
                        }
                        anyChanged = true;
                    }
                    updatedEvents.add(event);
                }

                if (anyChanged) {
                    if (!dryRun) {
                        doc.put(JOURNAL_EVENTS_FIELD, updatedEvents);
                        writeModels.add(new ReplaceOneModel<>(
                                Filters.eq(ID_FIELD, docId),
                                doc));
                    }
                    currentProgress = currentProgress.incrementJournalProcessed();
                } else {
                    currentProgress = currentProgress.incrementJournalSkipped();
                }
            } catch (final Exception e) {
                LOG.warn("Failed to process journal document {} (pid={}): {}", docIdStr, pid, e.getMessage());
                currentProgress = currentProgress.incrementJournalFailed();
            }
            // Update last processed ID and PID for EVERY document
            currentProgress = currentProgress.withLastJournalId(docIdStr);
            currentProgress = currentProgress.withLastJournalPid(pid);
        }

        // Perform bulk write if there are changes
        if (!dryRun && !writeModels.isEmpty()) {
            try {
                Source.fromPublisher(journalCollection.bulkWrite(writeModels,
                        new BulkWriteOptions().ordered(false)))
                        .runWith(Sink.head(), materializer)
                        .toCompletableFuture().join();
            } catch (final Exception e) {
                LOG.error("Bulk write failed for journal batch: {}", e.getMessage());
                // Continue to save progress even if bulk write fails
            }
        }

        // Save progress to MongoDB for non-dry-run; always update in-memory for status queries
        if (!dryRun) {
            final MigrationProgress progressToSave = currentProgress.withPhase(PHASE_JOURNAL);
            try {
                saveProgress(progressToSave).toCompletableFuture().join();
            } catch (final Exception e) {
                LOG.error("Failed to save progress after journal batch: {}", e.getMessage());
            }
        }
        this.currentProgress = currentProgress;

        return currentProgress;
    }

    /**
     * Applies throttling to the source stream if throttling is enabled (maxDocsPerMinute > 0).
     * Throttling is implemented using Pekko Streams throttle operator.
     *
     * @param source the source stream
     * @param maxDocsPerMinute maximum documents per minute, 0 means no throttling
     * @return throttled source if enabled, original source otherwise
     */
    private Source<Document, NotUsed> applyThrottling(final Source<Document, NotUsed> source,
            final int maxDocsPerMinute) {
        if (maxDocsPerMinute <= 0) {
            return source;
        }

        // Throttle directly using the configured docs/minute rate.
        // Pekko Streams throttle uses a token-bucket algorithm, so bursts up to maxDocsPerMinute
        // are allowed as long as the average rate stays within the limit.
        return source.throttle(maxDocsPerMinute, java.time.Duration.ofMinutes(1));
    }

    /**
     * Re-encrypts fields in a JSON object based on the migration mode.
     *
     * <p>Supports three modes:
     * <ul>
     *   <li><b>Initial encryption</b> ({@code oldKey == null}): encrypt plaintext with newKey</li>
     *   <li><b>Key rotation</b> (both keys set): decrypt with oldKey, encrypt with newKey</li>
     *   <li><b>Disable encryption</b> ({@code newKey == null}): decrypt with oldKey, write plaintext</li>
     * </ul>
     *
     * @param oldKey the old encryption key, or {@code null} for initial encryption (plaintext data)
     * @param newKey the new encryption key, or {@code null} to disable encryption (write plaintext)
     * @return the transformed JSON object, or {@code null} if already in the desired state (skip).
     */
    static JsonObject reEncryptFields(final JsonObject jsonObject, final String entityTypePrefix,
            final List<String> pointers, @Nullable final String oldKey, @Nullable final String newKey) {

        if (oldKey == null && newKey != null) {
            // Initial encryption: data is plaintext, encrypt with new key
            // Check if any field already has the encrypted_ prefix — if so, skip
            final boolean alreadyEncrypted = pointers.stream()
                    .map(p -> entityTypePrefix + p)
                    .map(org.eclipse.ditto.json.JsonPointer::of)
                    .flatMap(pointer -> jsonObject.getValue(pointer).stream())
                    .filter(org.eclipse.ditto.json.JsonValue::isString)
                    .anyMatch(v -> containsEncryptedValue(v.asString()));
            if (alreadyEncrypted) {
                return null;
            }
            final JsonObject encrypted = JsonFieldsEncryptor.encrypt(jsonObject, entityTypePrefix, pointers, newKey);
            // Skip if encrypt produced no changes (e.g. no matching pointers in this entity)
            return encrypted.equals(jsonObject) ? null : encrypted;
        }

        // Key rotation or disable workflow — oldKey must be set
        // Try decrypting with the old key
        try {
            final JsonObject decrypted = JsonFieldsEncryptor.decrypt(jsonObject, entityTypePrefix,
                    pointers, oldKey);

            if (newKey == null) {
                // Disable workflow: return decrypted plaintext, but skip if nothing changed
                // (decrypt silently passes through plaintext values, so unchanged means already plain)
                return decrypted.equals(jsonObject) ? null : decrypted;
            } else {
                // Key rotation: re-encrypt with new key
                final JsonObject reEncrypted = JsonFieldsEncryptor.encrypt(decrypted, entityTypePrefix, pointers, newKey);
                // Skip if the result is identical (e.g. no matching pointers in this entity)
                return reEncrypted.equals(jsonObject) ? null : reEncrypted;
            }
        } catch (final ConnectionConfigurationInvalidException e) {
            // Old key failed — try new key to see if already migrated
            // (Only applicable for key rotation, not disable workflow)
            if (newKey == null) {
                // Disable workflow: if old key fails, data is already plaintext - skip
                return null;
            }

            try {
                JsonFieldsEncryptor.decrypt(jsonObject, entityTypePrefix, pointers, newKey);
                // Already encrypted with new key — skip
                return null;
            } catch (final ConnectionConfigurationInvalidException e2) {
                // Both keys failed — data might be plaintext, try encrypting directly
                final JsonObject encrypted = JsonFieldsEncryptor.encrypt(jsonObject, entityTypePrefix, pointers, newKey);
                return encrypted.equals(jsonObject) ? null : encrypted;
            }
        }
    }

    /**
     * Checks whether a string value contains an encrypted portion — either as a direct
     * {@code encrypted_} prefix (non-URI fields) or embedded in the password part of a URI.
     */
    private static boolean containsEncryptedValue(final String value) {
        if (value.startsWith(JsonFieldsEncryptor.ENCRYPTED_PREFIX)) {
            return true;
        }
        try {
            final URI uri = new URI(value);
            if (uri.getScheme() != null && uri.getRawUserInfo() != null) {
                final String[] userPass = uri.getRawUserInfo().split(":", 2);
                return userPass.length == 2 &&
                        userPass[1].startsWith(JsonFieldsEncryptor.ENCRYPTED_PREFIX);
            }
        } catch (final Exception ignored) {
            // Not a valid URI — fall through
        }
        return false;
    }

    /**
     * Builds a MongoDB filter that matches only documents containing at least one of the
     * encryptable fields. This avoids fetching documents (e.g. empty events) that have
     * no fields to encrypt/decrypt.
     *
     * @param documentPrefix the BSON path prefix to the document (e.g. "s2" for snapshots,
     *        "events.p" for journal payloads)
     * @param entityTypePrefix the entity type prefix applied to pointers (e.g. "connection" for journal)
     * @param pointers the configured JSON pointers to encrypt
     * @return a Bson filter requiring at least one encryptable field to exist
     */
    private static Bson buildEncryptableFieldsFilter(final String documentPrefix,
            final String entityTypePrefix, final List<String> pointers) {
        final String prefix = entityTypePrefix.isEmpty()
                ? documentPrefix
                : documentPrefix + "." + entityTypePrefix;
        final List<Bson> existsFilters = pointers.stream()
                .map(pointer -> pointer.replace("/", "."))
                .map(dotPath -> Filters.exists(prefix + dotPath))
                .collect(Collectors.toList());
        return Filters.or(existsFilters);
    }

    private CompletionStage<Void> saveProgress(final MigrationProgress progress) {
        final Document progressDoc = new Document()
                .append(ID_FIELD, PROGRESS_ID)
                .append("phase", progress.phase)
                .append("lastProcessedSnapshotId", progress.lastProcessedSnapshotId)
                .append("lastProcessedSnapshotPid", progress.lastProcessedSnapshotPid)
                .append("lastProcessedJournalId", progress.lastProcessedJournalId)
                .append("lastProcessedJournalPid", progress.lastProcessedJournalPid)
                .append("snapshotsProcessed", progress.snapshotsProcessed)
                .append("snapshotsSkipped", progress.snapshotsSkipped)
                .append("snapshotsFailed", progress.snapshotsFailed)
                .append("journalProcessed", progress.journalProcessed)
                .append("journalSkipped", progress.journalSkipped)
                .append("journalFailed", progress.journalFailed)
                .append("startedAt", progress.startedAt)
                .append("updatedAt", Instant.now().toString());

        return Source.fromPublisher(
                progressCollection.replaceOne(
                        Filters.eq(ID_FIELD, PROGRESS_ID),
                        progressDoc,
                        new ReplaceOptions().upsert(true)))
                .runWith(Sink.ignore(), materializer)
                .thenApply(done -> null);
    }

    private CompletionStage<Optional<MigrationProgress>> loadProgress() {
        return Source.fromPublisher(
                progressCollection.find(Filters.eq(ID_FIELD, PROGRESS_ID)).first())
                .runWith(Sink.headOption(), materializer)
                .thenApply(optDoc -> optDoc.map(doc -> {
                    final MigrationProgress progress = new MigrationProgress();
                    progress.phase = doc.getString("phase");
                    progress.lastProcessedSnapshotId = doc.getString("lastProcessedSnapshotId");
                    progress.lastProcessedSnapshotPid = doc.getString("lastProcessedSnapshotPid");
                    progress.lastProcessedJournalId = doc.getString("lastProcessedJournalId");
                    progress.lastProcessedJournalPid = doc.getString("lastProcessedJournalPid");
                    progress.snapshotsProcessed = doc.getLong("snapshotsProcessed") != null
                            ? doc.getLong("snapshotsProcessed") : 0L;
                    progress.snapshotsSkipped = doc.getLong("snapshotsSkipped") != null
                            ? doc.getLong("snapshotsSkipped") : 0L;
                    progress.snapshotsFailed = doc.getLong("snapshotsFailed") != null
                            ? doc.getLong("snapshotsFailed") : 0L;
                    progress.journalProcessed = doc.getLong("journalProcessed") != null
                            ? doc.getLong("journalProcessed") : 0L;
                    progress.journalSkipped = doc.getLong("journalSkipped") != null
                            ? doc.getLong("journalSkipped") : 0L;
                    progress.journalFailed = doc.getLong("journalFailed") != null
                            ? doc.getLong("journalFailed") : 0L;
                    progress.startedAt = doc.getString("startedAt");
                    return progress;
                }));
    }

    private CompletionStage<Void> deleteProgress() {
        return Source.fromPublisher(
                progressCollection.deleteOne(Filters.eq(ID_FIELD, PROGRESS_ID)))
                .runWith(Sink.ignore(), materializer)
                .thenApply(done -> null);
    }

    /**
     * Mutable progress tracker for the migration process.
     */
    static final class MigrationProgress {
        String phase = PHASE_SNAPSHOTS;
        String lastProcessedSnapshotId;
        String lastProcessedSnapshotPid;
        String lastProcessedJournalId;
        String lastProcessedJournalPid;
        long snapshotsProcessed;
        long snapshotsSkipped;
        long snapshotsFailed;
        long journalProcessed;
        long journalSkipped;
        long journalFailed;
        String startedAt = Instant.now().toString();

        MigrationProgress withPhase(final String newPhase) {
            this.phase = newPhase;
            return this;
        }

        MigrationProgress withLastSnapshotId(final String id) {
            this.lastProcessedSnapshotId = id;
            return this;
        }

        MigrationProgress withLastSnapshotPid(final String pid) {
            this.lastProcessedSnapshotPid = pid;
            return this;
        }

        MigrationProgress withLastJournalId(final String id) {
            this.lastProcessedJournalId = id;
            return this;
        }

        MigrationProgress withLastJournalPid(final String pid) {
            this.lastProcessedJournalPid = pid;
            return this;
        }

        MigrationProgress incrementSnapshotsProcessed() {
            this.snapshotsProcessed++;
            return this;
        }

        MigrationProgress incrementSnapshotsSkipped() {
            this.snapshotsSkipped++;
            return this;
        }

        MigrationProgress incrementSnapshotsFailed() {
            this.snapshotsFailed++;
            return this;
        }

        MigrationProgress incrementJournalProcessed() {
            this.journalProcessed++;
            return this;
        }

        MigrationProgress incrementJournalSkipped() {
            this.journalSkipped++;
            return this;
        }

        MigrationProgress incrementJournalFailed() {
            this.journalFailed++;
            return this;
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

}
