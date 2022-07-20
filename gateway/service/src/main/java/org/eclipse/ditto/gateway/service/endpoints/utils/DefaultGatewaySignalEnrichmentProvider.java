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

import org.eclipse.ditto.edge.service.dispatching.EdgeCommandForwarderActor;
import org.eclipse.ditto.internal.models.signalenrichment.ByRoundTripSignalEnrichmentFacade;
import org.eclipse.ditto.internal.models.signalenrichment.DefaultSignalEnrichmentProviderConfig;
import org.eclipse.ditto.internal.models.signalenrichment.DittoCachingSignalEnrichmentFacade;
import org.eclipse.ditto.internal.models.signalenrichment.SignalEnrichmentFacade;

import com.typesafe.config.Config;

import akka.actor.ActorSystem;
import akka.http.javadsl.model.HttpRequest;

/**
 * Provider for gateway-service of signal-enriching facades that uses an async Caffeine cache in order to load
 * extra data to enrich.
 */
public final class DefaultGatewaySignalEnrichmentProvider implements GatewaySignalEnrichmentProvider {

    private static final String COMMAND_FORWARDER = "/user/gatewayRoot/" + EdgeCommandForwarderActor.ACTOR_NAME;
    private static final String CACHE_LOADER_DISPATCHER = "signal-enrichment-cache-dispatcher";

    private final SignalEnrichmentFacade facade;

    /**
     * Instantiate this provider. Called by reflection.
     *
     * @param actorSystem The actor system for which this provider is instantiated.
     * @param config the config the extension is configured.
     */
    public DefaultGatewaySignalEnrichmentProvider(final ActorSystem actorSystem, final Config config) {
        final var commandHandler = actorSystem.actorSelection(COMMAND_FORWARDER);
        final var providerConfig = DefaultSignalEnrichmentProviderConfig.of(config);
        final var delegate = ByRoundTripSignalEnrichmentFacade.of(commandHandler, providerConfig.getAskTimeout());
        if (providerConfig.isCachingEnabled()) {
            final Executor cacheLoaderExecutor = actorSystem.dispatchers().lookup(CACHE_LOADER_DISPATCHER);
            facade = DittoCachingSignalEnrichmentFacade.newInstance(
                    delegate,
                    providerConfig.getCacheConfig(),
                    cacheLoaderExecutor,
                    "gateway");
        } else {
            facade = delegate;
        }
    }

    @Override
    public CompletionStage<SignalEnrichmentFacade> getFacade(final HttpRequest request) {
        return CompletableFuture.completedStage(facade);
    }

}
