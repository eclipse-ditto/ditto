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

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.eclipse.ditto.connectivity.model.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.connectivity.service.messaging.persistence.JsonFieldsEncryptor;
import org.eclipse.ditto.internal.utils.persistence.api.payload.PersistedJournalEntry;
import org.eclipse.ditto.internal.utils.persistence.api.payload.PersistedSnapshot;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Re-encrypts persisted payloads during encryption migration.
 * <p>
 * Provides static methods for transforming the JSON payloads of persisted snapshots and journal entries, including
 * re-encrypting fields with new keys, decrypting fields to plaintext, and initial encryption of plaintext data. The
 * entries come from, and go back to, the persistence backend's {@code PersistedPayloadStore}; this class only sees
 * their JSON.
 */
public final class PayloadReEncryptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(PayloadReEncryptor.class);

    private PayloadReEncryptor() {
        // Utility class - no instantiation
    }

    /**
     * Processes a persisted snapshot during migration.
     * <p>
     * Reads the snapshot payload, decrypts/re-encrypts it according to the migration context, and returns the snapshot
     * with the new payload for the batch write.
     *
     * @param snapshot the persisted snapshot
     * @param context the migration context (keys, pointers, entity prefix)
     * @param dryRun if true, skip producing the rewritten snapshot
     * @return the processing result
     */
    public static PayloadProcessingResult<PersistedSnapshot> processSnapshot(final PersistedSnapshot snapshot,
            final MigrationContext context, final boolean dryRun) {
        final String cursor = snapshot.cursor();
        final String pid = snapshot.pid();
        try {
            final JsonObject payload = snapshot.payload();

            final Optional<JsonObject> reEncrypted = reEncryptFields(payload, context);

            if (reEncrypted.isEmpty()) {
                return PayloadProcessingResult.skipped();
            } else {
                if (!dryRun) {
                    return PayloadProcessingResult.processed(snapshot.withPayload(reEncrypted.get()));
                }
                return PayloadProcessingResult.processed(null);
            }
        } catch (final Exception e) {
            LOGGER.warn("Failed to process snapshot {} (pid={}): {}", cursor, pid, e.getMessage());
            return PayloadProcessingResult.failed();
        }
    }

    /**
     * Processes a persisted journal entry during migration.
     * <p>
     * Iterates through the entry's event payloads, decrypts/re-encrypts each according to the migration context, and
     * returns the entry with the new payloads for the batch write.
     *
     * @param entry the persisted journal entry
     * @param context the migration context (keys, pointers, entity prefix)
     * @param dryRun if true, skip producing the rewritten entry
     * @return the processing result
     */
    public static PayloadProcessingResult<PersistedJournalEntry> processJournalEntry(final PersistedJournalEntry entry,
            final MigrationContext context, final boolean dryRun) {
        final String cursor = entry.cursor();
        final String pid = entry.pid();
        try {
            final List<JsonObject> payloads = entry.eventPayloads();
            if (payloads.isEmpty()) {
                return PayloadProcessingResult.skipped();
            } else {
                boolean anyChanged = false;
                final List<JsonObject> updatedPayloads = new ArrayList<>(payloads.size());

                for (final JsonObject payload : payloads) {
                    final Optional<JsonObject> reEncrypted = reEncryptFields(payload, context);

                    if (reEncrypted.isPresent()) {
                        anyChanged = true;
                    }
                    updatedPayloads.add(reEncrypted.orElse(payload));
                }

                if (anyChanged) {
                    if (!dryRun) {
                        return PayloadProcessingResult.processed(entry.withEventPayloads(updatedPayloads));
                    }
                    return PayloadProcessingResult.processed(null);
                } else {
                    return PayloadProcessingResult.skipped();
                }
            }
        } catch (final Exception e) {
            LOGGER.warn("Failed to process journal document {} (pid={}): {}", cursor, pid, e.getMessage());
            return PayloadProcessingResult.failed();
        }
    }

    /**
     * Re-encrypts fields in a JSON object based on the migration mode.
     * <p>
     * Supports three modes:
     * <ul>
     *   <li><b>Initial encryption</b> ({@code oldKey == null}): encrypt plaintext with newKey</li>
     *   <li><b>Key rotation</b> (both keys set): decrypt with oldKey, encrypt with newKey</li>
     *   <li><b>Disable encryption</b> ({@code newKey == null}): decrypt with oldKey, write plaintext</li>
     * </ul>
     *
     * @param jsonObject the JSON object to process
     * @param context the migration context (keys, pointers, entity prefix)
     * @return the transformed JSON object, or {@code Optional.empty()} if already in the desired state (skip)
     */
    public static Optional<JsonObject> reEncryptFields(final JsonObject jsonObject, final MigrationContext context) {
        final String oldKey = context.oldKey();
        final String newKey = context.newKey();

        if (oldKey == null && newKey != null) {
            return initialEncrypt(jsonObject, context);
        } else if (oldKey != null && newKey == null) {
            return disableEncryption(jsonObject, context);
        } else if (oldKey != null) {
            return rotateKey(jsonObject, context);
        } else {
            return Optional.empty();
        }
    }

    private static Optional<JsonObject> initialEncrypt(final JsonObject jsonObject,
            final MigrationContext context) {
        if (hasAnyEncryptedField(jsonObject, context)) {
            return Optional.empty();
        }
        final JsonObject encrypted = JsonFieldsEncryptor.encrypt(
                jsonObject, context.entityTypePrefix(), context.pointers(), context.newKey());
        return ifChanged(jsonObject, encrypted);
    }

    private static Optional<JsonObject> disableEncryption(final JsonObject jsonObject,
            final MigrationContext context) {
        try {
            final JsonObject decrypted = JsonFieldsEncryptor.decrypt(
                    jsonObject, context.entityTypePrefix(), context.pointers(), context.oldKey());
            return ifChanged(jsonObject, decrypted);
        } catch (final ConnectionConfigurationInvalidException e) {
            LOGGER.debug("Decryption failed in disable workflow, assuming already plaintext — skipping");
            return Optional.empty();
        }
    }

    private static Optional<JsonObject> rotateKey(final JsonObject jsonObject,
            final MigrationContext context) {
        try {
            final JsonObject decrypted = JsonFieldsEncryptor.decrypt(
                    jsonObject, context.entityTypePrefix(), context.pointers(), context.oldKey());
            final JsonObject reEncrypted = JsonFieldsEncryptor.encrypt(
                    decrypted, context.entityTypePrefix(), context.pointers(), context.newKey());
            return ifChanged(jsonObject, reEncrypted);
        } catch (final ConnectionConfigurationInvalidException oldKeyFailed) {
            return skipIfAlreadyMigratedOrThrow(jsonObject, context, oldKeyFailed);
        }
    }

    /**
     * When old-key decryption fails during key rotation, checks whether the data is already
     * encrypted with the new key. If so, the document was already migrated — skip it.
     * If the new key also fails, both exceptions are combined and re-thrown.
     */
    private static Optional<JsonObject> skipIfAlreadyMigratedOrThrow(final JsonObject jsonObject,
            final MigrationContext context, final ConnectionConfigurationInvalidException oldKeyFailed) {
        try {
            JsonFieldsEncryptor.decrypt(jsonObject, context.entityTypePrefix(),
                    context.pointers(), context.newKey());
            return Optional.empty();
        } catch (final ConnectionConfigurationInvalidException newKeyFailed) {
            newKeyFailed.addSuppressed(oldKeyFailed);
            throw newKeyFailed;
        }
    }

    private static Optional<JsonObject> ifChanged(final JsonObject original, final JsonObject result) {
        return result.equals(original) ? Optional.empty() : Optional.of(result);
    }

    /**
     * Checks whether any targeted field already contains encrypted data — either as a direct
     * {@code encrypted_} prefix or embedded in the password part of a URI.
     */
    private static boolean hasAnyEncryptedField(final JsonObject jsonObject, final MigrationContext context) {
        return context.pointers().stream()
                .map(p -> context.entityTypePrefix() + p)
                .map(JsonPointer::of)
                .flatMap(pointer -> jsonObject.getValue(pointer).stream())
                .filter(JsonValue::isString)
                .anyMatch(v -> containsEncryptedValue(v.asString()));
    }

    private static boolean containsEncryptedValue(final String value) {
        final String encryptedPrefix = "encrypted_";
        if (value.startsWith(encryptedPrefix)) {
            return true;
        }
        try {
            final URI uri = new URI(value);
            if (uri.getScheme() != null && uri.getRawUserInfo() != null) {
                final String[] userPass = uri.getRawUserInfo().split(":", 2);
                return userPass.length == 2 &&
                        userPass[1].startsWith(encryptedPrefix);
            }
        } catch (final Exception ignored) {
            // Not a valid URI — fall through
        }
        return false;
    }
}
