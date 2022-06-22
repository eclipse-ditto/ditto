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
package org.eclipse.ditto.connectivity.service.mapping;

import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.edge.service.dispatching.EdgeCommandForwarderActor;
import org.eclipse.ditto.internal.models.signalenrichment.ByRoundTripSignalEnrichmentFacade;
import org.eclipse.ditto.internal.models.signalenrichment.DefaultSignalEnrichmentFacadeByRoundTripConfig;
import org.eclipse.ditto.internal.models.signalenrichment.SignalEnrichmentConfig;
import org.eclipse.ditto.internal.models.signalenrichment.SignalEnrichmentFacade;
import org.eclipse.ditto.internal.models.signalenrichment.SignalEnrichmentFacadeByRoundTripConfig;

import akka.actor.ActorSelection;
import akka.actor.ActorSystem;

/**
 * Provider for Connectivity-service of signal-enriching facades that make a round-trip for each query.
 */
public final class ConnectivityByRoundTripSignalEnrichmentProvider implements ConnectivitySignalEnrichmentProvider {

    private static final String COMMAND_FORWARDER_ACTOR_PATH =
            "/user/connectivityRoot/" + EdgeCommandForwarderActor.ACTOR_NAME;

    private final ByRoundTripSignalEnrichmentFacade byRoundTripSignalEnrichmentFacade;

    /**
     * Instantiate this provider. Called by reflection.
     *
     * @param actorSystem The actor system for which this provider is instantiated.
     * @param signalEnrichmentConfig Configuration for this provider.
     */
    @SuppressWarnings("unused")
    public ConnectivityByRoundTripSignalEnrichmentProvider(final ActorSystem actorSystem,
            final SignalEnrichmentConfig signalEnrichmentConfig) {
        final ActorSelection commandHandler = actorSystem.actorSelection(COMMAND_FORWARDER_ACTOR_PATH);
        final SignalEnrichmentFacadeByRoundTripConfig config =
                DefaultSignalEnrichmentFacadeByRoundTripConfig.of(signalEnrichmentConfig.getProviderConfig());
        byRoundTripSignalEnrichmentFacade =
                ByRoundTripSignalEnrichmentFacade.of(commandHandler, config.getAskTimeout());
    }

    @Override
    public SignalEnrichmentFacade getFacade(final ConnectionId connectionId) {
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
