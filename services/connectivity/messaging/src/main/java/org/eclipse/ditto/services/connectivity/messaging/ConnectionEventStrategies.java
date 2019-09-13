/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.connectivity.messaging;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionLifecycle;
import org.eclipse.ditto.model.connectivity.ConnectivityStatus;
import org.eclipse.ditto.services.utils.persistentactors.events.AbstractHandleStrategy;
import org.eclipse.ditto.services.utils.persistentactors.events.EventStrategy;
import org.eclipse.ditto.signals.events.connectivity.ConnectionClosed;
import org.eclipse.ditto.signals.events.connectivity.ConnectionCreated;
import org.eclipse.ditto.signals.events.connectivity.ConnectionDeleted;
import org.eclipse.ditto.signals.events.connectivity.ConnectionModified;
import org.eclipse.ditto.signals.events.connectivity.ConnectionOpened;
import org.eclipse.ditto.signals.events.connectivity.ConnectivityEvent;

/**
 * TODO
 */
public final class ConnectionEventStrategies extends AbstractHandleStrategy<ConnectivityEvent, Connection> {

    private static final ConnectionEventStrategies INSTANCE = new ConnectionEventStrategies();

    private ConnectionEventStrategies() {
        addStrategy(ConnectionCreated.class, new ConnectionCreatedStrategy());
        addStrategy(ConnectionModified.class, new ConnectionModifiedStrategy());
        addStrategy(ConnectionOpened.class, new ConnectionOpenedStrategy());
        addStrategy(ConnectionClosed.class, new ConnectionClosedStrategy());
        addStrategy(ConnectionDeleted.class, new ConnectionDeletedStrategy());
    }

    // TODO
    public static ConnectionEventStrategies getInstance() {
        return INSTANCE;
    }

    private static final class ConnectionCreatedStrategy implements EventStrategy<ConnectionCreated, Connection> {

        @Override
        public Connection handle(final ConnectionCreated event, @Nullable final Connection entity,
                final long revision) {

            return event.getConnection();
        }
    }

    private static final class ConnectionModifiedStrategy implements EventStrategy<ConnectionModified, Connection> {

        @Override
        public Connection handle(final ConnectionModified event, @Nullable final Connection entity,
                final long revision) {

            return event.getConnection();
        }
    }

    private static final class ConnectionOpenedStrategy implements EventStrategy<ConnectionOpened, Connection> {

        @Override
        public Connection handle(final ConnectionOpened event, @Nullable final Connection connection,
                final long revision) {
            checkNotNull(connection, "connection");
            return connection.toBuilder().connectionStatus(ConnectivityStatus.OPEN).build();
        }
    }

    private static final class ConnectionClosedStrategy implements EventStrategy<ConnectionClosed, Connection> {

        @Override
        public Connection handle(final ConnectionClosed event, @Nullable final Connection connection,
                final long revision) {
            checkNotNull(connection, "connection");
            return connection.toBuilder().connectionStatus(ConnectivityStatus.CLOSED).build();
        }
    }

    private static final class ConnectionDeletedStrategy implements EventStrategy<ConnectionDeleted, Connection> {

        @Nullable
        @Override
        public Connection handle(final ConnectionDeleted event, @Nullable final Connection connection,
                final long revision) {
            if (connection != null) {
                return connection.toBuilder().lifecycle(ConnectionLifecycle.DELETED).build();
            } else {
                return null;
            }
        }
    }
}
