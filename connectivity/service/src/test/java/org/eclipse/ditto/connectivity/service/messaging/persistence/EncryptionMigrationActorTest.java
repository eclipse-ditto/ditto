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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.NoSuchAlgorithmException;
import java.util.List;

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

        final JsonObject result = EncryptionMigrationActor.reEncryptFields(
                encryptedWithOldKey, "", POINTERS, OLD_KEY, NEW_KEY);

        assertThat(result).isNotNull();
        final JsonObject decryptedWithNewKey = JsonFieldsEncryptor.decrypt(result, "", POINTERS, NEW_KEY);
        assertThat(decryptedWithNewKey.getValue("/credentials/password"))
                .contains(plain.getValue("/credentials/password").get());
    }

    @Test
    public void reEncryptJournalFieldsFromOldKeyToNewKey() {
        final JsonObject plain = createPlainJournalJson();
        final JsonObject encryptedWithOldKey = JsonFieldsEncryptor.encrypt(
                plain, "connection", POINTERS, OLD_KEY);

        final JsonObject result = EncryptionMigrationActor.reEncryptFields(
                encryptedWithOldKey, "connection", POINTERS, OLD_KEY, NEW_KEY);

        assertThat(result).isNotNull();
        final JsonObject decrypted = JsonFieldsEncryptor.decrypt(result, "connection", POINTERS, NEW_KEY);
        assertThat(decrypted.getValue("/connection/credentials/password"))
                .contains(plain.getValue("/connection/credentials/password").get());
    }

    @Test
    public void skipAlreadyMigratedDocument() {
        final JsonObject plain = createPlainSnapshotJson();
        final JsonObject encryptedWithNewKey = JsonFieldsEncryptor.encrypt(plain, "", POINTERS, NEW_KEY);

        final JsonObject result = EncryptionMigrationActor.reEncryptFields(
                encryptedWithNewKey, "", POINTERS, OLD_KEY, NEW_KEY);

        assertThat(result).isNull();
    }

    @Test
    public void whenBothKeysFailTreatsAsPlaintextAndEncrypts() {
        // When neither old nor new key can decrypt, the data is treated as plaintext
        // and encrypted with the new key. This handles the case where data was stored
        // before encryption was enabled.
        final JsonObject plain = createPlainSnapshotJson();

        final JsonObject result = EncryptionMigrationActor.reEncryptFields(
                plain, "", POINTERS, OLD_KEY, NEW_KEY);

        // Should encrypt the plaintext data with new key
        assertThat(result).isNotNull();

        // Verify it can be decrypted with the new key
        final JsonObject decrypted = JsonFieldsEncryptor.decrypt(result, "", POINTERS, NEW_KEY);
        assertThat(decrypted).isEqualTo(plain);
    }

    @Test
    public void initialEncryptionEncryptsPlaintext() {
        // Initial encryption: oldKey is null, newKey is set
        final JsonObject plain = createPlainSnapshotJson();

        final JsonObject result = EncryptionMigrationActor.reEncryptFields(
                plain, "", POINTERS, null, NEW_KEY);

        assertThat(result).isNotNull();
        // Verify it can be decrypted with the new key
        final JsonObject decrypted = JsonFieldsEncryptor.decrypt(result, "", POINTERS, NEW_KEY);
        assertThat(decrypted).isEqualTo(plain);
    }

    @Test
    public void initialEncryptionSkipsAlreadyEncrypted() {
        // Initial encryption: oldKey is null, data already encrypted with new key
        final JsonObject plain = createPlainSnapshotJson();
        final JsonObject alreadyEncrypted = JsonFieldsEncryptor.encrypt(plain, "", POINTERS, NEW_KEY);

        final JsonObject result = EncryptionMigrationActor.reEncryptFields(
                alreadyEncrypted, "", POINTERS, null, NEW_KEY);

        // Should skip - already encrypted
        assertThat(result).isNull();
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

        final JsonObject result = EncryptionMigrationActor.reEncryptFields(
                encryptedWithOldKey, "", POINTERS, OLD_KEY, NEW_KEY);

        assertThat(result).isNotNull();
        final JsonObject decrypted = JsonFieldsEncryptor.decrypt(result, "", POINTERS, NEW_KEY);
        assertThat(decrypted.getValue("/uri").map(v -> v.asString()))
                .hasValue("amqps://user:secretpassword@broker.example.com:5671");
    }

    @Test
    public void plainTextFieldsNotAffected() {
        final JsonObject plain = createPlainSnapshotJson();

        final JsonObject result = EncryptionMigrationActor.reEncryptFields(
                plain, "", POINTERS, OLD_KEY, NEW_KEY);

        assertThat(result).isNotNull();
        final String encryptedPwd = result.getValue("/credentials/password").get().asString();
        assertThat(encryptedPwd).startsWith("encrypted_");
    }

    @Test
    public void initialEncryptionSkipsUriWithAlreadyEncryptedPassword() {
        // Bug 1: URI fields like amqps://user:encrypted_XXX@host were not detected
        // as already encrypted because startsWith("encrypted_") checks the full URI string
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
        final JsonObject result = EncryptionMigrationActor.reEncryptFields(
                encrypted, "", POINTERS, null, NEW_KEY);

        assertThat(result).isNull();
    }

    @Test
    public void disableWorkflowSkipsAlreadyPlaintextEntity() {
        // Bug 2: decrypt() silently passes through plaintext, so disable workflow
        // was counting plaintext entities as "processed" instead of "skipped"
        final JsonObject plain = createPlainSnapshotJson();

        final JsonObject result = EncryptionMigrationActor.reEncryptFields(
                plain, "", POINTERS, OLD_KEY, null);

        // Should return null (skip) because decrypt returns unchanged plaintext
        assertThat(result).isNull();
    }

    @Test
    public void disableWorkflowProcessesEntityWithEncryptedUriPassword() {
        final JsonObject plain = JsonFactory.newObjectBuilder()
                .set("/uri", "amqps://user:secretpassword@broker.example.com:5671")
                .set("/credentials/password", "mypassword")
                .build();
        final JsonObject encrypted = JsonFieldsEncryptor.encrypt(plain, "", POINTERS, OLD_KEY);

        // Disable workflow (newKey=null) should decrypt and return plaintext
        final JsonObject result = EncryptionMigrationActor.reEncryptFields(
                encrypted, "", POINTERS, OLD_KEY, null);

        assertThat(result).isNotNull();
        assertThat(result.getValue("/uri").get().asString())
                .isEqualTo("amqps://user:secretpassword@broker.example.com:5671");
        assertThat(result.getValue("/credentials/password").get().asString())
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
        assertThat(EncryptionMigrationActor.reEncryptFields(
                emptyEvent, "connection", POINTERS, null, NEW_KEY)).isNull();

        // Key rotation
        assertThat(EncryptionMigrationActor.reEncryptFields(
                emptyEvent, "connection", POINTERS, OLD_KEY, NEW_KEY)).isNull();

        // Disable workflow
        assertThat(EncryptionMigrationActor.reEncryptFields(
                emptyEvent, "connection", POINTERS, OLD_KEY, null)).isNull();
    }

    // --- MigrateConnectionEncryption command tests ---

    @Test
    public void commandSerializationRoundTrip() {
        final var headers = org.eclipse.ditto.base.model.headers.DittoHeaders.empty();
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
        final var headers = org.eclipse.ditto.base.model.headers.DittoHeaders.empty();

        final var command = MigrateConnectionEncryption.fromJson(minimalJson, headers);

        assertThat(command.isDryRun()).isFalse();
        assertThat(command.isResume()).isFalse();
    }

    // --- MigrateConnectionEncryptionAbort command tests ---

    @Test
    public void abortCommandSerializationRoundTrip() {
        final var headers = org.eclipse.ditto.base.model.headers.DittoHeaders.empty();
        final var command = MigrateConnectionEncryptionAbort.of(headers);

        final JsonObject json = command.toJson();
        final var deserialized = MigrateConnectionEncryptionAbort.fromJson(json, headers);

        assertThat(deserialized.getType()).isEqualTo(MigrateConnectionEncryptionAbort.TYPE);
        assertThat(deserialized.getType()).isEqualTo("connectivity.commands:migrateEncryptionAbort");
    }

    // --- MigrateConnectionEncryptionStatus command tests ---

    @Test
    public void statusCommandSerializationRoundTrip() {
        final var headers = org.eclipse.ditto.base.model.headers.DittoHeaders.empty();
        final var command = MigrateConnectionEncryptionStatus.of(headers);

        final JsonObject json = command.toJson();
        final var deserialized = MigrateConnectionEncryptionStatus.fromJson(json, headers);

        assertThat(deserialized.getType()).isEqualTo(MigrateConnectionEncryptionStatus.TYPE);
        assertThat(deserialized.getType()).isEqualTo("connectivity.commands:migrateEncryptionStatus");
    }

    // --- Response tests ---

    @Test
    public void acceptedResponseSerializationRoundTrip() {
        final var headers = org.eclipse.ditto.base.model.headers.DittoHeaders.empty();
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
        final var headers = org.eclipse.ditto.base.model.headers.DittoHeaders.empty();
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
        final var headers = org.eclipse.ditto.base.model.headers.DittoHeaders.empty();
        final var response = MigrateConnectionEncryptionStatusResponse.of(
                "in_progress:snapshots",
                150, 10, 2,
                0, 0, 0,
                "507f1f77bcf86cd799439011", "connection:mqtt-prod-sensor-01",
                null, null,
                "2026-02-16T10:00:00Z", "2026-02-16T10:30:00Z",
                true,
                true,
                headers);

        final JsonObject json = response.toJson();
        final var deserialized = MigrateConnectionEncryptionStatusResponse.fromJson(json, headers);

        assertThat(deserialized.getPhase()).isEqualTo("in_progress:snapshots");
        assertThat(deserialized.isMigrationActive()).isTrue();
        assertThat(deserialized.getType()).isEqualTo(MigrateConnectionEncryptionStatusResponse.TYPE);
    }

    @Test
    public void abortResponseSerializationRoundTrip() {
        final var headers = org.eclipse.ditto.base.model.headers.DittoHeaders.empty();
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

    // --- Progress tracking tests ---

    @Test
    public void migrationProgressTracking() {
        final EncryptionMigrationActor.MigrationProgress progress =
                new EncryptionMigrationActor.MigrationProgress();

        progress.incrementSnapshotsProcessed()
                .incrementSnapshotsProcessed()
                .incrementSnapshotsSkipped()
                .incrementSnapshotsFailed();

        assertThat(progress.snapshotsProcessed).isEqualTo(2);
        assertThat(progress.snapshotsSkipped).isEqualTo(1);
        assertThat(progress.snapshotsFailed).isEqualTo(1);

        progress.withPhase("journal")
                .incrementJournalProcessed()
                .incrementJournalSkipped();

        assertThat(progress.phase).isEqualTo("journal");
        assertThat(progress.journalProcessed).isEqualTo(1);
        assertThat(progress.journalSkipped).isEqualTo(1);
    }

    // --- Helper methods ---

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
