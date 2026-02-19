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
import static org.eclipse.ditto.connectivity.service.messaging.persistence.EncryptionMigrationTestHelper.*;

import java.util.UUID;

import org.bson.Document;
import org.eclipse.ditto.json.JsonObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;

/**
 * System/integration test for encryption migration in <b>initial-encryption</b> mode.
 * <p>
 * Mode: encryption ON + current key only (no old key).
 * Behavior: encrypts plaintext data with the current key.
 * <p>
 * Requires Docker containers running with the initial-encryption docker-compose override:
 * <pre>
 *   docker-compose -f docker-compose.yml -f docker-compose-initial-encryption.yml up -d
 *   mvn verify -pl connectivity/service -Dtest=EncryptionMigrationInitialEncryptionIT -Dskip.npm -DfailIfNoTests=false
 * </pre>
 */
public final class EncryptionMigrationInitialEncryptionIT {

    private static final String GATEWAY_URL = System.getProperty("ditto.gateway.url", "http://localhost:8080");
    private static final String MONGODB_HOST = System.getProperty("ditto.mongodb.host", "localhost");
    private static final int MONGODB_PORT = Integer.getInteger("ditto.mongodb.port", 27017);

    /** The current encryption key (must match docker-compose-initial-encryption.yml). */
    private static final String CURRENT_KEY = TEST_KEY;

    private MongoClient mongoClient;
    private MongoDatabase database;
    private MongoCollection<Document> snapshotCollection;

    @Before
    public void setUp() {
        mongoClient = createMongoClient(MONGODB_HOST, MONGODB_PORT);
        database = getDatabase(mongoClient);
        snapshotCollection = database.getCollection(SNAPSHOT_COLLECTION);
    }

    @After
    public void tearDown() {
        if (mongoClient != null) {
            mongoClient.close();
        }
    }

    /**
     * Tests that migration encrypts plaintext snapshot data when running in initial-encryption mode.
     * <p>
     * Scenario: Data was stored before encryption was enabled (plaintext in MongoDB).
     * After migration, all sensitive fields should be encrypted with the current key.
     */
    @Test
    public void migrationEncryptsPlaintextData() throws Exception {
        // Given: a snapshot with plaintext (unencrypted) fields
        final String connId = "init-enc-test-" + UUID.randomUUID().toString().substring(0, 8);
        final String pid = "connection:" + connId;
        final JsonObject plainData = createTestConnectionJson(connId);

        // Insert as plaintext — simulates data stored before encryption was enabled
        insertTestSnapshot(pid, plainData);

        // Verify data is currently plaintext
        final Document snapshotBefore = findSnapshot(snapshotCollection, pid);
        assertThat(snapshotBefore).isNotNull();
        final JsonObject jsonBefore = extractSnapshotJson(snapshotBefore);
        assertThat(isPlaintextValue(jsonBefore.getValue("/credentials/password").get().asString())).isTrue();
        assertThat(isPlaintextValue(jsonBefore.getValue("/uri").get().asString())).isTrue();

        // When: trigger migration
        postMigrationPiggyback(GATEWAY_URL, false, false);
        waitForMigrationCompleted(GATEWAY_URL, 60);

        // Then: data should now be encrypted with current key
        final Document snapshotAfter = findSnapshot(snapshotCollection, pid);
        assertThat(snapshotAfter).isNotNull();
        final JsonObject jsonAfter = extractSnapshotJson(snapshotAfter);

        // Fields should be encrypted
        final String pwdAfter = jsonAfter.getValue("/credentials/password").get().asString();
        assertThat(isEncryptedValue(pwdAfter)).isTrue();

        final String uriAfter = jsonAfter.getValue("/uri").get().asString();
        assertThat(isEncryptedValue(uriAfter)).isTrue();

        // Should be decryptable with current key
        final JsonObject decrypted = decryptFields(jsonAfter, "", DEFAULT_POINTERS, CURRENT_KEY);
        assertThat(decrypted.getValue("/credentials/password").get().asString())
                .isEqualTo("mySecretPassword");
        assertThat(decrypted.getValue("/uri").get().asString())
                .contains("secretPassword123");
    }

    /**
     * Tests that migration skips data already encrypted with the current key.
     * <p>
     * Scenario: Some connections were already encrypted (e.g., created after encryption was enabled).
     * Migration should detect these and skip them.
     */
    @Test
    public void migrationSkipsAlreadyEncryptedData() throws Exception {
        // Given: a snapshot already encrypted with the current key
        final String connId = "init-skip-test-" + UUID.randomUUID().toString().substring(0, 8);
        final String pid = "connection:" + connId;
        final JsonObject plainData = createTestConnectionJson(connId);

        // Encrypt with current key before inserting
        final JsonObject encryptedData = encryptFields(plainData, "", DEFAULT_POINTERS, CURRENT_KEY);
        insertTestSnapshot(pid, encryptedData);

        // Capture encrypted values before migration
        final Document snapshotBefore = findSnapshot(snapshotCollection, pid);
        final JsonObject jsonBefore = extractSnapshotJson(snapshotBefore);
        final String pwdBefore = jsonBefore.getValue("/credentials/password").get().asString();

        // When: trigger migration
        postMigrationPiggyback(GATEWAY_URL, false, false);
        waitForMigrationCompleted(GATEWAY_URL, 60);

        // Then: data should be unchanged (skipped)
        final Document snapshotAfter = findSnapshot(snapshotCollection, pid);
        final JsonObject jsonAfter = extractSnapshotJson(snapshotAfter);
        final String pwdAfter = jsonAfter.getValue("/credentials/password").get().asString();

        // Encrypted value should be identical (was skipped, not re-encrypted)
        assertThat(pwdAfter).isEqualTo(pwdBefore);
    }

    /**
     * Tests that dry-run mode counts documents but does not encrypt them.
     */
    @Test
    public void dryRunDoesNotEncryptData() throws Exception {
        // Given: a snapshot with plaintext fields
        final String connId = "init-dryrun-test-" + UUID.randomUUID().toString().substring(0, 8);
        final String pid = "connection:" + connId;
        final JsonObject plainData = createTestConnectionJson(connId);
        insertTestSnapshot(pid, plainData);

        // Capture state before
        final Document snapshotBefore = findSnapshot(snapshotCollection, pid);
        final JsonObject jsonBefore = extractSnapshotJson(snapshotBefore);
        final String pwdBefore = jsonBefore.getValue("/credentials/password").get().asString();
        assertThat(isPlaintextValue(pwdBefore)).isTrue();

        // When: run dry-run migration
        postMigrationPiggyback(GATEWAY_URL, true, false);
        waitForMigrationCompleted(GATEWAY_URL, 60);

        // Then: data should still be plaintext
        final Document snapshotAfter = findSnapshot(snapshotCollection, pid);
        final JsonObject jsonAfter = extractSnapshotJson(snapshotAfter);
        final String pwdAfter = jsonAfter.getValue("/credentials/password").get().asString();

        assertThat(pwdAfter).isEqualTo(pwdBefore);
        assertThat(isPlaintextValue(pwdAfter)).isTrue();
    }

    // --- Test data helpers ---

    private void insertTestSnapshot(final String pid, final JsonObject snapshotJson) throws Exception {
        final org.bson.BsonDocument bsonData = org.eclipse.ditto.internal.utils.persistence.mongo.DittoBsonJson
                .getInstance().parse(snapshotJson);
        final Document s2 = Document.parse(bsonData.toJson());

        final Document snapshotDoc = new Document()
                .append("pid", pid)
                .append("sn", 1L)
                .append("ts", System.currentTimeMillis())
                .append(SNAPSHOT_SERIALIZED_FIELD, s2);

        blockFirst(snapshotCollection.insertOne(snapshotDoc));
    }
}
