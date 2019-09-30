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
package org.eclipse.ditto.services.connectivity.messaging.persistence.strategies.events;

import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.services.utils.persistentactors.events.AbstractEventStrategies;
import org.eclipse.ditto.signals.events.connectivity.ConnectionClosed;
import org.eclipse.ditto.signals.events.connectivity.ConnectionCreated;
import org.eclipse.ditto.signals.events.connectivity.ConnectionDeleted;
import org.eclipse.ditto.signals.events.connectivity.ConnectionModified;
import org.eclipse.ditto.signals.events.connectivity.ConnectionOpened;
import org.eclipse.ditto.signals.events.connectivity.ConnectivityEvent;

/**
 * Strategies to modify a connection by events.
 */
public final class ConnectionEventStrategies extends AbstractEventStrategies<ConnectivityEvent, Connection> {

    private static final ConnectionEventStrategies INSTANCE = new ConnectionEventStrategies();

    private ConnectionEventStrategies() {
        addStrategy(ConnectionCreated.class, new ConnectionCreatedStrategy());
        addStrategy(ConnectionModified.class, new ConnectionModifiedStrategy());
        addStrategy(ConnectionOpened.class, new ConnectionOpenedStrategy());
        addStrategy(ConnectionClosed.class, new ConnectionClosedStrategy());
        addStrategy(ConnectionDeleted.class, new ConnectionDeletedStrategy());
    }

    /**
     * @return the unique instance of this class.
     */
    public static ConnectionEventStrategies getInstance() {
        return INSTANCE;
    }

}
