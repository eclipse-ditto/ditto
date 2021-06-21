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
package org.eclipse.ditto.connectivity.service.mapping;

import java.util.Objects;

import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;

/**
 * Implementation of {@link ConnectionContext}.
 */
public final class DittoConnectionContext implements ConnectionContext {

    private final Connection connection;
    private final ConnectivityConfig connectivityConfig;

    private DittoConnectionContext(final Connection connection, final ConnectivityConfig connectivityConfig) {
        this.connection = connection;
        this.connectivityConfig = connectivityConfig;
    }

    /**
     * Create a connection context from a connection and its connectivity config.
     *
     * @param connection the connection.
     * @param config the connectivity config.
     * @return the connection context.
     */
    public static DittoConnectionContext of(final Connection connection, final ConnectivityConfig config) {
        return new DittoConnectionContext(connection, config);
    }

    @Override
    public Connection getConnection() {
        return connection;
    }

    @Override
    public ConnectivityConfig getConnectivityConfig() {
        return connectivityConfig;
    }

    @Override
    public ConnectionContext withConnectivityConfig(final ConnectivityConfig modifiedConfig) {
        return new DittoConnectionContext(connection, modifiedConfig);
    }

    @Override
    public ConnectionContext withConnection(final Connection modifiedConnection) {
        return new DittoConnectionContext(modifiedConnection, connectivityConfig);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() +
                "[connection=" + connection +
                ",connectivityConfig=" + connectivityConfig +
                "]";
    }

    @Override
    public boolean equals(final Object other) {
        if (other instanceof DittoConnectionContext) {
            final var that = (DittoConnectionContext) other;
            return Objects.equals(connection, that.connection) &&
                    Objects.equals(connectivityConfig, that.connectivityConfig);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(connection, connectivityConfig);
    }
}
