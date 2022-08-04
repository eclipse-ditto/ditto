/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
import org.eclipse.ditto.internal.models.signalenrichment.DefaultSignalEnrichmentProviderConfig;
import org.eclipse.ditto.internal.models.signalenrichment.DittoCachingSignalEnrichmentFacade;
import org.eclipse.ditto.internal.models.signalenrichment.SignalEnrichmentFacade;

import com.typesafe.config.Config;

import akka.actor.ActorSystem;

/**
 * Provider for Connectivity-service of signal-enriching facades that uses an async Caffeine cache in order to load
 * extra data to enrich.
 */
public final class DefaultConnectivitySignalEnrichmentProvider implements ConnectivitySignalEnrichmentProvider {

    private static final String COMMAND_FORWARDER_ACTOR_PATH =
            "/user/connectivityRoot/" + EdgeCommandForwarderActor.ACTOR_NAME;
    private static final String CACHE_DISPATCHER = "signal-enrichment-cache-dispatcher";
    private final SignalEnrichmentFacade facade;

    /**
     * Instantiate this provider. Called by reflection.
     *
     * @param actorSystem The actor system for which this provider is instantiated.
     */
    @SuppressWarnings("unused")
    public DefaultConnectivitySignalEnrichmentProvider(final ActorSystem actorSystem, final Config config) {
        final var commandHandler = actorSystem.actorSelection(COMMAND_FORWARDER_ACTOR_PATH);
        final var providerConfig = DefaultSignalEnrichmentProviderConfig.of(config);
        final var delegate = ByRoundTripSignalEnrichmentFacade.of(commandHandler, providerConfig.getAskTimeout());
        if (providerConfig.isCachingEnabled()) {
            final var cacheLoaderExecutor = actorSystem.dispatchers().lookup(CACHE_DISPATCHER);
            facade = DittoCachingSignalEnrichmentFacade.newInstance(
                    delegate,
                    providerConfig.getCacheConfig(),
                    cacheLoaderExecutor,
                    "connectivity");
        } else {
            facade = delegate;
        }

    }

    @Override
    public SignalEnrichmentFacade getFacade(final ConnectionId connectionId) {
        return facade;
    }

}
