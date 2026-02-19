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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.bson.BsonDocument;
import org.bson.Document;
import org.eclipse.ditto.internal.utils.persistence.mongo.DittoBsonJson;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.reactivestreams.Publisher;

import com.mongodb.client.model.Filters;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;

/**
 * Shared test utilities for encryption migration system/integration tests.
 * <p>
 * Provides crypto helpers, MongoDB access, snapshot manipulation, and piggyback command
 * utilities used by all three migration mode test classes.
 */
public final class EncryptionMigrationTestHelper {

    /** Test encryption key (same as in connection-fields-encryption-test.conf). */
    public static final String TEST_KEY = "vJFSTPE9PO2BtZlcMAwNjs8jdFvQCk0Ya9MVdYjRJUU=";

    /** MongoDB collection name for connection snapshots. */
    public static final String SNAPSHOT_COLLECTION = "connection_snaps";

    /** MongoDB collection name for connection journal events. */
    public static final String JOURNAL_COLLECTION = "connection_journal";

    /** MongoDB collection name for migration progress tracking. */
    public static final String PROGRESS_COLLECTION = "connection_encryption_migration";

    /** BSON field containing the serialized snapshot data. */
    public static final String SNAPSHOT_SERIALIZED_FIELD = "s2";

    /** BSON field containing the journal events array. */
    public static final String JOURNAL_EVENTS_FIELD = "events";

    /** BSON field containing the journal event payload. */
    public static final String JOURNAL_PAYLOAD_FIELD = "p";

    /** Default JSON pointers for encryption. */
    public static final List<String> DEFAULT_POINTERS = List.of("/uri", "/credentials/password");

    /** Prefix used by JsonFieldsEncryptor for encrypted values. */
    public static final String ENCRYPTED_PREFIX = "encrypted_";

    private static final String DEVOPS_AUTH = "Basic " +
            Base64.getEncoder().encodeToString("devops:devopsPw1!".getBytes());

    private EncryptionMigrationTestHelper() {
        // Utility class
    }

    // --- MongoDB access ---

    /**
     * Creates a MongoDB client connecting to the default Docker-exposed port.
     *
     * @return the MongoDB client
     */
    public static MongoClient createMongoClient() {
        return createMongoClient("localhost", 27017);
    }

    /**
     * Creates a MongoDB client for a specific host and port.
     *
     * @param host the MongoDB host
     * @param port the MongoDB port
     * @return the MongoDB client
     */
    public static MongoClient createMongoClient(final String host, final int port) {
        return MongoClients.create("mongodb://" + host + ":" + port);
    }

    /**
     * Gets the default Ditto database.
     *
     * @param client the MongoDB client
     * @return the database instance
     */
    public static MongoDatabase getDatabase(final MongoClient client) {
        return client.getDatabase("connectivity");
    }

    /**
     * Finds a snapshot document by connection PID.
     *
     * @param snapshotCollection the snapshot collection
     * @param connectionPid the connection persistence ID (e.g., "connection:my-conn-id")
     * @return the snapshot document, or null if not found
     */
    public static Document findSnapshot(final MongoCollection<Document> snapshotCollection,
            final String connectionPid) throws Exception {
        return blockFirst(snapshotCollection.find(Filters.eq("pid", connectionPid)).first());
    }

    /**
     * Finds a journal document by connection PID.
     *
     * @param journalCollection the journal collection
     * @param connectionPid the connection persistence ID
     * @return the journal document, or null if not found
     */
    public static Document findFirstJournalEntry(final MongoCollection<Document> journalCollection,
            final String connectionPid) throws Exception {
        return blockFirst(journalCollection.find(Filters.eq("pid", connectionPid)).first());
    }

    // --- Snapshot data extraction ---

    /**
     * Extracts the serialized snapshot JSON object from a snapshot document.
     *
     * @param snapshotDoc the MongoDB snapshot document
     * @return the JSON object from the s2 field
     */
    public static JsonObject extractSnapshotJson(final Document snapshotDoc) {
        final Document s2 = snapshotDoc.get(SNAPSHOT_SERIALIZED_FIELD, Document.class);
        final BsonDocument bsonDoc = s2.toBsonDocument(Document.class,
                com.mongodb.MongoClientSettings.getDefaultCodecRegistry());
        return DittoBsonJson.getInstance().serialize(bsonDoc);
    }

    /**
     * Replaces the serialized snapshot JSON in a snapshot document and writes it back to MongoDB.
     *
     * @param snapshotCollection the snapshot collection
     * @param snapshotDoc the original snapshot document
     * @param newJson the new JSON to store in the s2 field
     */
    public static void replaceSnapshotJson(final MongoCollection<Document> snapshotCollection,
            final Document snapshotDoc, final JsonObject newJson) throws Exception {
        final BsonDocument newBson = DittoBsonJson.getInstance().parse(newJson);
        snapshotDoc.put(SNAPSHOT_SERIALIZED_FIELD, Document.parse(newBson.toJson()));
        blockFirst(snapshotCollection.replaceOne(
                Filters.eq("_id", snapshotDoc.get("_id")),
                snapshotDoc));
    }

    // --- Crypto utilities ---

    /**
     * Encrypts JSON fields using the same algorithm as the connectivity service.
     *
     * @param json the JSON object with plaintext fields
     * @param entityTypePrefix the entity prefix ("" for snapshots, "connection" for journal)
     * @param pointers the JSON pointers to encrypt
     * @param key the encryption key
     * @return the JSON object with encrypted fields
     */
    public static JsonObject encryptFields(final JsonObject json, final String entityTypePrefix,
            final List<String> pointers, final String key) {
        return JsonFieldsEncryptor.encrypt(json, entityTypePrefix, pointers, key);
    }

    /**
     * Decrypts JSON fields using the same algorithm as the connectivity service.
     *
     * @param json the JSON object with encrypted fields
     * @param entityTypePrefix the entity prefix ("" for snapshots, "connection" for journal)
     * @param pointers the JSON pointers to decrypt
     * @param key the decryption key
     * @return the JSON object with decrypted fields
     */
    public static JsonObject decryptFields(final JsonObject json, final String entityTypePrefix,
            final List<String> pointers, final String key) {
        return JsonFieldsEncryptor.decrypt(json, entityTypePrefix, pointers, key);
    }

    /**
     * Checks whether a field value is encrypted (has the encrypted_ prefix).
     *
     * @param value the string value to check
     * @return true if the value is encrypted
     */
    public static boolean isEncryptedValue(final String value) {
        if (value.startsWith(ENCRYPTED_PREFIX)) {
            return true;
        }
        // Check for encrypted password in URI
        try {
            final java.net.URI uri = new java.net.URI(value);
            if (uri.getScheme() != null && uri.getRawUserInfo() != null) {
                final String[] userPass = uri.getRawUserInfo().split(":", 2);
                return userPass.length == 2 && userPass[1].startsWith(ENCRYPTED_PREFIX);
            }
        } catch (final Exception ignored) {
            // Not a URI
        }
        return false;
    }

    /**
     * Checks whether a field value is plaintext (not encrypted).
     *
     * @param value the string value to check
     * @return true if the value is plaintext
     */
    public static boolean isPlaintextValue(final String value) {
        return !isEncryptedValue(value);
    }

    // --- Piggyback command helpers ---

    /**
     * Sends a migration piggyback command to the connectivity service.
     *
     * @param gatewayBaseUrl the gateway URL (e.g., "http://localhost:8080")
     * @param dryRun whether to run in dry-run mode
     * @param resume whether to resume a previous migration
     * @return the HTTP response body as a string
     */
    public static String postMigrationPiggyback(final String gatewayBaseUrl, final boolean dryRun,
            final boolean resume) throws Exception {
        final String body = """
                {
                  "targetActorSelection": "/user/connectivityRoot/encryptionMigration",
                  "headers": {
                    "aggregate": false
                  },
                  "piggybackCommand": {
                    "type": "connectivity.commands:migrateEncryption",
                    "dryRun": %s,
                    "resume": %s
                  }
                }""".formatted(dryRun, resume);

        return postPiggyback(gatewayBaseUrl, body);
    }

    /**
     * Sends a migration status piggyback command to the connectivity service.
     *
     * @param gatewayBaseUrl the gateway URL
     * @return the HTTP response body as a string
     */
    public static String postMigrationStatusPiggyback(final String gatewayBaseUrl) throws Exception {
        final String body = """
                {
                  "targetActorSelection": "/user/connectivityRoot/encryptionMigration",
                  "headers": {
                    "aggregate": false
                  },
                  "piggybackCommand": {
                    "type": "connectivity.commands:migrateEncryptionStatus"
                  }
                }""";

        return postPiggyback(gatewayBaseUrl, body);
    }

    /**
     * Waits for a migration to complete by polling the status endpoint.
     *
     * @param gatewayBaseUrl the gateway URL
     * @param timeoutSeconds maximum time to wait
     * @return the final status response body
     */
    public static String waitForMigrationCompleted(final String gatewayBaseUrl,
            final int timeoutSeconds) throws Exception {
        final long deadline = System.currentTimeMillis() + (timeoutSeconds * 1000L);

        while (System.currentTimeMillis() < deadline) {
            final String status = postMigrationStatusPiggyback(gatewayBaseUrl);
            final JsonObject statusJson = JsonFactory.newObject(status);

            // Check connectivity-specific response (unwrap if aggregated)
            final JsonObject effectiveResponse = unwrapAggregatedResponse(statusJson);

            final String phase = effectiveResponse.getValue("phase")
                    .map(v -> v.asString())
                    .orElse("");

            if ("completed".equals(phase)) {
                return status;
            }

            final boolean active = effectiveResponse.getValue("migrationActive")
                    .map(v -> v.asBoolean())
                    .orElse(false);

            if (!active && !phase.startsWith("in_progress")) {
                // Migration ended (possibly with error)
                return status;
            }

            TimeUnit.SECONDS.sleep(1);
        }

        throw new AssertionError("Migration did not complete within " + timeoutSeconds + " seconds");
    }

    /**
     * Creates a simple test connection JSON for testing purposes.
     *
     * @param connectionId the connection ID
     * @return a JSON object representing a minimal connection with sensitive fields
     */
    public static JsonObject createTestConnectionJson(final String connectionId) {
        return JsonFactory.newObjectBuilder()
                .set("id", connectionId)
                .set("name", "test-connection-" + connectionId)
                .set("connectionType", "mqtt")
                .set("connectionStatus", "closed")
                .set("uri", "tcp://user:secretPassword123@broker.example.com:1883")
                .set("credentials/password", "mySecretPassword")
                .build();
    }

    // --- Internal helpers ---

    private static String postPiggyback(final String gatewayBaseUrl, final String body)
            throws Exception {
        final HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        final HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(gatewayBaseUrl + "/devops/piggyback/connectivity"))
                .header("Content-Type", "application/json")
                .header("Authorization", DEVOPS_AUTH)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(30))
                .build();

        final HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        return response.body();
    }

    private static JsonObject unwrapAggregatedResponse(final JsonObject response) {
        // If the response is aggregated (contains "connectivity" key with nested response),
        // unwrap it. Otherwise, return as-is.
        return response.getValue("connectivity")
                .filter(v -> v.isObject())
                .map(v -> v.asObject())
                .orElse(response);
    }

    /**
     * Blocks on a reactive publisher and returns the first element.
     *
     * @param publisher the reactive publisher
     * @param <T> the element type
     * @return the first element, or null if empty
     */
    @SuppressWarnings("unchecked")
    public static <T> T blockFirst(final Publisher<T> publisher) throws Exception {
        final java.util.concurrent.CompletableFuture<T> future = new java.util.concurrent.CompletableFuture<>();
        publisher.subscribe(new org.reactivestreams.Subscriber<T>() {
            @Override
            public void onSubscribe(final org.reactivestreams.Subscription s) {
                s.request(1);
            }

            @Override
            public void onNext(final T t) {
                future.complete(t);
            }

            @Override
            public void onError(final Throwable t) {
                future.completeExceptionally(t);
            }

            @Override
            public void onComplete() {
                if (!future.isDone()) {
                    future.complete(null);
                }
            }
        });
        return future.get(10, TimeUnit.SECONDS);
    }

    /**
     * Blocks on a reactive publisher and waits for completion.
     *
     * @param publisher the reactive publisher
     */
    public static void blockComplete(final Publisher<?> publisher) throws Exception {
        blockFirst(publisher);
    }
}
