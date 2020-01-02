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
package org.eclipse.ditto.services.gateway.endpoints.utils;

import org.eclipse.ditto.services.base.config.SignalEnrichmentConfig;
import org.eclipse.ditto.services.models.things.DefaultSignalEnrichmentFacadeByRoundTripConfig;
import org.eclipse.ditto.services.models.things.SignalEnrichmentFacade;
import org.eclipse.ditto.services.models.things.SignalEnrichmentFacadeByRoundTrip;
import org.eclipse.ditto.services.models.things.SignalEnrichmentFacadeByRoundTripConfig;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.http.javadsl.model.HttpRequest;

/**
 * Provider for gateway-service of thing-enriching facades that make a round-trip for each query.
 */
public final class GatewayByRoundTripProvider implements GatewaySignalEnrichmentProvider {

    private final ActorRef commandHandler;
    private final SignalEnrichmentFacadeByRoundTripConfig signalEnrichmentFacadeByRoundTripConfig;

    /**
     * Instantiate this provider. Called by reflection.
     *
     * @param actorSystem The actor system for which this provider is instantiated.
     * @param commandHandler The recipient of retrieve-thing commands.
     * @param signalEnrichmentConfig Configuration for this provider.
     */
    @SuppressWarnings("unused")
    public GatewayByRoundTripProvider(final ActorSystem actorSystem, final ActorRef commandHandler,
            final SignalEnrichmentConfig signalEnrichmentConfig) {
        this.commandHandler = commandHandler;
        signalEnrichmentFacadeByRoundTripConfig =
                DefaultSignalEnrichmentFacadeByRoundTripConfig.of(signalEnrichmentConfig.getProviderConfig());
    }

    @Override
    public SignalEnrichmentFacade createFacade(final HttpRequest request) {
        return SignalEnrichmentFacadeByRoundTrip.of(commandHandler,
                signalEnrichmentFacadeByRoundTripConfig.getAskTimeout());
    }
}
