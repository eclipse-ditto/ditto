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
 * System/integration test for encryption migration in <b>disable-encryption</b> mode.
 * <p>
 * Mode: encryption OFF + old key present.
 * Behavior: decrypts data encrypted with old key back to plaintext.
 * <p>
 * Requires Docker containers running with the disable-encryption docker-compose override:
 * <pre>
 *   docker-compose -f docker-compose.yml -f docker-compose-disable-encryption.yml up -d
 *   mvn verify -pl connectivity/service -Dtest=EncryptionMigrationDisableIT -Dskip.npm -DfailIfNoTests=false
 * </pre>
 */
public final class EncryptionMigrationDisableIT {

    private static final String GATEWAY_URL = System.getProperty("ditto.gateway.url", "http://localhost:8080");
    private static final String MONGODB_HOST = System.getProperty("ditto.mongodb.host", "localhost");
    private static final int MONGODB_PORT = Integer.getInteger("ditto.mongodb.port", 27017);

    /**
     * The old key that was used to encrypt data (must match
     * CONNECTIVITY_CONNECTION_OLD_ENCRYPTION_KEY in docker-compose-disable-encryption.yml).
     */
    private static final String OLD_KEY = TEST_KEY;

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
     * Tests that migration decrypts encrypted data back to plaintext when running in disable mode.
     * <p>
     * Scenario: Data was previously encrypted. Encryption is now disabled with the old key
     * configured for reading. Migration should decrypt all encrypted fields to plaintext.
     */
    @Test
    public void migrationDecryptsToPlaintext() throws Exception {
        // Given: a snapshot with fields encrypted using the old key
        final String connId = "disable-test-" + UUID.randomUUID().toString().substring(0, 8);
        final String pid = "connection:" + connId;
        final JsonObject plainData = createTestConnectionJson(connId);

        // Encrypt with old key — simulates data encrypted before encryption was disabled
        final JsonObject encryptedData = encryptFields(plainData, "", DEFAULT_POINTERS, OLD_KEY);
        insertTestSnapshot(pid, encryptedData);

        // Verify data is currently encrypted
        final Document snapshotBefore = findSnapshot(snapshotCollection, pid);
        assertThat(snapshotBefore).isNotNull();
        final JsonObject jsonBefore = extractSnapshotJson(snapshotBefore);
        assertThat(isEncryptedValue(jsonBefore.getValue("/credentials/password").get().asString())).isTrue();
        assertThat(isEncryptedValue(jsonBefore.getValue("/uri").get().asString())).isTrue();

        // When: trigger migration
        postMigrationPiggyback(GATEWAY_URL, false, false);
        waitForMigrationCompleted(GATEWAY_URL, 60);

        // Then: data should now be plaintext
        final Document snapshotAfter = findSnapshot(snapshotCollection, pid);
        assertThat(snapshotAfter).isNotNull();
        final JsonObject jsonAfter = extractSnapshotJson(snapshotAfter);

        // Fields should be plaintext (no encrypted_ prefix)
        final String pwdAfter = jsonAfter.getValue("/credentials/password").get().asString();
        assertThat(isPlaintextValue(pwdAfter)).isTrue();
        assertThat(pwdAfter).isEqualTo("mySecretPassword");

        final String uriAfter = jsonAfter.getValue("/uri").get().asString();
        assertThat(isPlaintextValue(uriAfter)).isTrue();
        assertThat(uriAfter).isEqualTo("tcp://user:secretPassword123@broker.example.com:1883");
    }

    /**
     * Tests that migration skips data that is already plaintext.
     * <p>
     * Scenario: Some connections were already stored as plaintext (e.g., created after
     * encryption was disabled). Migration should detect these and skip them.
     */
    @Test
    public void migrationSkipsAlreadyPlaintextData() throws Exception {
        // Given: a snapshot with plaintext fields
        final String connId = "disable-skip-test-" + UUID.randomUUID().toString().substring(0, 8);
        final String pid = "connection:" + connId;
        final JsonObject plainData = createTestConnectionJson(connId);

        // Insert as plaintext
        insertTestSnapshot(pid, plainData);

        // Capture state before
        final Document snapshotBefore = findSnapshot(snapshotCollection, pid);
        final JsonObject jsonBefore = extractSnapshotJson(snapshotBefore);
        final String pwdBefore = jsonBefore.getValue("/credentials/password").get().asString();
        assertThat(isPlaintextValue(pwdBefore)).isTrue();

        // When: trigger migration
        postMigrationPiggyback(GATEWAY_URL, false, false);
        waitForMigrationCompleted(GATEWAY_URL, 60);

        // Then: data should be unchanged (skipped)
        final Document snapshotAfter = findSnapshot(snapshotCollection, pid);
        final JsonObject jsonAfter = extractSnapshotJson(snapshotAfter);
        final String pwdAfter = jsonAfter.getValue("/credentials/password").get().asString();

        assertThat(pwdAfter).isEqualTo(pwdBefore);
        assertThat(isPlaintextValue(pwdAfter)).isTrue();
    }

    /**
     * Tests that dry-run mode counts documents but does not decrypt them.
     */
    @Test
    public void dryRunDoesNotDecryptData() throws Exception {
        // Given: a snapshot encrypted with old key
        final String connId = "disable-dryrun-test-" + UUID.randomUUID().toString().substring(0, 8);
        final String pid = "connection:" + connId;
        final JsonObject plainData = createTestConnectionJson(connId);

        final JsonObject encryptedData = encryptFields(plainData, "", DEFAULT_POINTERS, OLD_KEY);
        insertTestSnapshot(pid, encryptedData);

        // Capture encrypted values before
        final Document snapshotBefore = findSnapshot(snapshotCollection, pid);
        final JsonObject jsonBefore = extractSnapshotJson(snapshotBefore);
        final String pwdBefore = jsonBefore.getValue("/credentials/password").get().asString();
        assertThat(isEncryptedValue(pwdBefore)).isTrue();

        // When: run dry-run migration
        postMigrationPiggyback(GATEWAY_URL, true, false);
        waitForMigrationCompleted(GATEWAY_URL, 60);

        // Then: data should still be encrypted
        final Document snapshotAfter = findSnapshot(snapshotCollection, pid);
        final JsonObject jsonAfter = extractSnapshotJson(snapshotAfter);
        final String pwdAfter = jsonAfter.getValue("/credentials/password").get().asString();

        // Should be identical (dry-run, no modifications)
        assertThat(pwdAfter).isEqualTo(pwdBefore);
        assertThat(isEncryptedValue(pwdAfter)).isTrue();

        // Should still be decryptable with old key
        final JsonObject decrypted = decryptFields(jsonAfter, "", DEFAULT_POINTERS, OLD_KEY);
        assertThat(decrypted.getValue("/credentials/password").get().asString())
                .isEqualTo("mySecretPassword");
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
