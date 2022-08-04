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
package org.eclipse.ditto.connectivity.service.messaging.persistence.stages;

import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.signals.commands.ConnectivityCommandInterceptor;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.logs.ConnectionLogger;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.logs.ConnectionLoggerRegistry;

/**
 * Everything needed by connection strategies from the state of a connection actor.
 */
public final class ConnectionState {

    private final ConnectionId connectionId;
    private final ConnectionLoggerRegistry connectionLoggerRegistry;
    private final ConnectionLogger connectionLogger;
    private final ConnectivityCommandInterceptor validator;

    private ConnectionState(final ConnectionId connectionId,
            final ConnectionLoggerRegistry connectionLoggerRegistry,
            final ConnectionLogger connectionLogger,
            final ConnectivityCommandInterceptor validator) {
        this.connectionId = connectionId;
        this.connectionLoggerRegistry = connectionLoggerRegistry;
        this.connectionLogger = connectionLogger;
        this.validator = validator;
    }

    /**
     * Create a connection state.
     *
     * @param connectionId the connection ID.
     * @param connectionLoggerRegistry the registry for connection loggers.
     * @param connectionLogger the logger for public consumption.
     * @param validator the current command validator.
     * @return the connection state.
     */
    public static ConnectionState of(
            final ConnectionId connectionId,
            final ConnectionLoggerRegistry connectionLoggerRegistry,
            final ConnectionLogger connectionLogger,
            final ConnectivityCommandInterceptor validator) {

        return new ConnectionState(connectionId, connectionLoggerRegistry, connectionLogger, validator);
    }

    /**
     * @return the connection ID.
     */
    public ConnectionId id() {
        return connectionId;
    }

    /**
     * @return the public logger.
     */
    public ConnectionLogger getConnectionLogger() {
        return connectionLogger;
    }

    /**
     * @return the registry of connection loggers.
     */
    public ConnectionLoggerRegistry getConnectionLoggerRegistry() {
        return connectionLoggerRegistry;
    }

    /**
     * @return the command validator.
     */
    public ConnectivityCommandInterceptor getValidator() {
        return validator;
    }
}
