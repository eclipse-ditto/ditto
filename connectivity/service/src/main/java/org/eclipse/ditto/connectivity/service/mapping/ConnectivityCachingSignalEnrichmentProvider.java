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

import java.util.concurrent.Executor;

import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.internal.models.signalenrichment.CachingSignalEnrichmentFacadeConfig;
import org.eclipse.ditto.internal.models.signalenrichment.CachingSignalEnrichmentFacadeProvider;
import org.eclipse.ditto.internal.models.signalenrichment.DefaultCachingSignalEnrichmentFacadeConfig;
import org.eclipse.ditto.internal.models.signalenrichment.SignalEnrichmentConfig;
import org.eclipse.ditto.internal.models.signalenrichment.SignalEnrichmentFacade;

import akka.actor.ActorSystem;

/**
 * Provider for Connectivity-service of signal-enriching facades that uses an async Caffeine cache in order to load
 * extra data to enrich.
 */
public final class ConnectivityCachingSignalEnrichmentProvider implements ConnectivitySignalEnrichmentProvider {

    private final SignalEnrichmentFacade cachingSignalEnrichmentFacade;

    /**
     * Instantiate this provider. Called by reflection.
     *
     * @param actorSystem The actor system for which this provider is instantiated.
     */
    @SuppressWarnings("unused")
    public ConnectivityCachingSignalEnrichmentProvider(final ActorSystem actorSystem,
            final SignalEnrichmentConfig signalEnrichmentConfig) {

        final ConnectivityByRoundTripSignalEnrichmentProvider cacheLoaderProvider =
                new ConnectivityByRoundTripSignalEnrichmentProvider(actorSystem, signalEnrichmentConfig);
        final CachingSignalEnrichmentFacadeConfig cachingSignalEnrichmentFacadeConfig =
                DefaultCachingSignalEnrichmentFacadeConfig.of(signalEnrichmentConfig.getProviderConfig());
        final Executor cacheLoaderExecutor = actorSystem.dispatchers().lookup("signal-enrichment-cache-dispatcher");
        final var signalEnrichmentFacadeProvider = CachingSignalEnrichmentFacadeProvider.get(actorSystem);
        cachingSignalEnrichmentFacade = signalEnrichmentFacadeProvider.getSignalEnrichmentFacade(
                actorSystem,
                cacheLoaderProvider.getByRoundTripSignalEnrichmentFacade(),
                cachingSignalEnrichmentFacadeConfig.getCacheConfig(),
                cacheLoaderExecutor,
                "connectivity"
        );
    }

    @Override
    public SignalEnrichmentFacade getFacade(final ConnectionId connectionId) {
        return cachingSignalEnrichmentFacade;
    }

}
