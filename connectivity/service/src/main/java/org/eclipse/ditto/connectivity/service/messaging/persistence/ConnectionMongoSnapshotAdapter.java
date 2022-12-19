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
import org.eclipse.ditto.internal.utils.persistence.mongo.AbstractMongoSnapshotAdapter;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;

import akka.actor.ActorSystem;

/**
 * SnapshotAdapter for {@link String}s persisted to/from MongoDB.
 */
public final class ConnectionMongoSnapshotAdapter extends AbstractMongoSnapshotAdapter<Connection> {

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
        if (encryptionConfig.getSymmetricalKey().isEmpty()) {
            if(jsonObject.formatAsString().contains(JsonFieldsEncryptor.ENCRYPTED_PREFIX)){
                LOGGER.warn("Encrypted fields will not be decrypted. Missing symmetrical key. " +
                        "Either configure the one used for encryption or edit connections and update encrypted fields");
            }
            return ConnectionMigrationUtil.connectionFromJsonWithMigration(jsonObject);
        }
        final JsonObject decrypted = JsonFieldsEncryptor.decrypt(jsonObject, "", encryptionConfig.getJsonPointers(),
                encryptionConfig.getSymmetricalKey());
        return ConnectionMigrationUtil.connectionFromJsonWithMigration(decrypted);
    }

    @Override
    protected JsonObject convertToJson(final Connection snapshotEntity) {
        if (encryptionConfig.isEncryptionEnabled()) {
            final JsonObject jsonObject = super.convertToJson(snapshotEntity);
            return JsonFieldsEncryptor.encrypt(jsonObject, "", encryptionConfig.getJsonPointers(),
                    encryptionConfig.getSymmetricalKey());
        }
        return super.convertToJson(snapshotEntity);
    }
}
