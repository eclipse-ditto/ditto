/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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

import java.util.Optional;

import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionLifecycle;
import org.eclipse.ditto.connectivity.service.config.DittoConnectivityConfig;
import org.eclipse.ditto.connectivity.service.config.FieldsEncryptionConfig;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.persistence.api.serializer.SnapshotSerializer;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;

import org.apache.pekko.actor.ActorSystem;

/**
 * The {@link SnapshotSerializer} for a {@link Connection}: the pure domain&harr;{@code JsonObject} logic, including the
 * connection fields encrypt/decrypt hooks. It is backend-neutral; the storage envelope (Mongo BSON / Postgres JSONB) is
 * supplied separately by the active backend's {@code SnapshotCodec} and combined with this serializer in
 * {@code org.eclipse.ditto.internal.utils.persistence.api.serializer.NeutralSnapshotAdapter}.
 * <p>
 * The {@code MongoSnapshotAdapter} class name and the {@code ditto.extensions.snapshot-adapter} key are unchanged, so
 * existing MongoDB configurations keep working; the class is now purely a serializer without BSON-specific code.
 */
public final class ConnectionMongoSnapshotAdapter extends SnapshotSerializer<Connection> {

    public static final Logger LOGGER = LoggerFactory.getLogger(ConnectionMongoSnapshotAdapter.class);
    private final FieldsEncryptionConfig encryptionConfig;

    /**
     * @param actorSystem the actor system in which to load the extension
     * @param config the config of the extension.
     */
    @SuppressWarnings("unused")
    public ConnectionMongoSnapshotAdapter(final ActorSystem actorSystem, final Config config) {
        this(DittoConnectivityConfig.of(
                        DefaultScopedConfig.dittoScoped(actorSystem.settings().config()))
                .getConnectionConfig().getFieldsEncryptionConfig());
    }

    public ConnectionMongoSnapshotAdapter(FieldsEncryptionConfig encryptionConfig) {
        super(LOGGER);
        this.encryptionConfig = encryptionConfig;
        LOGGER.info("Connections fields encryption: {}", encryptionConfig.isEncryptionEnabled());
        if (encryptionConfig.isEncryptionEnabled()) {
            LOGGER.debug("Connections fields that will be encryption: {}", encryptionConfig.getJsonPointers());
        }
    }

    @Override
    protected boolean isDeleted(final Connection snapshotEntity) {
        return snapshotEntity.hasLifecycle(ConnectionLifecycle.DELETED);
    }

    @Override
    protected JsonField getDeletedLifecycleJsonField() {
        final var field = Connection.JsonFields.LIFECYCLE;
        return JsonField.newInstance(field.getPointer().getRoot().orElseThrow(),
                JsonValue.of(ConnectionLifecycle.DELETED.name()), field);
    }

    @Override
    protected Optional<JsonField> getRevisionJsonField(final Connection entity) {
        return Optional.empty();
    }

    @Override
    protected Connection createJsonifiableFrom(final JsonObject jsonObject) {
        final boolean hasEncryptedFields = encryptionConfig.getJsonPointers().stream()
                .map(JsonPointer::of)
                .flatMap(p -> jsonObject.getValue(p).filter(JsonValue::isString).map(JsonValue::asString).stream())
                .anyMatch(v -> v.contains(JsonFieldsEncryptor.ENCRYPTED_PREFIX));

        if (!hasEncryptedFields) {
            return ConnectionMigrationUtil.connectionFromJsonWithMigration(jsonObject);
        }

        // Mirrors the event adapters: the current key is primary and the old key its fallback; without a current key
        // (encryption disabled, the last key kept as 'old-symmetrical-key') the old key alone decrypts.
        final Optional<String> currentKey = encryptionConfig.getSymmetricalKey();
        final Optional<String> oldKey = encryptionConfig.getOldSymmetricalKey();
        if (currentKey.isEmpty() && oldKey.isEmpty()) {
            LOGGER.warn("Encrypted fields will not be decrypted. Missing symmetrical key. Either configure the key "
                    + "used for encryption as 'symmetrical-key' or 'old-symmetrical-key', or edit the connections "
                    + "and update the encrypted fields.");
            return ConnectionMigrationUtil.connectionFromJsonWithMigration(jsonObject);
        }
        final String primaryKey = currentKey.orElseGet(oldKey::get);
        final Optional<String> fallbackKey = currentKey.isPresent() ? oldKey : Optional.empty();
        final JsonObject decrypted = JsonFieldsEncryptor.decrypt(jsonObject, "", encryptionConfig.getJsonPointers(),
                primaryKey, fallbackKey);
        return ConnectionMigrationUtil.connectionFromJsonWithMigration(decrypted);
    }

    @Override
    protected JsonObject convertToJson(final Connection snapshotEntity) {
        if (encryptionConfig.isEncryptionEnabled() && isCurrentKeyConfigured()) {
            final JsonObject jsonObject = super.convertToJson(snapshotEntity);
            return JsonFieldsEncryptor.encrypt(jsonObject, "", encryptionConfig.getJsonPointers(),
                    encryptionConfig.getSymmetricalKey().orElseThrow());
        }
        return super.convertToJson(snapshotEntity);
    }

    private boolean isCurrentKeyConfigured() {
        final Optional<String> symmetricalKey = encryptionConfig.getSymmetricalKey();
        return symmetricalKey.isPresent() && !symmetricalKey.get().isEmpty();
    }
}
