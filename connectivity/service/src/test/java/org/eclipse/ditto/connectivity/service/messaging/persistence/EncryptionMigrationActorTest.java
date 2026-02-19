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

import static org.assertj.core.api.Assertions.assertThat;

import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Optional;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.service.messaging.persistence.migration.DocumentProcessor;
import org.eclipse.ditto.connectivity.service.messaging.persistence.migration.MigrationContext;
import org.eclipse.ditto.connectivity.service.util.EncryptorAesGcm;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests for the encryption migration logic in {@link EncryptionMigrationActor}.
 * <p>
 * Tests the core re-encryption logic (snapshot and journal field re-encryption),
 * command/response serialization, and validation behavior.
 */
public final class EncryptionMigrationActorTest {

    private static String OLD_KEY;
    private static String NEW_KEY;
    private static String WRONG_KEY;

    private static final List<String> POINTERS = List.of(
            "/uri",
            "/credentials/password"
    );

    @BeforeClass
    public static void initKeys() throws NoSuchAlgorithmException {
        OLD_KEY = EncryptorAesGcm.generateAESKeyAsString();
        NEW_KEY = EncryptorAesGcm.generateAESKeyAsString();
        WRONG_KEY = EncryptorAesGcm.generateAESKeyAsString();
    }

    // --- Re-encryption logic tests ---

    @Test
    public void reEncryptSnapshotFieldsFromOldKeyToNewKey() {
        final JsonObject plain = createPlainSnapshotJson();
        final JsonObject encryptedWithOldKey = JsonFieldsEncryptor.encrypt(plain, "", POINTERS, OLD_KEY);

        final MigrationContext context = MigrationContext.forSnapshots(OLD_KEY, NEW_KEY, POINTERS);
        final Optional<JsonObject> result = DocumentProcessor.reEncryptFields(encryptedWithOldKey, context);

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
        final Optional<JsonObject> result = DocumentProcessor.reEncryptFields(encryptedWithOldKey, context);

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
        final Optional<JsonObject> result = DocumentProcessor.reEncryptFields(encryptedWithNewKey, context);

        assertThat(result).isEmpty();
    }

    @Test
    public void keyRotationEncryptsPlaintextFieldsWithNewKey() {
        // During key rotation, plaintext fields (no encrypted_ prefix) are treated as plaintext:
        // decrypt passes them through unchanged, then encrypt wraps them with the new key.
        final JsonObject plain = createPlainSnapshotJson();

        final MigrationContext context = MigrationContext.forSnapshots(OLD_KEY, NEW_KEY, POINTERS);
        final Optional<JsonObject> result = DocumentProcessor.reEncryptFields(plain, context);

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
                () -> DocumentProcessor.reEncryptFields(encryptedWithWrongKey, context))
                .isInstanceOf(org.eclipse.ditto.connectivity.model.ConnectionConfigurationInvalidException.class);
    }

    @Test
    public void initialEncryptionEncryptsPlaintext() {
        // Initial encryption: oldKey is null, newKey is set
        final JsonObject plain = createPlainSnapshotJson();

        final MigrationContext context = MigrationContext.forSnapshots(null, NEW_KEY, POINTERS);
        final Optional<JsonObject> result = DocumentProcessor.reEncryptFields(plain, context);

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
        final Optional<JsonObject> result = DocumentProcessor.reEncryptFields(alreadyEncrypted, context);

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
        final Optional<JsonObject> result = DocumentProcessor.reEncryptFields(encryptedWithOldKey, context);

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
        final Optional<JsonObject> result = DocumentProcessor.reEncryptFields(encrypted, context);

        assertThat(result).isEmpty();
    }

    @Test
    public void disableWorkflowSkipsAlreadyPlaintextEntity() {
        final JsonObject plain = createPlainSnapshotJson();

        final MigrationContext context = MigrationContext.forSnapshots(OLD_KEY, null, POINTERS);
        final Optional<JsonObject> result = DocumentProcessor.reEncryptFields(plain, context);

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
        final Optional<JsonObject> result = DocumentProcessor.reEncryptFields(encrypted, context);

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
        assertThat(DocumentProcessor.reEncryptFields(emptyEvent, initialContext)).isEmpty();

        // Key rotation
        final MigrationContext rotationContext = MigrationContext.forJournal(OLD_KEY, NEW_KEY, POINTERS);
        assertThat(DocumentProcessor.reEncryptFields(emptyEvent, rotationContext)).isEmpty();

        // Disable workflow
        final MigrationContext disableContext = MigrationContext.forJournal(OLD_KEY, null, POINTERS);
        assertThat(DocumentProcessor.reEncryptFields(emptyEvent, disableContext)).isEmpty();
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
                "in_progress:snapshots",
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
                .withPhase("journal")
                .incrementJournalProcessed()
                .incrementJournalSkipped();

        assertThat(afterJournal.phase()).isEqualTo("journal");
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
                        .withPhase("journal")
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
