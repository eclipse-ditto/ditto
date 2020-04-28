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

import org.eclipse.ditto.services.gateway.util.config.streaming.GatewaySignalEnrichmentConfig;
import org.eclipse.ditto.services.models.concierge.actors.ConciergeForwarderActor;
import org.eclipse.ditto.services.models.signalenrichment.ByRoundTripSignalEnrichmentFacade;
import org.eclipse.ditto.services.models.signalenrichment.SignalEnrichmentFacade;

import akka.actor.ActorSelection;
import akka.actor.ActorSystem;
import akka.http.javadsl.model.HttpRequest;

/**
 * Provider for gateway-service of signal-enriching facades that make a round-trip for each query.
 */
public final class GatewayByRoundTripSignalEnrichmentProvider implements GatewaySignalEnrichmentProvider {

    private static final String CONCIERGE_FORWARDER = "/user/gatewayRoot/" + ConciergeForwarderActor.ACTOR_NAME;

    private final ByRoundTripSignalEnrichmentFacade byRoundTripSignalEnrichmentFacade;

    /**
     * Instantiate this provider. Called by reflection.
     *
     * @param actorSystem The actor system for which this provider is instantiated.
     * @param signalEnrichmentConfig Configuration for this provider.
     */
    public GatewayByRoundTripSignalEnrichmentProvider(final ActorSystem actorSystem,
            final GatewaySignalEnrichmentConfig signalEnrichmentConfig) {
        final ActorSelection commandHandler = actorSystem.actorSelection(CONCIERGE_FORWARDER);
        byRoundTripSignalEnrichmentFacade =
                ByRoundTripSignalEnrichmentFacade.of(commandHandler, signalEnrichmentConfig.getAskTimeout());
    }

    @Override
    public SignalEnrichmentFacade getFacade(final HttpRequest request) {
        return getByRoundTripSignalEnrichmentFacade();
    }

    /**
     * Package-private getter for the unique by-round-trip signal enrichment facade per provider instance.
     *
     * @return the unique by-round-trip signal enrichment facade.
     */
    ByRoundTripSignalEnrichmentFacade getByRoundTripSignalEnrichmentFacade() {
        return byRoundTripSignalEnrichmentFacade;
    }

}
