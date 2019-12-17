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
package org.eclipse.ditto.services.connectivity.mapping;

import java.time.Duration;

import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.services.models.things.ThingEnrichingFacade;
import org.eclipse.ditto.services.models.things.ThingEnrichingFacadeByRoundTrip;

import com.typesafe.config.Config;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;

/**
 * Provider for Connectivity-service of thing-enriching facades that make a round-trip for each query.
 */
public final class ConnectionByRoundTripProvider implements ConnectionEnrichmentProvider {

    private final ActorRef commandHandler;
    private final Duration askTimeout;

    /**
     * Instantiate this provider. Called by reflection.
     *
     * @param actorSystem The actor system for which this provider is instantiated.
     * @param commandHandler The recipient of retrieve-thing commands.
     * @param config Configuration for this provider.
     */
    @SuppressWarnings("unused")
    public ConnectionByRoundTripProvider(final ActorSystem actorSystem, final ActorRef commandHandler,
            final Config config) {
        this.commandHandler = commandHandler;
        askTimeout = config.getDuration("ask-timeout");
    }

    @Override
    public ThingEnrichingFacade createFacade(final ConnectionId connectionId) {
        return ThingEnrichingFacadeByRoundTrip.of(commandHandler, askTimeout);
    }
}
