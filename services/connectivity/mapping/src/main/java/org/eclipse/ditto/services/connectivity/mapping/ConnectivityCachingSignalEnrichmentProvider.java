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

import java.util.concurrent.Executor;

import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.services.base.config.SignalEnrichmentConfig;
import org.eclipse.ditto.services.models.signalenrichment.CachingSignalEnrichmentFacade;
import org.eclipse.ditto.services.models.signalenrichment.CachingSignalEnrichmentFacadeConfig;
import org.eclipse.ditto.services.models.signalenrichment.DefaultCachingSignalEnrichmentFacadeConfig;
import org.eclipse.ditto.services.models.signalenrichment.SignalEnrichmentFacade;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;

/**
 * Provider for Connectivity-service of thing-enriching facades that uses an async Caffeine cache in order to load
 * extra data to enrich.
 */
public final class ConnectivityCachingSignalEnrichmentProvider implements ConnectivitySignalEnrichmentProvider {

    private final ActorRef commandHandler;
    private final CachingSignalEnrichmentFacadeConfig cachingSignalEnrichmentFacadeConfig;
    private final Executor cacheLoaderExecutor;

    /**
     * Instantiate this provider. Called by reflection.
     *
     * @param actorSystem The actor system for which this provider is instantiated.
     * @param commandHandler The recipient of retrieve-thing commands - for this class this is the conciergeForwarder.
     * @param signalEnrichmentConfig Configuration for this provider.
     */
    @SuppressWarnings("unused")
    public ConnectivityCachingSignalEnrichmentProvider(final ActorSystem actorSystem, final ActorRef commandHandler,
            final SignalEnrichmentConfig signalEnrichmentConfig) {
        this.commandHandler = commandHandler;
        cachingSignalEnrichmentFacadeConfig =
                DefaultCachingSignalEnrichmentFacadeConfig.of(signalEnrichmentConfig.getProviderConfig());
        cacheLoaderExecutor = actorSystem.dispatchers().lookup("signal-enrichment-cache-dispatcher");
    }

    @Override
    public SignalEnrichmentFacade createFacade(final ConnectionId connectionId) {
        return CachingSignalEnrichmentFacade.of(commandHandler,
                cachingSignalEnrichmentFacadeConfig.getAskTimeout(),
                cachingSignalEnrichmentFacadeConfig.getCacheConfig(),
                cacheLoaderExecutor);
    }
}
