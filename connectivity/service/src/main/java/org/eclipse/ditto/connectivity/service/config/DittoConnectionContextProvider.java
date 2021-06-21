/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.config;

import java.util.Optional;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.events.Event;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.service.mapping.ConnectionContext;
import org.eclipse.ditto.connectivity.service.mapping.DittoConnectionContext;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;

/**
 * Default implementation of {@link ConnectionContextProvider} which simply builds and returns a
 * {@link org.eclipse.ditto.connectivity.service.mapping.DittoConnectionContext}.
 */
public class DittoConnectionContextProvider implements ConnectionContextProvider {

    private final DittoConnectivityConfig connectivityConfig;

    public DittoConnectionContextProvider(final ActorSystem actorSystem) {
        this.connectivityConfig =
                DittoConnectivityConfig.of(DefaultScopedConfig.dittoScoped(actorSystem.settings().config()));
    }

    @Override
    public ConnectionContext getConnectionContext(final Connection connection, final DittoHeaders dittoHeaders) {
        return DittoConnectionContext.of(connection, connectivityConfig);
    }

    @Override
    public void registerForConnectivityConfigChanges(final ConnectionId connectionId, final ActorRef subscriber) {
        // nothing to do, config changes are not supported by the default implementation
    }

    @Override
    public boolean canHandle(final Event<?> event) {
        return false;
    }

    @Override
    public Optional<ConnectivityConfig> handleEvent(final Event<?> event) {
        return Optional.empty();
    }
}
