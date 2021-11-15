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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

import org.eclipse.ditto.gateway.service.util.config.streaming.GatewaySignalEnrichmentConfig;
import org.eclipse.ditto.internal.models.signalenrichment.CachingSignalEnrichmentFacadeProvider;
import org.eclipse.ditto.internal.models.signalenrichment.SignalEnrichmentFacade;

import akka.actor.ActorSystem;
import akka.http.javadsl.model.HttpRequest;

/**
 * Provider for gateway-service of signal-enriching facades that uses an async Caffeine cache in order to load
 * extra data to enrich.
 */
public final class GatewayCachingSignalEnrichmentProvider implements GatewaySignalEnrichmentProvider {

    private static final String CACHE_LOADER_DISPATCHER = "signal-enrichment-cache-dispatcher";

    private final SignalEnrichmentFacade cachingSignalEnrichmentFacade;

    /**
     * Instantiate this provider. Called by reflection.
     *
     * @param actorSystem The actor system for which this provider is instantiated.
     * @param signalEnrichmentConfig Configuration for this provider.
     */
    public GatewayCachingSignalEnrichmentProvider(final ActorSystem actorSystem,
            final GatewaySignalEnrichmentConfig signalEnrichmentConfig) {
        final GatewayByRoundTripSignalEnrichmentProvider cacheLoaderProvider =
                new GatewayByRoundTripSignalEnrichmentProvider(actorSystem, signalEnrichmentConfig);
        final Executor cacheLoaderExecutor = actorSystem.dispatchers().lookup(CACHE_LOADER_DISPATCHER);
        final var cachingSignalEnrichmentFacadeProvider = CachingSignalEnrichmentFacadeProvider.get(actorSystem);
        cachingSignalEnrichmentFacade = cachingSignalEnrichmentFacadeProvider.getSignalEnrichmentFacade(
                actorSystem,
                cacheLoaderProvider.getByRoundTripSignalEnrichmentFacade(),
                signalEnrichmentConfig.getCacheConfig(),
                cacheLoaderExecutor,
                "gateway"
        );
    }

    @Override
    public CompletionStage<SignalEnrichmentFacade> getFacade(final HttpRequest request) {
        return CompletableFuture.completedStage(cachingSignalEnrichmentFacade);
    }

}
