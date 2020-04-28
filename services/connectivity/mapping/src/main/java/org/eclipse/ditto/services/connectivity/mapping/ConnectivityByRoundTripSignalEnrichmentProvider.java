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

import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.services.models.signalenrichment.ByRoundTripSignalEnrichmentFacade;
import org.eclipse.ditto.services.models.signalenrichment.DefaultSignalEnrichmentFacadeByRoundTripConfig;
import org.eclipse.ditto.services.models.signalenrichment.SignalEnrichmentConfig;
import org.eclipse.ditto.services.models.signalenrichment.SignalEnrichmentFacade;
import org.eclipse.ditto.services.models.signalenrichment.SignalEnrichmentFacadeByRoundTripConfig;

import akka.actor.ActorSelection;
import akka.actor.ActorSystem;

/**
 * Provider for Connectivity-service of signal-enriching facades that make a round-trip for each query.
 */
public final class ConnectivityByRoundTripSignalEnrichmentProvider extends ConnectivitySignalEnrichmentProvider {

    private static final String CONCIERGE_FORWARDER = "/user/connectivityRoot/conciergeForwarder";

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
        final ActorSelection commandHandler = actorSystem.actorSelection(CONCIERGE_FORWARDER);
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
