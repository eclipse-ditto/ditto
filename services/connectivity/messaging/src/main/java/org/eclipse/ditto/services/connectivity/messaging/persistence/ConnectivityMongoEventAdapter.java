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
package org.eclipse.ditto.services.connectivity.messaging.persistence;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.services.utils.persistence.mongo.AbstractMongoEventAdapter;
import org.eclipse.ditto.signals.base.JsonParsable;
import org.eclipse.ditto.signals.events.base.EventRegistry;
import org.eclipse.ditto.signals.events.base.GlobalEventRegistry;
import org.eclipse.ditto.signals.events.connectivity.ConnectionCreated;
import org.eclipse.ditto.signals.events.connectivity.ConnectionModified;
import org.eclipse.ditto.signals.events.connectivity.ConnectivityEvent;

import akka.actor.ExtendedActorSystem;

/**
 * EventAdapter for {@link ConnectivityEvent}s persisted into
 * akka-persistence event-journal. Converts Events to MongoDB BSON objects and vice versa.
 */
public final class ConnectivityMongoEventAdapter extends AbstractMongoEventAdapter<ConnectivityEvent<?>> {

    public ConnectivityMongoEventAdapter(@Nullable final ExtendedActorSystem system) {
        super(system, createEventRegistry());
    }

    private static EventRegistry<ConnectivityEvent<?>> createEventRegistry() {

        final Map<String, JsonParsable<ConnectivityEvent<?>>> parseStrategies = new HashMap<>();
        parseStrategies.put(ConnectionCreated.TYPE, (jsonObject, dittoHeaders) -> {
            final Connection connection = ConnectionMigrationUtil.connectionFromJsonWithMigration(
                    jsonObject.getValueOrThrow(ConnectivityEvent.JsonFields.CONNECTION));
            return ConnectionCreated.of(connection, dittoHeaders);
        });
        parseStrategies.put(ConnectionModified.TYPE, (jsonObject, dittoHeaders) -> {
            final Connection connection = ConnectionMigrationUtil.connectionFromJsonWithMigration(
                    jsonObject.getValueOrThrow(ConnectivityEvent.JsonFields.CONNECTION)
            );
            return ConnectionModified.of(connection, dittoHeaders);
        });
        final GlobalEventRegistry<ConnectivityEvent<?>> globalEventRegistry = GlobalEventRegistry.getInstance();
        return globalEventRegistry.customize(parseStrategies);
    }

}
