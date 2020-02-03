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

import java.util.concurrent.Executor;

import org.eclipse.ditto.services.base.config.SignalEnrichmentConfig;
import org.eclipse.ditto.services.models.signalenrichment.CachingSignalEnrichmentFacade;
import org.eclipse.ditto.services.models.signalenrichment.CachingSignalEnrichmentFacadeConfig;
import org.eclipse.ditto.services.models.signalenrichment.DefaultCachingSignalEnrichmentFacadeConfig;
import org.eclipse.ditto.services.models.signalenrichment.SignalEnrichmentFacade;

import akka.actor.ActorSystem;
import akka.http.javadsl.model.HttpRequest;

/**
 * Provider for gateway-service of signal-enriching facades that uses an async Caffeine cache in order to load
 * extra data to enrich.
 */
public final class GatewayCachingSignalEnrichmentProvider extends GatewaySignalEnrichmentProvider {

    private static final String CACHE_LOADER_DISPATCHER = "signal-enrichment-cache-dispatcher";

    private final CachingSignalEnrichmentFacade cachingSignalEnrichmentFacade;

    /**
     * Instantiate this provider. Called by reflection.
     *
     * @param actorSystem The actor system for which this provider is instantiated.
     * @param signalEnrichmentConfig Configuration for this provider.
     */
    @SuppressWarnings("unused")
    public GatewayCachingSignalEnrichmentProvider(final ActorSystem actorSystem,
            final SignalEnrichmentConfig signalEnrichmentConfig) {
        final GatewayByRoundTripSignalEnrichmentProvider cacheLoaderProvider =
                new GatewayByRoundTripSignalEnrichmentProvider(actorSystem, signalEnrichmentConfig);
        final CachingSignalEnrichmentFacadeConfig cachingSignalEnrichmentFacadeConfig =
                DefaultCachingSignalEnrichmentFacadeConfig.of(signalEnrichmentConfig.getProviderConfig());
        final Executor cacheLoaderExecutor = actorSystem.dispatchers().lookup(CACHE_LOADER_DISPATCHER);
        cachingSignalEnrichmentFacade = CachingSignalEnrichmentFacade.of(
                cacheLoaderProvider.getByRoundTripSignalEnrichmentFacade(),
                cachingSignalEnrichmentFacadeConfig.getCacheConfig(),
                cacheLoaderExecutor,
                "gateway"
        );
    }

    @Override
    public SignalEnrichmentFacade getFacade(final HttpRequest request) {
        return cachingSignalEnrichmentFacade;
    }

}
