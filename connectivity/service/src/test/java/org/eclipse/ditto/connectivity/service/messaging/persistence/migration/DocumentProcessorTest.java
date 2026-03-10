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
import java.util.List;
import java.util.Optional;

import org.eclipse.ditto.connectivity.service.messaging.persistence.JsonFieldsEncryptor;
import org.eclipse.ditto.connectivity.service.util.EncryptorAesGcm;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests for {@link DocumentProcessor} encryption migration logic.
 */
public final class DocumentProcessorTest {

    private static String OLD_KEY;
    private static String NEW_KEY;

    private static final List<String> POINTERS = List.of(
            "/uri",
            "/credentials/password"
    );

    @BeforeClass
    public static void initKeys() throws NoSuchAlgorithmException {
        OLD_KEY = EncryptorAesGcm.generateAESKeyAsString();
        NEW_KEY = EncryptorAesGcm.generateAESKeyAsString();
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
    public void whenBothKeysFailTreatsAsPlaintextAndEncrypts() {
        // When neither old nor new key can decrypt, the data is treated as plaintext
        // and encrypted with the new key. This handles the case where data was stored
        // before encryption was enabled.
        final JsonObject plain = createPlainSnapshotJson();

        final MigrationContext context = MigrationContext.forSnapshots(OLD_KEY, NEW_KEY, POINTERS);
        final Optional<JsonObject> result = DocumentProcessor.reEncryptFields(plain, context);

        // Should encrypt the plaintext data with new key
        assertThat(result).isPresent();

        // Verify it can be decrypted with the new key
        final JsonObject decrypted = JsonFieldsEncryptor.decrypt(result.get(), "", POINTERS, NEW_KEY);
        assertThat(decrypted).isEqualTo(plain);
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
    public void plainTextFieldsNotAffected() {
        final JsonObject plain = createPlainSnapshotJson();

        final MigrationContext context = MigrationContext.forSnapshots(OLD_KEY, NEW_KEY, POINTERS);
        final Optional<JsonObject> result = DocumentProcessor.reEncryptFields(plain, context);

        assertThat(result).isPresent();
        final String encryptedPwd = result.get().getValue("/credentials/password").get().asString();
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
        final MigrationContext context = MigrationContext.forSnapshots(null, NEW_KEY, POINTERS);
        final Optional<JsonObject> result = DocumentProcessor.reEncryptFields(encrypted, context);

        assertThat(result).isEmpty();
    }

    @Test
    public void disableWorkflowSkipsAlreadyPlaintextEntity() {
        // Bug 2: decrypt() silently passes through plaintext, so disable workflow
        // was counting plaintext entities as "processed" instead of "skipped"
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
