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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.events.Event;
import org.eclipse.ditto.connectivity.model.ConnectionId;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;

/**
 * Default implementation of {@link ConnectionConfigProvider} which simply builds and returns a
 * {@link ConnectivityConfig}.
 */
public class DittoConnectionConfigProvider implements ConnectionConfigProvider {

    public DittoConnectionConfigProvider(final ActorSystem actorSystem) {
    }

    @Override
    public CompletionStage<Config> getConnectivityConfigOverwrites(final ConnectionId connectionId,
            @Nullable final DittoHeaders dittoHeaders) {

        return CompletableFuture.completedFuture(ConfigFactory.empty());
    }

    @Override
    public CompletionStage<Void> registerForConnectivityConfigChanges(final ConnectionId connectionId,
            @Nullable final DittoHeaders dittoHeaders, final ActorRef subscriber) {

        // nothing to do, config changes are not supported by the default implementation
        return CompletableFuture.completedStage(null);
    }

    @Override
    public boolean canHandle(final Event<?> event) {
        return false;
    }

    @Override
    public void handleEvent(final Event<?> event, final ActorRef supervisorActor,
            @Nullable final ActorRef persistenceActor) {
        // By default not handled in Ditto
    }

}
