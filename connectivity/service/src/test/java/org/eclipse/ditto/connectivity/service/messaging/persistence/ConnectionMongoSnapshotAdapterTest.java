/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

import java.net.URI;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.bson.BsonDocument;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.connectivity.model.ConnectionLifecycle;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.service.config.DefaultFieldsEncryptionConfig;
import org.eclipse.ditto.connectivity.service.messaging.TestConstants;
import org.eclipse.ditto.internal.utils.persistence.api.serializer.NeutralSnapshotAdapter;
import org.eclipse.ditto.internal.utils.persistence.mongo.DittoBsonJson;
import org.eclipse.ditto.internal.utils.persistence.mongo.MongoSnapshotCodec;
import org.eclipse.ditto.json.JsonObject;
import org.junit.Test;

public class ConnectionMongoSnapshotAdapterTest {

    @Test
    public void makeEmptySnapshotsForDeletedConnections() {
        final Config config = ConfigFactory.load("connection-fields-encryption-test");
        final var serializer = new ConnectionMongoSnapshotAdapter(DefaultFieldsEncryptionConfig.of(config.getConfig("connection")));
        // The Mongo BSON envelope is now the injected snapshot codec; the serializer holds only the domain logic.
        final var underTest = new NeutralSnapshotAdapter<>(serializer, MongoSnapshotCodec.INSTANCE);
        final var deletedSnapshot = (BsonDocument) underTest.toSnapshotStore(
                TestConstants.createConnection().toBuilder().lifecycle(ConnectionLifecycle.DELETED).build());
        final JsonObject snapshotJson = DittoBsonJson.getInstance().serialize(deletedSnapshot);
        assertThat(snapshotJson).containsOnly(serializer.getDeletedLifecycleJsonField());
    }

    @Test
    public void encryptDecryptSnapshot() {
        final Config config = ConfigFactory.load("connection-fields-encryption-test");
        final ConnectionMongoSnapshotAdapter serializer = new ConnectionMongoSnapshotAdapter(DefaultFieldsEncryptionConfig.of(config.getConfig("connection")));
        final var underTest = new NeutralSnapshotAdapter<>(serializer, MongoSnapshotCodec.INSTANCE);
        final Connection connection = TestConstants.createConnection();
        final URI uri = URI.create(connection.getUri());
        final String userInfo = uri.getUserInfo();

        final BsonDocument snapshotConnection = (BsonDocument) underTest.toSnapshotStore(connection);
        final Connection encryptedConnection = ConnectivityModelFactory.connectionFromJson(JsonObject.of(snapshotConnection.toJson()));
        final Connection decryptedConnection = serializer.createJsonifiableFrom(JsonObject.of(snapshotConnection.toJson()));
        final String userInfoAfterDecrypt = URI.create(decryptedConnection.getUri()).getUserInfo();

        assertThat(encryptedConnection.getUri()).contains("encrypted_");
        assertThat(userInfoAfterDecrypt).isEqualTo(userInfo);
    }

    @Test
    public void disabledEncryptionWithTheFormerKeyKeptAsCurrentKeyDecryptsAndWritesPlaintext() {
        // disabling encryption with the former key left as 'symmetrical-key' (the former PostgreSQL advice) keeps
        // working: snapshots written while encryption was enabled are still decrypted, and rewritten in plaintext
        final Config config = ConfigFactory.load("connection-fields-encryption-test").getConfig("connection");
        final ConnectionMongoSnapshotAdapter encryptingSerializer =
                new ConnectionMongoSnapshotAdapter(DefaultFieldsEncryptionConfig.of(config));
        final Connection connection = TestConstants.createConnection();
        final String userInfo = URI.create(connection.getUri()).getUserInfo();
        final BsonDocument encryptedSnapshot = (BsonDocument)
                new NeutralSnapshotAdapter<>(encryptingSerializer, MongoSnapshotCodec.INSTANCE)
                        .toSnapshotStore(connection);

        final ConnectionMongoSnapshotAdapter disabledSerializer = new ConnectionMongoSnapshotAdapter(
                DefaultFieldsEncryptionConfig.of(ConfigFactory.parseString("encryption.encryption-enabled = false")
                        .withFallback(config)));
        final Connection recovered =
                disabledSerializer.createJsonifiableFrom(JsonObject.of(encryptedSnapshot.toJson()));
        final BsonDocument rewrittenSnapshot = (BsonDocument)
                new NeutralSnapshotAdapter<>(disabledSerializer, MongoSnapshotCodec.INSTANCE)
                        .toSnapshotStore(recovered);

        assertThat(encryptedSnapshot.toJson()).contains("encrypted_");
        assertThat(URI.create(recovered.getUri()).getUserInfo()).isEqualTo(userInfo);
        assertThat(rewrittenSnapshot.toJson()).doesNotContain("encrypted_");
    }

    @Test
    public void disabledEncryptionWithOnlyTheOldKeyDecryptsAndWritesPlaintext() {
        // the MongoDB procedure for disabling encryption, now valid on both backends: encryption off, no current key,
        // the last key kept as 'old-symmetrical-key'; a snapshot written under that key still decrypts, and is
        // rewritten in plaintext
        final Config config = ConfigFactory.load("connection-fields-encryption-test").getConfig("connection");
        final Connection connection = TestConstants.createConnection();
        final String userInfo = URI.create(connection.getUri()).getUserInfo();
        final BsonDocument encryptedSnapshot = encryptWith(config, connection);

        final ConnectionMongoSnapshotAdapter disabledSerializer = new ConnectionMongoSnapshotAdapter(
                DefaultFieldsEncryptionConfig.of(ConfigFactory.parseString(
                                "encryption.encryption-enabled = false\n"
                                        + "encryption.symmetrical-key = \"\"\n"
                                        + "encryption.old-symmetrical-key = \"" + currentKeyOf(config) + "\"")
                        .withFallback(config)));
        final Connection recovered =
                disabledSerializer.createJsonifiableFrom(JsonObject.of(encryptedSnapshot.toJson()));
        final BsonDocument rewrittenSnapshot = (BsonDocument)
                new NeutralSnapshotAdapter<>(disabledSerializer, MongoSnapshotCodec.INSTANCE)
                        .toSnapshotStore(recovered);

        assertThat(encryptedSnapshot.toJson()).contains("encrypted_");
        assertThat(URI.create(recovered.getUri()).getUserInfo()).isEqualTo(userInfo);
        assertThat(rewrittenSnapshot.toJson()).doesNotContain("encrypted_");
    }

    @Test
    public void rotatedKeyDecryptsASnapshotWrittenUnderTheOldKeyThroughTheFallback() {
        // key rotation: a snapshot written under the former key decrypts through 'old-symmetrical-key' while the new
        // 'symmetrical-key' is current, and is rewritten under the new key
        final Config config = ConfigFactory.load("connection-fields-encryption-test").getConfig("connection");
        final Connection connection = TestConstants.createConnection();
        final String userInfo = URI.create(connection.getUri()).getUserInfo();
        final BsonDocument encryptedUnderOldKey = encryptWith(config, connection);

        final ConnectionMongoSnapshotAdapter rotatedSerializer = new ConnectionMongoSnapshotAdapter(
                DefaultFieldsEncryptionConfig.of(ConfigFactory.parseString(
                                "encryption.symmetrical-key = \"" + NEW_KEY + "\"\n"
                                        + "encryption.old-symmetrical-key = \"" + currentKeyOf(config) + "\"")
                        .withFallback(config)));
        final Connection recovered =
                rotatedSerializer.createJsonifiableFrom(JsonObject.of(encryptedUnderOldKey.toJson()));
        final BsonDocument rewrittenSnapshot = (BsonDocument)
                new NeutralSnapshotAdapter<>(rotatedSerializer, MongoSnapshotCodec.INSTANCE).toSnapshotStore(recovered);
        final Connection recoveredWithNewKeyOnly = new ConnectionMongoSnapshotAdapter(
                DefaultFieldsEncryptionConfig.of(ConfigFactory.parseString(
                        "encryption.symmetrical-key = \"" + NEW_KEY + "\"").withFallback(config)))
                .createJsonifiableFrom(JsonObject.of(rewrittenSnapshot.toJson()));

        assertThat(URI.create(recovered.getUri()).getUserInfo()).isEqualTo(userInfo);
        assertThat(rewrittenSnapshot.toJson()).contains("encrypted_");
        assertThat(URI.create(recoveredWithNewKeyOnly.getUri()).getUserInfo()).isEqualTo(userInfo);
    }

    @Test
    public void withoutAnyKeyEncryptedFieldsStayEncrypted() {
        // no key at all: the adapter cannot decrypt and must not fail; the ciphertext stays
        final Config config = ConfigFactory.load("connection-fields-encryption-test").getConfig("connection");
        final Connection connection = TestConstants.createConnection();
        final BsonDocument encryptedSnapshot = encryptWith(config, connection);

        final ConnectionMongoSnapshotAdapter keylessSerializer = new ConnectionMongoSnapshotAdapter(
                DefaultFieldsEncryptionConfig.of(ConfigFactory.parseString(
                                "encryption.encryption-enabled = false\nencryption.symmetrical-key = \"\"")
                        .withFallback(config)));
        final Connection recovered =
                keylessSerializer.createJsonifiableFrom(JsonObject.of(encryptedSnapshot.toJson()));

        assertThat(recovered.getUri()).contains("encrypted_");
    }

    @Test
    public void wrongOldKeyWithoutCurrentKeyFailsDecryption() {
        // the one input whose outcome the old-key-alone path changed: before, a missing current key kept the
        // ciphertext with a WARN whatever the old key was; now the old key alone decrypts, so a wrong one fails the
        // same way a wrong single current key does
        final Config config = ConfigFactory.load("connection-fields-encryption-test").getConfig("connection");
        final BsonDocument encryptedSnapshot = encryptWith(config, TestConstants.createConnection());
        final JsonObject encryptedSnapshotJson = JsonObject.of(encryptedSnapshot.toJson());

        final ConnectionMongoSnapshotAdapter wrongOldKeySerializer = new ConnectionMongoSnapshotAdapter(
                DefaultFieldsEncryptionConfig.of(ConfigFactory.parseString(
                                "encryption.encryption-enabled = false\n"
                                        + "encryption.symmetrical-key = \"\"\n"
                                        + "encryption.old-symmetrical-key = \"" + NEW_KEY + "\"")
                        .withFallback(config)));

        assertThatThrownBy(() -> wrongOldKeySerializer.createJsonifiableFrom(encryptedSnapshotJson))
                .isInstanceOf(ConnectionConfigurationInvalidException.class);
    }

    @Test
    public void blankOldKeyWithoutCurrentKeyCountsAsNoKeyAndKeepsTheCiphertext() {
        // boundary: a whitespace-only 'old-symmetrical-key' is no key, like the empty 'symmetrical-key'; the adapter
        // takes the no-key branch instead of trying to decrypt with it
        final Config config = ConfigFactory.load("connection-fields-encryption-test").getConfig("connection");
        final BsonDocument encryptedSnapshot = encryptWith(config, TestConstants.createConnection());

        final ConnectionMongoSnapshotAdapter blankKeySerializer = new ConnectionMongoSnapshotAdapter(
                DefaultFieldsEncryptionConfig.of(ConfigFactory.parseString(
                                "encryption.encryption-enabled = false\n"
                                        + "encryption.symmetrical-key = \"\"\n"
                                        + "encryption.old-symmetrical-key = \"   \"")
                        .withFallback(config)));
        final Connection recovered =
                blankKeySerializer.createJsonifiableFrom(JsonObject.of(encryptedSnapshot.toJson()));

        assertThat(recovered.getUri()).contains("encrypted_");
    }

    @Test
    public void plaintextSnapshotNeedsNoKey() {
        final Config config = ConfigFactory.load("connection-fields-encryption-test").getConfig("connection");
        final Connection connection = TestConstants.createConnection();
        final ConnectionMongoSnapshotAdapter keylessSerializer = new ConnectionMongoSnapshotAdapter(
                DefaultFieldsEncryptionConfig.of(ConfigFactory.parseString(
                                "encryption.encryption-enabled = false\nencryption.symmetrical-key = \"\"")
                        .withFallback(config)));
        final BsonDocument plaintextSnapshot = (BsonDocument)
                new NeutralSnapshotAdapter<>(keylessSerializer, MongoSnapshotCodec.INSTANCE)
                        .toSnapshotStore(connection);

        final Connection recovered =
                keylessSerializer.createJsonifiableFrom(JsonObject.of(plaintextSnapshot.toJson()));

        assertThat(plaintextSnapshot.toJson()).doesNotContain("encrypted_");
        assertThat(recovered.getUri()).isEqualTo(connection.getUri());
    }

    // a second valid AES-256 key for the rotation case (32 bytes, base64)
    private static final String NEW_KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    private static String currentKeyOf(final Config connectionConfig) {
        return connectionConfig.getString("encryption.symmetrical-key");
    }

    private static BsonDocument encryptWith(final Config connectionConfig, final Connection connection) {
        final ConnectionMongoSnapshotAdapter encryptingSerializer =
                new ConnectionMongoSnapshotAdapter(DefaultFieldsEncryptionConfig.of(connectionConfig));
        return (BsonDocument) new NeutralSnapshotAdapter<>(encryptingSerializer, MongoSnapshotCodec.INSTANCE)
                .toSnapshotStore(connection);
    }
}
