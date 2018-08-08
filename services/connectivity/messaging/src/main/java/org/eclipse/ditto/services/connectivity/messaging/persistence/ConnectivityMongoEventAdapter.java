/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.connectivity.messaging.persistence;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.services.utils.persistence.mongo.AbstractMongoEventAdapter;
import org.eclipse.ditto.signals.base.JsonParsable;
import org.eclipse.ditto.signals.events.base.EventRegistry;
import org.eclipse.ditto.signals.events.connectivity.ConnectionCreated;
import org.eclipse.ditto.signals.events.connectivity.ConnectionModified;
import org.eclipse.ditto.signals.events.connectivity.ConnectivityEvent;
import org.eclipse.ditto.signals.events.connectivity.ConnectivityEventRegistry;

import akka.actor.ExtendedActorSystem;

/**
 * EventAdapter for {@link ConnectivityEvent}s persisted into
 * akka-persistence event-journal. Converts Events to MongoDB BSON objects and vice versa.
 */
public final class ConnectivityMongoEventAdapter extends AbstractMongoEventAdapter<ConnectivityEvent> {

    public ConnectivityMongoEventAdapter(final ExtendedActorSystem system) {
        super(system, createEventRegistry());
    }

    private static EventRegistry<ConnectivityEvent> createEventRegistry() {

        final Map<String, JsonParsable<ConnectivityEvent>> parseStrategies = new HashMap<>();
        parseStrategies.put(ConnectionCreated.TYPE, (jsonObject, dittoHeaders) -> {
            final Connection connection = ConnectionMigrationUtil.connectionFromJsonWithMigration(
                    jsonObject.getValueOrThrow(ConnectionCreated.JsonFields.CONNECTION));
            return ConnectionCreated.of(connection, dittoHeaders);
        });
        parseStrategies.put(ConnectionModified.TYPE, (jsonObject, dittoHeaders) -> {
            final Connection connection = ConnectionMigrationUtil.connectionFromJsonWithMigration(
                    jsonObject.getValueOrThrow(ConnectionCreated.JsonFields.CONNECTION)
            );
            return ConnectionModified.of(connection, dittoHeaders);
        });
        return ConnectivityEventRegistry.newInstance(parseStrategies);
    }
}
