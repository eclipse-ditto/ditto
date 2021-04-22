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
package org.eclipse.ditto.services.connectivity.config;

import java.util.Optional;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.services.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.base.model.signals.events.Event;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;

/**
 * Default implementation of {@link ConnectivityConfigProvider} which simply builds and returns a
 * {@link DittoConnectivityConfig}.
 */
public class DittoConnectivityConfigProvider implements ConnectivityConfigProvider {

    private final DittoConnectivityConfig connectivityConfig;

    public DittoConnectivityConfigProvider(final ActorSystem actorSystem) {
        this.connectivityConfig =
                DittoConnectivityConfig.of(DefaultScopedConfig.dittoScoped(actorSystem.settings().config()));
    }

    @Override
    public ConnectivityConfig getConnectivityConfig(final ConnectionId connectionId, final DittoHeaders dittoHeaders) {
        return connectivityConfig;
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
