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

import java.util.HashMap;
import java.util.Map;

import akka.actor.ExtendedActorSystem;
import org.eclipse.ditto.base.model.signals.JsonParsable;
import org.eclipse.ditto.base.model.signals.events.Event;
import org.eclipse.ditto.base.model.signals.events.EventJsonDeserializer;
import org.eclipse.ditto.base.model.signals.events.EventRegistry;
import org.eclipse.ditto.base.model.signals.events.GlobalEventRegistry;
import org.eclipse.ditto.base.service.config.DittoServiceConfig;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectivityConstants;
import org.eclipse.ditto.connectivity.model.signals.events.ConnectionCreated;
import org.eclipse.ditto.connectivity.model.signals.events.ConnectionModified;
import org.eclipse.ditto.connectivity.model.signals.events.ConnectivityEvent;
import org.eclipse.ditto.connectivity.service.config.DefaultConnectionConfig;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.AbstractMongoEventAdapter;
import org.eclipse.ditto.connectivity.service.config.DittoConnectivityConfig;
import org.eclipse.ditto.connectivity.service.config.FieldsEncryptionConfig;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonObjectBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * EventAdapter for {@link ConnectivityEvent}s persisted into
 * akka-persistence event-journal. Converts Events to MongoDB BSON objects and vice versa.
 */
public final class ConnectivityMongoEventAdapter extends AbstractMongoEventAdapter<ConnectivityEvent<?>> {

    private final FieldsEncryptionConfig encryptionConfig;
    private final Logger logger;

    public ConnectivityMongoEventAdapter(final ExtendedActorSystem system) {
        super(system, createEventRegistry(), DefaultConnectionConfig.of(
                        DittoServiceConfig.of(DefaultScopedConfig.dittoScoped(system.settings().config()), "connectivity"))
                .getEventConfig());
        logger = LoggerFactory.getLogger(ConnectivityMongoEventAdapter.class);
        final DittoConnectivityConfig connectivityConfig = DittoConnectivityConfig.of(
                DefaultScopedConfig.dittoScoped(system.settings().config()));
        encryptionConfig = connectivityConfig.getConnectionConfig().getFieldsEncryptionConfig();
        logger.info("Connections fields encryption: {}", encryptionConfig.isEncryptionEnabled());
        if (encryptionConfig.isEncryptionEnabled()) {
            logger.debug("Connections fields that will be encryption: {}", encryptionConfig.getJsonPointers());
        }
    }

    @Override
    protected JsonObjectBuilder performToJournalMigration(final Event<?> event, final JsonObject jsonObject) {
        if (encryptionConfig.isEncryptionEnabled()) {
            final JsonObject superObject = super.performToJournalMigration(event, jsonObject).build();
            return JsonFieldsEncryptor.encrypt(superObject, ConnectivityConstants.ENTITY_TYPE.toString(),
                    encryptionConfig.getJsonPointers(), encryptionConfig.getSymmetricalKey())
                    .toBuilder();
        }
        return super.performToJournalMigration(event, jsonObject);
    }

    @Override
    protected JsonObject performFromJournalMigration(final JsonObject jsonObject) {
        return JsonFieldsEncryptor.decrypt(jsonObject, ConnectivityConstants.ENTITY_TYPE.toString(),
                encryptionConfig.getJsonPointers(), encryptionConfig.getSymmetricalKey());
    }

    private static EventRegistry<ConnectivityEvent<?>> createEventRegistry() {

        final Map<String, JsonParsable<ConnectivityEvent<?>>> parseStrategies = new HashMap<>();
        parseStrategies.put(ConnectionCreated.TYPE, (jsonObject, dittoHeaders) ->
                new EventJsonDeserializer<ConnectionCreated>(ConnectionCreated.TYPE, jsonObject)
                        .deserialize((revision, timestamp, metadata) -> {
                            final Connection connection = ConnectionMigrationUtil.connectionFromJsonWithMigration(
                                    jsonObject.getValueOrThrow(ConnectivityEvent.JsonFields.CONNECTION));
                            return ConnectionCreated.of(connection, revision, timestamp, dittoHeaders, metadata);
                        }));
        parseStrategies.put(ConnectionModified.TYPE, (jsonObject, dittoHeaders) ->
                new EventJsonDeserializer<ConnectionModified>(ConnectionModified.TYPE, jsonObject)
                        .deserialize((revision, timestamp, metadata) -> {
                            final Connection connection = ConnectionMigrationUtil.connectionFromJsonWithMigration(
                                    jsonObject.getValueOrThrow(ConnectivityEvent.JsonFields.CONNECTION));
                            return ConnectionModified.of(connection, revision, timestamp, dittoHeaders, metadata);
                        }));
        final GlobalEventRegistry<ConnectivityEvent<?>> globalEventRegistry = GlobalEventRegistry.getInstance();
        return globalEventRegistry.customize(parseStrategies);
    }

}
