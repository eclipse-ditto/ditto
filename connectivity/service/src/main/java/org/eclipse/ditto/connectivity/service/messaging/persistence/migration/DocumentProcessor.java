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

import org.bson.BsonDocument;
import org.bson.Document;
import org.eclipse.ditto.connectivity.model.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.connectivity.service.messaging.persistence.JsonFieldsEncryptor;
import org.eclipse.ditto.internal.utils.persistence.mongo.DittoBsonJson;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOneModel;
import com.mongodb.client.model.WriteModel;

/**
 * Processes MongoDB documents during encryption migration.
 * <p>
 * Provides static methods for transforming snapshot and journal documents, including
 * re-encrypting fields with new keys, decrypting fields to plaintext, and initial encryption
 * of plaintext data.
 */
public final class DocumentProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentProcessor.class);

    // MongoDB field names for pekko-persistence-mongodb
    private static final String SNAPSHOT_SERIALIZED_FIELD = "s2";
    private static final String JOURNAL_EVENTS_FIELD = "events";
    private static final String JOURNAL_PAYLOAD_FIELD = "p";
    private static final String ID_FIELD = "_id";

    private DocumentProcessor() {
        // Utility class - no instantiation
    }

    /**
     * Processes a snapshot document during migration.
     * <p>
     * Reads the serialized snapshot (BSON field "s2"), decrypts/re-encrypts according to
     * the migration context, and creates a write model for batch updates.
     *
     * @param doc the MongoDB snapshot document
     * @param context the migration context (keys, pointers, entity prefix)
     * @param dryRun if true, skip writing changes to MongoDB
     * @return the processing result
     */
    public static DocumentProcessingResult processSnapshotDocument(final Document doc,
            final MigrationContext context, final boolean dryRun) {
        final String docId = doc.get(ID_FIELD).toString();
        final String pid = doc.getString("pid");
        try {
            final Document s2 = doc.get(SNAPSHOT_SERIALIZED_FIELD, Document.class);
            if (s2 == null) {
                return DocumentProcessingResult.skipped();
            } else {
                final BsonDocument bsonDoc = s2.toBsonDocument(Document.class,
                        com.mongodb.MongoClientSettings.getDefaultCodecRegistry());
                final JsonObject jsonObject = DittoBsonJson.getInstance().serialize(bsonDoc);

                final Optional<JsonObject> reEncrypted = reEncryptFields(jsonObject, context);

                if (reEncrypted.isEmpty()) {
                    return DocumentProcessingResult.skipped();
                } else {
                    if (!dryRun) {
                        final BsonDocument newBson = DittoBsonJson.getInstance().parse(reEncrypted.get());
                        doc.put(SNAPSHOT_SERIALIZED_FIELD, Document.parse(newBson.toJson()));
                        final WriteModel<Document> writeModel = new ReplaceOneModel<>(
                                Filters.eq(ID_FIELD, doc.get(ID_FIELD)),
                                doc);
                        return DocumentProcessingResult.processed(writeModel);
                    }
                    return DocumentProcessingResult.processed(null);
                }
            }
        } catch (final Exception e) {
            LOGGER.warn("Failed to process snapshot {} (pid={}): {}", docId, pid, e.getMessage());
            return DocumentProcessingResult.failed();
        }
    }

    /**
     * Processes a journal document during migration.
     * <p>
     * Iterates through the "events" array, decrypts/re-encrypts each event payload according to
     * the migration context, and creates a write model for batch updates.
     *
     * @param doc the MongoDB journal document
     * @param context the migration context (keys, pointers, entity prefix)
     * @param dryRun if true, skip writing changes to MongoDB
     * @return the processing result
     */
    public static DocumentProcessingResult processJournalDocument(final Document doc,
            final MigrationContext context, final boolean dryRun) {
        final Object docId = doc.get(ID_FIELD);
        final String docIdStr = docId.toString();
        final String pid = doc.getString("pid");
        try {
            final List<Document> events = doc.getList(JOURNAL_EVENTS_FIELD, Document.class);
            if (events == null || events.isEmpty()) {
                return DocumentProcessingResult.skipped();
            } else {
                boolean anyChanged = false;
                final List<Document> updatedEvents = new ArrayList<>(events.size());

                for (final Document event : events) {
                    final Document payload = event.get(JOURNAL_PAYLOAD_FIELD, Document.class);
                    if (payload == null) {
                        updatedEvents.add(event);
                    } else {
                        final BsonDocument bsonPayload = payload.toBsonDocument(Document.class,
                                com.mongodb.MongoClientSettings.getDefaultCodecRegistry());
                        final JsonObject jsonPayload = DittoBsonJson.getInstance().serialize(bsonPayload);

                        final Optional<JsonObject> reEncrypted = reEncryptFields(jsonPayload, context);

                        if (reEncrypted.isPresent()) {
                            if (!dryRun) {
                                final BsonDocument newBson = DittoBsonJson.getInstance().parse(reEncrypted.get());
                                event.put(JOURNAL_PAYLOAD_FIELD, Document.parse(newBson.toJson()));
                            }
                            anyChanged = true;
                        }
                        updatedEvents.add(event);
                    }
                }

                if (anyChanged) {
                    if (!dryRun) {
                        doc.put(JOURNAL_EVENTS_FIELD, updatedEvents);
                        final WriteModel<Document> writeModel = new ReplaceOneModel<>(
                                Filters.eq(ID_FIELD, docId),
                                doc);
                        return DocumentProcessingResult.processed(writeModel);
                    }
                    return DocumentProcessingResult.processed(null);
                } else {
                    return DocumentProcessingResult.skipped();
                }
            }
        } catch (final Exception e) {
            LOGGER.warn("Failed to process journal document {} (pid={}): {}", docIdStr, pid, e.getMessage());
            return DocumentProcessingResult.failed();
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
