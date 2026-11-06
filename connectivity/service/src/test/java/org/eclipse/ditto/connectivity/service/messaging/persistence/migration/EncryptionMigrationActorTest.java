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

import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Status;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.awaitility.Awaitility;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.connectivity.service.config.DittoConnectivityConfig;
import org.eclipse.ditto.connectivity.service.messaging.TestConstants;
import org.eclipse.ditto.connectivity.service.messaging.persistence.JsonFieldsEncryptor;
import org.eclipse.ditto.connectivity.service.messaging.persistence.migration.InMemoryPersistedPayloadStore.JournalEntry;
import org.eclipse.ditto.connectivity.service.messaging.persistence.migration.InMemoryPersistedPayloadStore.Snapshot;
import org.eclipse.ditto.connectivity.service.messaging.persistence.migration.InMemoryPersistedPayloadStore.StreamCall;
import org.eclipse.ditto.connectivity.service.util.EncryptorAesGcm;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.persistence.api.DittoReadJournal;
import org.eclipse.ditto.internal.utils.persistence.api.PersistenceBackendProvider;
import org.eclipse.ditto.internal.utils.persistence.api.payload.PersistedPayloadStore;
import org.eclipse.ditto.internal.utils.persistence.api.payload.PersistedSnapshot;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Tests for the encryption migration logic in {@link EncryptionMigrationActor}.
 * <p>
 * Tests the core re-encryption logic (snapshot and journal field re-encryption), command/response serialization,
 * validation behavior, and the actor's behaviour on an in-memory {@link PersistedPayloadStore}: the 202 before any
 * store I/O, dry run, fresh start, resume, abort, status and the handling of a failed write.
 */
public final class EncryptionMigrationActorTest {

    private static String OLD_KEY;
    private static String NEW_KEY;
    private static String WRONG_KEY;

    private static final List<String> POINTERS = List.of(
            "/uri",
            "/credentials/password"
    );

    private static final List<String> JOURNAL_POINTERS = List.of(
            "/connection/uri",
            "/connection/credentials/password"
    );

    /** The stored progress field names, in the order the migration has always written them. */
    private static final List<String> PROGRESS_FIELD_NAMES = List.of(
            "phase",
            "lastProcessedSnapshotId", "lastProcessedSnapshotPid",
            "lastProcessedJournalId", "lastProcessedJournalPid",
            "snapshotsProcessed", "snapshotsSkipped", "snapshotsFailed",
            "journalProcessed", "journalSkipped", "journalFailed",
            "startedAt", "updatedAt");

    private static final Duration AWAIT = Duration.ofSeconds(10);

    private static ActorSystem actorSystem;
    private static ConnectivityConfig connectivityConfig;

    @BeforeClass
    public static void initKeys() throws NoSuchAlgorithmException {
        OLD_KEY = EncryptorAesGcm.generateAESKeyAsString();
        NEW_KEY = EncryptorAesGcm.generateAESKeyAsString();
        WRONG_KEY = EncryptorAesGcm.generateAESKeyAsString();

        // initial encryption with NEW_KEY; batches of one so that every entry is a batch of its own; no throttling
        final Config config = ConfigFactory.parseString("""
                        ditto.connectivity.connection.encryption {
                          encryption-enabled = true
                          symmetrical-key = "%s"
                          json-pointers = ["/uri", "/credentials/password"]
                          migration.batch-size = 1
                          migration.max-documents-per-minute = 0
                        }
                        """.formatted(NEW_KEY))
                .withFallback(TestConstants.CONFIG)
                .resolve();
        connectivityConfig = DittoConnectivityConfig.of(DefaultScopedConfig.dittoScoped(config));
        actorSystem = ActorSystem.create("EncryptionMigrationActorTest");
    }

    @AfterClass
    public static void tearDown() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem);
            actorSystem = null;
        }
    }

    // --- Re-encryption logic tests ---

    @Test
    public void reEncryptSnapshotFieldsFromOldKeyToNewKey() {
        final JsonObject plain = createPlainSnapshotJson();
        final JsonObject encryptedWithOldKey = JsonFieldsEncryptor.encrypt(plain, "", POINTERS, OLD_KEY);

        final MigrationContext context = MigrationContext.forSnapshots(OLD_KEY, NEW_KEY, POINTERS);
        final Optional<JsonObject> result = PayloadReEncryptor.reEncryptFields(encryptedWithOldKey, context);

        assertThat(result).isPresent();
        final JsonObject decryptedWithNewKey = JsonFieldsEncryptor.decrypt(result.get(), "", POINTERS, NEW_KEY);
        assertThat(decryptedWithNewKey.getValue("/credentials/password"))
                .contains(plain.getValue("/credentials/password").get());
    }

    @Test
    public void reEncryptJournalFieldsFromOldKeyToNewKey() {
        final JsonObject plain = createPlainJournalJson();
        final JsonObject encryptedWithOldKey = JsonFieldsEncryptor.encrypt(
                plain, "connection", POINTERS, OLD_KEY);

        final MigrationContext context = MigrationContext.forJournal(OLD_KEY, NEW_KEY, POINTERS);
        final Optional<JsonObject> result = PayloadReEncryptor.reEncryptFields(encryptedWithOldKey, context);

        assertThat(result).isPresent();
        final JsonObject decrypted = JsonFieldsEncryptor.decrypt(result.get(), "connection", POINTERS, NEW_KEY);
        assertThat(decrypted.getValue("/connection/credentials/password"))
                .contains(plain.getValue("/connection/credentials/password").get());
    }

    @Test
    public void skipAlreadyMigratedDocument() {
        final JsonObject plain = createPlainSnapshotJson();
        final JsonObject encryptedWithNewKey = JsonFieldsEncryptor.encrypt(plain, "", POINTERS, NEW_KEY);

        final MigrationContext context = MigrationContext.forSnapshots(OLD_KEY, NEW_KEY, POINTERS);
        final Optional<JsonObject> result = PayloadReEncryptor.reEncryptFields(encryptedWithNewKey, context);

        assertThat(result).isEmpty();
    }

    @Test
    public void keyRotationEncryptsPlaintextFieldsWithNewKey() {
        // During key rotation, plaintext fields (no encrypted_ prefix) are treated as plaintext:
        // decrypt passes them through unchanged, then encrypt wraps them with the new key.
        final JsonObject plain = createPlainSnapshotJson();

        final MigrationContext context = MigrationContext.forSnapshots(OLD_KEY, NEW_KEY, POINTERS);
        final Optional<JsonObject> result = PayloadReEncryptor.reEncryptFields(plain, context);

        assertThat(result).isPresent();

        final JsonObject decrypted = JsonFieldsEncryptor.decrypt(result.get(), "", POINTERS, NEW_KEY);
        assertThat(decrypted).isEqualTo(plain);
    }

    @Test
    public void whenBothKeysFailOnEncryptedDataThrowsException() {
        // Data encrypted with a wrong key (has encrypted_ prefix but neither old nor new key
        // can decrypt it) should throw an error — not silently double-encrypt.
        final JsonObject plain = createPlainSnapshotJson();
        final JsonObject encryptedWithWrongKey = JsonFieldsEncryptor.encrypt(plain, "", POINTERS, WRONG_KEY);

        final MigrationContext context = MigrationContext.forSnapshots(OLD_KEY, NEW_KEY, POINTERS);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> PayloadReEncryptor.reEncryptFields(encryptedWithWrongKey, context))
                .isInstanceOf(org.eclipse.ditto.connectivity.model.ConnectionConfigurationInvalidException.class);
    }

    @Test
    public void initialEncryptionEncryptsPlaintext() {
        // Initial encryption: oldKey is null, newKey is set
        final JsonObject plain = createPlainSnapshotJson();

        final MigrationContext context = MigrationContext.forSnapshots(null, NEW_KEY, POINTERS);
        final Optional<JsonObject> result = PayloadReEncryptor.reEncryptFields(plain, context);

        assertThat(result).isPresent();
        // Verify it can be decrypted with the new key
        final JsonObject decrypted = JsonFieldsEncryptor.decrypt(result.get(), "", POINTERS, NEW_KEY);
        assertThat(decrypted).isEqualTo(plain);
    }

    @Test
    public void initialEncryptionSkipsAlreadyEncrypted() {
        // Initial encryption: oldKey is null, data already encrypted with new key
        final JsonObject plain = createPlainSnapshotJson();
        final JsonObject alreadyEncrypted = JsonFieldsEncryptor.encrypt(plain, "", POINTERS, NEW_KEY);

        final MigrationContext context = MigrationContext.forSnapshots(null, NEW_KEY, POINTERS);
        final Optional<JsonObject> result = PayloadReEncryptor.reEncryptFields(alreadyEncrypted, context);

        // Should skip - already encrypted
        assertThat(result).isEmpty();
    }

    @Test
    public void uriPasswordReEncryptedCorrectly() {
        final JsonObject plain = JsonFactory.newObjectBuilder()
                .set("/uri", "amqps://user:secretpassword@broker.example.com:5671")
                .set("/credentials/password", "mypassword")
                .build();
        final JsonObject encryptedWithOldKey = JsonFieldsEncryptor.encrypt(plain, "", POINTERS, OLD_KEY);

        final String encryptedUri = encryptedWithOldKey.getValue("/uri").get().asString();
        assertThat(encryptedUri).contains("encrypted_");

        final MigrationContext context = MigrationContext.forSnapshots(OLD_KEY, NEW_KEY, POINTERS);
        final Optional<JsonObject> result = PayloadReEncryptor.reEncryptFields(encryptedWithOldKey, context);

        assertThat(result).isPresent();
        final JsonObject decrypted = JsonFieldsEncryptor.decrypt(result.get(), "", POINTERS, NEW_KEY);
        assertThat(decrypted.getValue("/uri").map(v -> v.asString()))
                .hasValue("amqps://user:secretpassword@broker.example.com:5671");
    }

    @Test
    public void initialEncryptionSkipsUriWithAlreadyEncryptedPassword() {
        final JsonObject plain = JsonFactory.newObjectBuilder()
                .set("/uri", "amqps://user:secretpassword@broker.example.com:5671")
                .set("/credentials/password", "mypassword")
                .build();
        final JsonObject encrypted = JsonFieldsEncryptor.encrypt(plain, "", POINTERS, NEW_KEY);

        // Verify the URI has encrypted password embedded (not a direct encrypted_ prefix)
        final String encUri = encrypted.getValue("/uri").get().asString();
        assertThat(encUri).startsWith("amqps://");
        assertThat(encUri).contains("encrypted_");

        // Initial encryption (oldKey=null) should detect the encrypted URI and skip
        final MigrationContext context = MigrationContext.forSnapshots(null, NEW_KEY, POINTERS);
        final Optional<JsonObject> result = PayloadReEncryptor.reEncryptFields(encrypted, context);

        assertThat(result).isEmpty();
    }

    @Test
    public void disableWorkflowSkipsAlreadyPlaintextEntity() {
        final JsonObject plain = createPlainSnapshotJson();

        final MigrationContext context = MigrationContext.forSnapshots(OLD_KEY, null, POINTERS);
        final Optional<JsonObject> result = PayloadReEncryptor.reEncryptFields(plain, context);

        // Should return empty (skip) because decrypt returns unchanged plaintext
        assertThat(result).isEmpty();
    }

    @Test
    public void disableWorkflowProcessesEntityWithEncryptedUriPassword() {
        final JsonObject plain = JsonFactory.newObjectBuilder()
                .set("/uri", "amqps://user:secretpassword@broker.example.com:5671")
                .set("/credentials/password", "mypassword")
                .build();
        final JsonObject encrypted = JsonFieldsEncryptor.encrypt(plain, "", POINTERS, OLD_KEY);

        // Disable workflow (newKey=null) should decrypt and return plaintext
        final MigrationContext context = MigrationContext.forSnapshots(OLD_KEY, null, POINTERS);
        final Optional<JsonObject> result = PayloadReEncryptor.reEncryptFields(encrypted, context);

        assertThat(result).isPresent();
        assertThat(result.get().getValue("/uri").get().asString())
                .isEqualTo("amqps://user:secretpassword@broker.example.com:5671");
        assertThat(result.get().getValue("/credentials/password").get().asString())
                .isEqualTo("mypassword");
    }

    @Test
    public void eventWithNoEncryptableFieldsIsSkipped() {
        // Empty events or events without any of the configured pointers should be skipped
        final JsonObject emptyEvent = JsonFactory.newObjectBuilder()
                .set("/connection/type", "persistence-actor-internal:empty-event")
                .set("/connection/effect", "priorityUpdate")
                .build();

        // Initial encryption
        final MigrationContext initialContext = MigrationContext.forJournal(null, NEW_KEY, POINTERS);
        assertThat(PayloadReEncryptor.reEncryptFields(emptyEvent, initialContext)).isEmpty();

        // Key rotation
        final MigrationContext rotationContext = MigrationContext.forJournal(OLD_KEY, NEW_KEY, POINTERS);
        assertThat(PayloadReEncryptor.reEncryptFields(emptyEvent, rotationContext)).isEmpty();

        // Disable workflow
        final MigrationContext disableContext = MigrationContext.forJournal(OLD_KEY, null, POINTERS);
        assertThat(PayloadReEncryptor.reEncryptFields(emptyEvent, disableContext)).isEmpty();
    }

    @Test
    public void commandSerializationRoundTrip() {
        final var headers = DittoHeaders.empty();
        final var command = MigrateConnectionEncryption.of(true, false, headers);

        final JsonObject json = command.toJson();
        final var deserialized = MigrateConnectionEncryption.fromJson(json, headers);

        assertThat(deserialized.isDryRun()).isTrue();
        assertThat(deserialized.isResume()).isFalse();
        assertThat(deserialized.getType()).isEqualTo(MigrateConnectionEncryption.TYPE);
    }

    @Test
    public void commandDefaultValues() {
        final JsonObject minimalJson = JsonFactory.newObjectBuilder()
                .set("type", MigrateConnectionEncryption.TYPE)
                .build();
        final var headers = DittoHeaders.empty();

        final var command = MigrateConnectionEncryption.fromJson(minimalJson, headers);

        assertThat(command.isDryRun()).isFalse();
        assertThat(command.isResume()).isFalse();
    }

    @Test
    public void abortCommandSerializationRoundTrip() {
        final var headers = DittoHeaders.empty();
        final var command = MigrateConnectionEncryptionAbort.of(headers);

        final JsonObject json = command.toJson();
        final var deserialized = MigrateConnectionEncryptionAbort.fromJson(json, headers);

        assertThat(deserialized.getType()).isEqualTo(MigrateConnectionEncryptionAbort.TYPE);
        assertThat(deserialized.getType()).isEqualTo("connectivity.commands:migrateEncryptionAbort");
    }

    @Test
    public void statusCommandSerializationRoundTrip() {
        final var headers = DittoHeaders.empty();
        final var command = MigrateConnectionEncryptionStatus.of(headers);

        final JsonObject json = command.toJson();
        final var deserialized = MigrateConnectionEncryptionStatus.fromJson(json, headers);

        assertThat(deserialized.getType()).isEqualTo(MigrateConnectionEncryptionStatus.TYPE);
        assertThat(deserialized.getType()).isEqualTo("connectivity.commands:migrateEncryptionStatus");
    }

    @Test
    public void acceptedResponseSerializationRoundTrip() {
        final var headers = DittoHeaders.empty();
        final var response = MigrateConnectionEncryptionResponse.accepted(
                false, "2026-02-16T10:00:00Z", false, headers);

        final JsonObject json = response.toJson();
        final var deserialized = MigrateConnectionEncryptionResponse.fromJson(json, headers);

        assertThat(deserialized.getPhase()).isEqualTo("started");
        assertThat(deserialized.isDryRun()).isFalse();
        assertThat(deserialized.isResumed()).isFalse();
        assertThat(deserialized.getHttpStatus()).isEqualTo(org.eclipse.ditto.base.model.common.HttpStatus.ACCEPTED);
        assertThat(deserialized.getType()).isEqualTo(MigrateConnectionEncryptionResponse.TYPE);
    }

    @Test
    public void dryRunResponseSerializationRoundTrip() {
        final var headers = DittoHeaders.empty();
        final var response = MigrateConnectionEncryptionResponse.dryRunCompleted(
                "completed", false, "2026-02-16T10:00:00Z",
                100, 10, 2, 200, 20, 5, headers);

        final JsonObject json = response.toJson();
        final var deserialized = MigrateConnectionEncryptionResponse.fromJson(json, headers);

        assertThat(deserialized.getPhase()).isEqualTo("completed");
        assertThat(deserialized.isDryRun()).isTrue();
        assertThat(deserialized.isResumed()).isFalse();
        assertThat(deserialized.getHttpStatus()).isEqualTo(org.eclipse.ditto.base.model.common.HttpStatus.OK);
        assertThat(json.getValue("snapshots/processed")).contains(org.eclipse.ditto.json.JsonValue.of(100));
        assertThat(json.getValue("journalEvents/processed")).contains(org.eclipse.ditto.json.JsonValue.of(200));
        assertThat(deserialized.getType()).isEqualTo(MigrateConnectionEncryptionResponse.TYPE);
    }

    @Test
    public void statusResponseSerializationRoundTrip() {
        final var headers = DittoHeaders.empty();
        final var progress = new MigrationProgress(
                MigrationPhase.SNAPSHOTS,
                "507f1f77bcf86cd799439011", "connection:mqtt-prod-sensor-01",
                null, null,
                150, 10, 2,
                0, 0, 0,
                "2026-02-16T10:00:00Z");
        final var response = MigrateConnectionEncryptionStatusResponse.of(
                "in_progress:snapshots", progress, true, true, headers);

        final JsonObject json = response.toJson();
        final var deserialized = MigrateConnectionEncryptionStatusResponse.fromJson(json, headers);

        assertThat(deserialized.getPhase()).isEqualTo("in_progress:snapshots");
        assertThat(deserialized.isMigrationActive()).isTrue();
        assertThat(deserialized.getType()).isEqualTo(MigrateConnectionEncryptionStatusResponse.TYPE);
    }

    @Test
    public void abortResponseSerializationRoundTrip() {
        final var headers = DittoHeaders.empty();
        final var response = MigrateConnectionEncryptionAbortResponse.of(
                "aborted:snapshots",
                150, 10, 2,
                0, 0, 0,
                "2026-02-16T10:35:00Z",
                headers);

        final JsonObject json = response.toJson();
        final var deserialized = MigrateConnectionEncryptionAbortResponse.fromJson(json, headers);

        assertThat(deserialized.getPhase()).isEqualTo("aborted:snapshots");
        assertThat(deserialized.getAbortedAt()).isEqualTo("2026-02-16T10:35:00Z");
        assertThat(deserialized.getType()).isEqualTo(MigrateConnectionEncryptionAbortResponse.TYPE);
    }

    @Test
    public void migrationProgressTracking() {
        final MigrationProgress initial =
                new MigrationProgress();

        final MigrationProgress afterSnapshots = initial
                .incrementSnapshotsProcessed()
                .incrementSnapshotsProcessed()
                .incrementSnapshotsSkipped()
                .incrementSnapshotsFailed();

        assertThat(afterSnapshots.snapshotsProcessed()).isEqualTo(2);
        assertThat(afterSnapshots.snapshotsSkipped()).isEqualTo(1);
        assertThat(afterSnapshots.snapshotsFailed()).isEqualTo(1);
        // Original should be unchanged (immutable)
        assertThat(initial.snapshotsProcessed()).isEqualTo(0);

        final MigrationProgress afterJournal = afterSnapshots
                .withPhase(MigrationPhase.JOURNAL)
                .incrementJournalProcessed()
                .incrementJournalSkipped();

        assertThat(afterJournal.phase()).isEqualTo(MigrationPhase.JOURNAL);
        assertThat(afterJournal.journalProcessed()).isEqualTo(1);
        assertThat(afterJournal.journalSkipped()).isEqualTo(1);
        // Snapshot counts should be preserved
        assertThat(afterJournal.snapshotsProcessed()).isEqualTo(2);
    }

    @Test
    public void bulkWriteFailureAdjustsSnapshotCounters() {
        final MigrationProgress progress =
                new MigrationProgress()
                        .incrementSnapshotsProcessed()
                        .incrementSnapshotsProcessed()
                        .incrementSnapshotsProcessed();

        assertThat(progress.snapshotsProcessed()).isEqualTo(3);
        assertThat(progress.snapshotsFailed()).isEqualTo(0);

        final MigrationProgress adjusted =
                progress.adjustForBulkWriteFailure(2, true);

        assertThat(adjusted.snapshotsProcessed()).isEqualTo(1);
        assertThat(adjusted.snapshotsFailed()).isEqualTo(2);
        // Journal counters should be unaffected
        assertThat(adjusted.journalProcessed()).isEqualTo(0);
        assertThat(adjusted.journalFailed()).isEqualTo(0);
    }

    @Test
    public void bulkWriteFailureAdjustsJournalCounters() {
        final MigrationProgress progress =
                new MigrationProgress()
                        .withPhase(MigrationPhase.JOURNAL)
                        .incrementJournalProcessed()
                        .incrementJournalProcessed();

        final MigrationProgress adjusted =
                progress.adjustForBulkWriteFailure(2, false);

        assertThat(adjusted.journalProcessed()).isEqualTo(0);
        assertThat(adjusted.journalFailed()).isEqualTo(2);
        // Snapshot counters should be unaffected
        assertThat(adjusted.snapshotsProcessed()).isEqualTo(0);
    }

    @Test
    public void bulkWriteFailureDoesNotGoNegative() {
        final MigrationProgress progress =
                new MigrationProgress()
                        .incrementSnapshotsProcessed();

        // More failures than processed: should clamp to 0
        final MigrationProgress adjusted =
                progress.adjustForBulkWriteFailure(5, true);

        assertThat(adjusted.snapshotsProcessed()).isEqualTo(0);
        assertThat(adjusted.snapshotsFailed()).isEqualTo(5);
    }

    // --- Actor behaviour on an in-memory payload store ---

    @Test
    public void payloadStoreIsObtainedForTheConnectionEntityTypeWhenTheActorStarts() {
        final AtomicReference<String> requestedEntityType = new AtomicReference<>();
        final InMemoryPersistedPayloadStore store = new InMemoryPersistedPayloadStore();

        actorSystem.actorOf(EncryptionMigrationActor.props(connectivityConfig, providerOf(store, requestedEntityType)));

        Awaitility.await().atMost(AWAIT).until(() -> "connection".equals(requestedEntityType.get()));
    }

    @Test
    public void migrationIsAcceptedBeforeAnyStoreIoCompletes() {
        new TestKit(actorSystem) {{
            final CompletableFuture<Void> deleteGate = new CompletableFuture<>();
            final InMemoryPersistedPayloadStore store =
                    new InMemoryPersistedPayloadStore().holdingDeleteProgressUntil(deleteGate);
            final ActorRef underTest = newActor(store);

            underTest.tell(MigrateConnectionEncryption.of(false, false, DittoHeaders.empty()), getRef());

            final MigrateConnectionEncryptionResponse accepted =
                    expectMsgClass(Duration.ofSeconds(3), MigrateConnectionEncryptionResponse.class);
            assertThat(accepted.getHttpStatus()).isEqualTo(HttpStatus.ACCEPTED);
            assertThat(accepted.getPhase()).isEqualTo("started");
            assertThat(accepted.isDryRun()).isFalse();
            assertThat(accepted.isResumed()).isFalse();
            // the only store call so far is the (still pending) delete of a previous run's progress
            assertThat(store.deleteProgressCalls.get()).isEqualTo(1);
            assertThat(store.snapshotStreamCalls).isEmpty();
            assertThat(store.journalStreamCalls).isEmpty();
            assertThat(store.rewrittenSnapshots).isEmpty();
            assertThat(store.savedProgressRecords).isEmpty();

            // a second migration is refused while the first one runs
            underTest.tell(MigrateConnectionEncryption.of(false, false, DittoHeaders.empty()), getRef());
            final Status.Failure refused = expectMsgClass(Status.Failure.class);
            assertThat(refused.cause()).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already in progress");

            deleteGate.complete(null);
            final MigrateConnectionEncryptionStatusResponse completed = awaitCompleted(this, underTest);
            assertThat(store.snapshotStreamCalls).hasSize(1);
            assertThat(store.journalStreamCalls).hasSize(1);
            assertThat(counter(completed, "snapshots/processed")).isZero();
            assertThat(counter(completed, "journalEvents/processed")).isZero();
        }};
    }

    @Test
    public void dryRunRewritesNothingAndSavesNoProgress() {
        new TestKit(actorSystem) {{
            final InMemoryPersistedPayloadStore store = new InMemoryPersistedPayloadStore()
                    .withSnapshots(plainSnapshot("s-1"))
                    .withJournalEntries(plainJournalEntry("j-1"));
            final ActorRef underTest = newActor(store);

            underTest.tell(MigrateConnectionEncryption.of(true, false, DittoHeaders.empty()), getRef());

            final MigrateConnectionEncryptionResponse accepted =
                    expectMsgClass(MigrateConnectionEncryptionResponse.class);
            assertThat(accepted.getHttpStatus()).isEqualTo(HttpStatus.ACCEPTED);
            assertThat(accepted.isDryRun()).isTrue();

            final MigrateConnectionEncryptionStatusResponse completed = awaitCompleted(this, underTest);
            assertThat(counter(completed, "snapshots/processed")).isEqualTo(1L);
            assertThat(counter(completed, "journalEvents/processed")).isEqualTo(1L);
            assertThat(completed.toJson().getValue("dryRun").map(JsonValue::asBoolean)).contains(true);
            // the entries were read (and would have been re-encrypted), but nothing was written back or saved
            assertThat(store.snapshotStreamCalls).containsExactly(new StreamCall(null, POINTERS, 1));
            assertThat(store.journalStreamCalls).containsExactly(new StreamCall(null, JOURNAL_POINTERS, 1));
            assertThat(store.rewrittenSnapshots).isEmpty();
            assertThat(store.rewrittenJournalEntries).isEmpty();
            assertThat(store.savedProgressRecords).isEmpty();
            // a fresh start clears a previous run's progress, dry run or not (as before)
            assertThat(store.deleteProgressCalls.get()).isEqualTo(1);
        }};
    }

    @Test
    public void freshStartDeletesTheOldProgressRewritesTheEntriesAndSavesProgressPerBatchAndAtTheEnd() {
        new TestKit(actorSystem) {{
            final InMemoryPersistedPayloadStore store = new InMemoryPersistedPayloadStore()
                    .withSavedProgress(progressJson("journal", "s-old", "j-old", 7, 3))
                    .withSnapshots(plainSnapshot("s-1"))
                    .withJournalEntries(plainJournalEntry("j-1"));
            final ActorRef underTest = newActor(store);

            underTest.tell(MigrateConnectionEncryption.of(false, false, DittoHeaders.empty()), getRef());
            expectMsgClass(MigrateConnectionEncryptionResponse.class);

            final MigrateConnectionEncryptionStatusResponse completed = awaitCompleted(this, underTest);
            // the old progress was deleted first: the run started from the beginning and counted from zero
            assertThat(store.deleteProgressCalls.get()).isEqualTo(1);
            assertThat(store.snapshotStreamCalls).containsExactly(new StreamCall(null, POINTERS, 1));
            assertThat(store.journalStreamCalls).containsExactly(new StreamCall(null, JOURNAL_POINTERS, 1));
            assertThat(counter(completed, "snapshots/processed")).isEqualTo(1L);
            assertThat(counter(completed, "journalEvents/processed")).isEqualTo(1L);

            // the rewritten entries carry the payloads encrypted with the configured key
            assertThat(store.rewrittenSnapshots).hasSize(1);
            final PersistedSnapshot rewrittenSnapshot = store.rewrittenSnapshots.get(0).get(0);
            assertThat(rewrittenSnapshot.cursor()).isEqualTo("s-1");
            assertThat(JsonFieldsEncryptor.decrypt(rewrittenSnapshot.payload(), "", POINTERS, NEW_KEY))
                    .isEqualTo(createPlainSnapshotJson());
            assertThat(store.rewrittenJournalEntries).hasSize(1);
            final List<JsonObject> rewrittenPayloads = store.rewrittenJournalEntries.get(0).get(0).eventPayloads();
            assertThat(rewrittenPayloads).hasSize(1);
            assertThat(JsonFieldsEncryptor.decrypt(rewrittenPayloads.get(0), "connection", POINTERS, NEW_KEY))
                    .isEqualTo(createPlainJournalJson());

            // progress saved after the snapshot batch, after the journal batch and at the end, in the stored shape
            assertThat(store.savedProgressRecords).hasSize(3);
            assertThat(store.savedProgressRecords.stream().map(record -> string(record, "phase")).toList())
                    .containsExactly("snapshots", "journal", "completed");
            final JsonObject finalRecord = store.savedProgressRecords.get(2);
            assertThat(finalRecord.stream().map(JsonField::getKeyName).toList()).isEqualTo(PROGRESS_FIELD_NAMES);
            assertThat(string(finalRecord, "lastProcessedSnapshotId")).isEqualTo("s-1");
            assertThat(string(finalRecord, "lastProcessedSnapshotPid")).isEqualTo("connection:s-1");
            assertThat(string(finalRecord, "lastProcessedJournalId")).isEqualTo("j-1");
            assertThat(finalRecord.getValue("snapshotsProcessed").map(JsonValue::asLong)).contains(1L);
            assertThat(finalRecord.getValue("journalProcessed").map(JsonValue::asLong)).contains(1L);
            assertThat(store.savedProgress()).contains(finalRecord);
        }};
    }

    @Test
    public void resumeInTheJournalPhaseSkipsTheSnapshotStreamAndContinuesBehindTheLastJournalEntry() {
        new TestKit(actorSystem) {{
            final InMemoryPersistedPayloadStore store = new InMemoryPersistedPayloadStore()
                    .withSavedProgress(progressJson("journal", "s-9", "j-1", 5, 2))
                    .withSnapshots(plainSnapshot("s-10"))
                    .withJournalEntries(plainJournalEntry("j-2"));
            final ActorRef underTest = newActor(store);

            underTest.tell(MigrateConnectionEncryption.of(false, true, DittoHeaders.empty()), getRef());

            final MigrateConnectionEncryptionResponse accepted =
                    expectMsgClass(MigrateConnectionEncryptionResponse.class);
            assertThat(accepted.getHttpStatus()).isEqualTo(HttpStatus.ACCEPTED);
            assertThat(accepted.isResumed()).isTrue();

            final MigrateConnectionEncryptionStatusResponse completed = awaitCompleted(this, underTest);
            assertThat(store.deleteProgressCalls.get()).isZero();
            assertThat(store.snapshotStreamCalls).isEmpty();
            assertThat(store.journalStreamCalls).containsExactly(new StreamCall("j-1", JOURNAL_POINTERS, 1));
            // the counters continue from the saved progress
            assertThat(counter(completed, "snapshots/processed")).isEqualTo(5L);
            assertThat(counter(completed, "journalEvents/processed")).isEqualTo(3L);
            assertThat(store.rewrittenSnapshots).isEmpty();
            assertThat(store.rewrittenJournalEntries).hasSize(1);
        }};
    }

    @Test
    public void resumeWithoutSavedProgressAnswersAlreadyCompletedAndTouchesNothing() {
        new TestKit(actorSystem) {{
            final InMemoryPersistedPayloadStore store = new InMemoryPersistedPayloadStore()
                    .withSnapshots(plainSnapshot("s-1"));
            final ActorRef underTest = newActor(store);

            underTest.tell(MigrateConnectionEncryption.of(false, true, DittoHeaders.empty()), getRef());

            final MigrateConnectionEncryptionResponse response =
                    expectMsgClass(MigrateConnectionEncryptionResponse.class);
            assertThat(response.getPhase()).isEqualTo("already_completed");
            assertThat(response.getHttpStatus()).isEqualTo(HttpStatus.OK);

            awaitCompleted(this, underTest);
            assertThat(store.snapshotStreamCalls).isEmpty();
            assertThat(store.journalStreamCalls).isEmpty();
            assertThat(store.deleteProgressCalls.get()).isZero();
            assertThat(store.savedProgressRecords).isEmpty();
        }};
    }

    @Test
    public void abortStopsTheRunningMigrationAndSavesItsProgress() {
        new TestKit(actorSystem) {{
            // one snapshot, then a stream that never ends: the run hangs in the snapshot phase after one batch
            final InMemoryPersistedPayloadStore store = new InMemoryPersistedPayloadStore()
                    .withSnapshotSource(Source.<PersistedSnapshot>single(plainSnapshot("s-1"))
                            .concat(Source.never()));
            final ActorRef underTest = newActor(store);

            underTest.tell(MigrateConnectionEncryption.of(false, false, DittoHeaders.empty()), getRef());
            expectMsgClass(MigrateConnectionEncryptionResponse.class);

            // status during the run comes from memory once the first batch is through
            final MigrateConnectionEncryptionStatusResponse running = awaitStatus(this, underTest,
                    status -> status.isMigrationActive() && "in_progress:snapshots".equals(status.getPhase()));
            assertThat(counter(running, "snapshots/processed")).isEqualTo(1L);

            underTest.tell(MigrateConnectionEncryptionAbort.of(DittoHeaders.empty()), getRef());
            final MigrateConnectionEncryptionAbortResponse aborted =
                    expectMsgClass(MigrateConnectionEncryptionAbortResponse.class);
            assertThat(aborted.getPhase()).isEqualTo("aborted:snapshots");
            assertThat(aborted.getAbortedAt()).isNotEmpty();

            // the run ends in the phase it was aborted in; the status says so from memory
            final MigrateConnectionEncryptionStatusResponse afterAbort =
                    awaitStatus(this, underTest, status -> !status.isMigrationActive());
            assertThat(afterAbort.getPhase()).isEqualTo("snapshots");
            assertThat(counter(afterAbort, "snapshots/processed")).isEqualTo(1L);
            assertThat(store.journalStreamCalls).isEmpty();

            // the progress was saved after the batch and again on the abort, with the plain phase (as before)
            Awaitility.await().atMost(AWAIT).until(() -> store.savedProgressRecords.size() == 2);
            final JsonObject abortRecord = store.savedProgressRecords.get(1);
            assertThat(string(abortRecord, "phase")).isEqualTo("snapshots");
            assertThat(string(abortRecord, "lastProcessedSnapshotId")).isEqualTo("s-1");
            assertThat(abortRecord.getValue("snapshotsProcessed").map(JsonValue::asLong)).contains(1L);

            // nothing runs any more, so another abort is refused
            underTest.tell(MigrateConnectionEncryptionAbort.of(DittoHeaders.empty()), getRef());
            final Status.Failure refused = expectMsgClass(Status.Failure.class);
            assertThat(refused.cause()).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("No migration is currently running");
        }};
    }

    @Test
    public void statusIsAnsweredFromTheStoreWhenNothingRanYet() {
        new TestKit(actorSystem) {{
            final ActorRef withSavedProgress = newActor(new InMemoryPersistedPayloadStore()
                    .withSavedProgress(progressJson("completed", "s-150", "j-200", 150, 200)));

            withSavedProgress.tell(MigrateConnectionEncryptionStatus.of(DittoHeaders.empty()), getRef());
            final MigrateConnectionEncryptionStatusResponse saved =
                    expectMsgClass(MigrateConnectionEncryptionStatusResponse.class);
            assertThat(saved.getPhase()).isEqualTo("completed");
            assertThat(saved.isMigrationActive()).isFalse();
            assertThat(counter(saved, "snapshots/processed")).isEqualTo(150L);
            assertThat(counter(saved, "journalEvents/processed")).isEqualTo(200L);

            final ActorRef withoutSavedProgress = newActor(new InMemoryPersistedPayloadStore());

            withoutSavedProgress.tell(MigrateConnectionEncryptionStatus.of(DittoHeaders.empty()), getRef());
            final MigrateConnectionEncryptionStatusResponse fresh =
                    expectMsgClass(MigrateConnectionEncryptionStatusResponse.class);
            assertThat(fresh.getPhase()).isEqualTo("snapshots");
            assertThat(fresh.isMigrationActive()).isFalse();
            assertThat(counter(fresh, "snapshots/processed")).isZero();
        }};
    }

    @Test
    public void statusFailsWhenTheStoreCannotBeRead() {
        new TestKit(actorSystem) {{
            final ActorRef underTest = newActor(new InMemoryPersistedPayloadStore().failingProgressOperations());

            underTest.tell(MigrateConnectionEncryptionStatus.of(DittoHeaders.empty()), getRef());

            final Status.Failure failure = expectMsgClass(Status.Failure.class);
            assertThat(failure.cause()).hasMessageContaining("progress unavailable");
        }};
    }

    @Test
    public void failedRewriteMovesTheProcessedCountToFailed() {
        new TestKit(actorSystem) {{
            final InMemoryPersistedPayloadStore store = new InMemoryPersistedPayloadStore()
                    .failingRewrites()
                    .withSnapshots(plainSnapshot("s-1"));
            final ActorRef underTest = newActor(store);

            underTest.tell(MigrateConnectionEncryption.of(false, false, DittoHeaders.empty()), getRef());
            expectMsgClass(MigrateConnectionEncryptionResponse.class);

            final MigrateConnectionEncryptionStatusResponse completed = awaitCompleted(this, underTest);
            assertThat(store.rewrittenSnapshots).hasSize(1);
            assertThat(counter(completed, "snapshots/processed")).isZero();
            assertThat(counter(completed, "snapshots/failed")).isEqualTo(1L);
            assertThat(counter(completed, "snapshots/skipped")).isZero();
            // the progress is saved nonetheless, with the adjusted counters
            final JsonObject finalRecord = store.savedProgress().orElseThrow();
            assertThat(string(finalRecord, "phase")).isEqualTo("completed");
            assertThat(finalRecord.getValue("snapshotsProcessed").map(JsonValue::asLong)).contains(0L);
            assertThat(finalRecord.getValue("snapshotsFailed").map(JsonValue::asLong)).contains(1L);
        }};
    }

    @Test
    public void storeIsClosedWhenTheActorStops() {
        new TestKit(actorSystem) {{
            final InMemoryPersistedPayloadStore store = new InMemoryPersistedPayloadStore();
            final ActorRef underTest = newActor(store);
            watch(underTest);
            // make sure the actor has started (and so obtained the store) before stopping it
            underTest.tell(MigrateConnectionEncryptionStatus.of(DittoHeaders.empty()), getRef());
            expectMsgClass(MigrateConnectionEncryptionStatusResponse.class);
            assertThat(store.closed).isFalse();

            actorSystem.stop(underTest);

            expectTerminated(underTest);
            assertThat(store.closed).isTrue();
        }};
    }

    // --- Helpers for the actor tests ---

    private static ActorRef newActor(final PersistedPayloadStore store) {
        return actorSystem.actorOf(
                EncryptionMigrationActor.props(connectivityConfig, providerOf(store, new AtomicReference<>())));
    }

    /** A provider that implements only what the actor needs: {@code payloadStore(entityType)}. */
    private static PersistenceBackendProvider providerOf(final PersistedPayloadStore store,
            final AtomicReference<String> requestedEntityType) {
        return new PersistenceBackendProvider() {

            @Override
            public String getJournalPluginId(final String entityType) {
                return "stub-journal";
            }

            @Override
            public String getSnapshotPluginId(final String entityType) {
                return "stub-snapshots";
            }

            @Override
            public DittoReadJournal getReadJournal() {
                throw new UnsupportedOperationException("not needed by the encryption migration");
            }

            @Override
            public PersistedPayloadStore payloadStore(final String entityType) {
                requestedEntityType.set(entityType);
                return store;
            }
        };
    }

    private static MigrateConnectionEncryptionStatusResponse awaitCompleted(final TestKit testKit,
            final ActorRef actor) {
        return awaitStatus(testKit, actor, status -> !status.isMigrationActive() &&
                "completed".equals(status.getPhase()));
    }

    /** Polls the status until it matches; a status before the first batch comes from the store, not from memory. */
    private static MigrateConnectionEncryptionStatusResponse awaitStatus(final TestKit testKit, final ActorRef actor,
            final Predicate<MigrateConnectionEncryptionStatusResponse> condition) {
        final Instant deadline = Instant.now().plus(AWAIT);
        MigrateConnectionEncryptionStatusResponse last;
        do {
            actor.tell(MigrateConnectionEncryptionStatus.of(DittoHeaders.empty()), testKit.getRef());
            last = testKit.expectMsgClass(Duration.ofSeconds(5), MigrateConnectionEncryptionStatusResponse.class);
            if (condition.test(last)) {
                return last;
            }
            try {
                Thread.sleep(20);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("interrupted while waiting for the migration status", e);
            }
        } while (Instant.now().isBefore(deadline));
        throw new AssertionError("The migration status did not reach the expected state in time; last: " +
                last.toJson());
    }

    private static long counter(final MigrateConnectionEncryptionStatusResponse status, final String path) {
        return status.toJson().getValue(path).map(JsonValue::asLong).orElseThrow(
                () -> new AssertionError("no <" + path + "> in " + status.toJson()));
    }

    private static String string(final JsonObject json, final String key) {
        return json.getValue(key).filter(JsonValue::isString).map(JsonValue::asString).orElseThrow(
                () -> new AssertionError("no string <" + key + "> in " + json));
    }

    private static Snapshot plainSnapshot(final String cursor) {
        return new Snapshot(cursor, "connection:" + cursor, createPlainSnapshotJson());
    }

    private static JournalEntry plainJournalEntry(final String cursor) {
        return new JournalEntry(cursor, "connection:" + cursor, List.of(createPlainJournalJson()));
    }

    /** A stored progress record as the migration saves it. */
    private static JsonObject progressJson(final String phase, final String lastSnapshotId,
            final String lastJournalId, final long snapshotsProcessed, final long journalProcessed) {
        return JsonFactory.newObjectBuilder()
                .set("phase", phase)
                .set("lastProcessedSnapshotId", lastSnapshotId)
                .set("lastProcessedSnapshotPid", "connection:" + lastSnapshotId)
                .set("lastProcessedJournalId", lastJournalId)
                .set("lastProcessedJournalPid", "connection:" + lastJournalId)
                .set("snapshotsProcessed", snapshotsProcessed)
                .set("snapshotsSkipped", 0L)
                .set("snapshotsFailed", 0L)
                .set("journalProcessed", journalProcessed)
                .set("journalSkipped", 0L)
                .set("journalFailed", 0L)
                .set("startedAt", "2026-02-16T10:00:00Z")
                .set("updatedAt", "2026-02-16T10:05:00Z")
                .build();
    }

    private static JsonObject createPlainSnapshotJson() {
        return JsonFactory.newObjectBuilder()
                .set("/uri", "amqps://user:password123@broker.example.com:5671")
                .set("/credentials/password", "secretPassword")
                .set("/name", "test-connection")
                .build();
    }

    private static JsonObject createPlainJournalJson() {
        return JsonFactory.newObjectBuilder()
                .set("/connection/uri", "amqps://user:password123@broker.example.com:5671")
                .set("/connection/credentials/password", "secretPassword")
                .set("/connection/name", "test-connection")
                .build();
    }

}
