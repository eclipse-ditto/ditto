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
package org.eclipse.ditto.gateway.service.endpoints.utils;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.edge.api.dispatching.EdgeCommandForwarderActor;
import org.eclipse.ditto.gateway.service.util.config.streaming.GatewaySignalEnrichmentConfig;
import org.eclipse.ditto.internal.models.signalenrichment.ByRoundTripSignalEnrichmentFacade;
import org.eclipse.ditto.internal.models.signalenrichment.SignalEnrichmentFacade;

import akka.actor.ActorSelection;
import akka.actor.ActorSystem;
import akka.http.javadsl.model.HttpRequest;

/**
 * Provider for gateway-service of signal-enriching facades that make a round-trip for each query.
 */
public final class GatewayByRoundTripSignalEnrichmentProvider implements GatewaySignalEnrichmentProvider {

    private static final String CONCIERGE_FORWARDER = "/user/gatewayRoot/" + EdgeCommandForwarderActor.ACTOR_NAME;

    private final ByRoundTripSignalEnrichmentFacade byRoundTripSignalEnrichmentFacade;

    /**
     * Instantiate this provider. Called by reflection.
     *
     * @param actorSystem The actor system for which this provider is instantiated.
     */
    public GatewayByRoundTripSignalEnrichmentProvider(final ActorSystem actorSystem) {
        final ActorSelection commandHandler = actorSystem.actorSelection(CONCIERGE_FORWARDER);
        final GatewaySignalEnrichmentConfig signalEnrichmentConfig = getSignalEnrichmentConfig(actorSystem);
        final Duration askTimeout = signalEnrichmentConfig.getAskTimeout();
        byRoundTripSignalEnrichmentFacade = ByRoundTripSignalEnrichmentFacade.of(commandHandler, askTimeout);
    }

    @Override
    public CompletionStage<SignalEnrichmentFacade> getFacade(final HttpRequest request) {
        return CompletableFuture.completedStage(getByRoundTripSignalEnrichmentFacade());
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
