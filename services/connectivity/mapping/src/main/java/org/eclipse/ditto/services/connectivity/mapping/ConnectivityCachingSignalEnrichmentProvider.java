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

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.services.base.config.SignalEnrichmentConfig;
import org.eclipse.ditto.services.models.signalenrichment.CachingSignalEnrichmentFacade;
import org.eclipse.ditto.services.models.signalenrichment.CachingSignalEnrichmentFacadeConfig;
import org.eclipse.ditto.services.models.signalenrichment.DefaultCachingSignalEnrichmentFacadeConfig;
import org.eclipse.ditto.services.models.signalenrichment.PolicyObserverActor;
import org.eclipse.ditto.services.models.signalenrichment.SignalEnrichmentFacade;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;

/**
 * Provider for Connectivity-service of signal-enriching facades that uses an async Caffeine cache in order to load
 * extra data to enrich.
 */
public final class ConnectivityCachingSignalEnrichmentProvider
        implements ConnectivitySignalEnrichmentProvider, Consumer<PolicyId> {

    private final ActorRef commandHandler;
    private final CachingSignalEnrichmentFacadeConfig cachingSignalEnrichmentFacadeConfig;
    private final Executor cacheLoaderExecutor;
    private final Set<CachingSignalEnrichmentFacade> createdFacades;

    /**
     * Instantiate this provider. Called by reflection.
     *
     * @param actorSystem The actor system for which this provider is instantiated.
     * @param policyObserver The {@code PolicyObserverActor} actor to use in order to subscribe to policy changes.
     * @param commandHandler The recipient of retrieve-thing commands - for this class this is the conciergeForwarder.
     * @param signalEnrichmentConfig Configuration for this provider.
     */
    @SuppressWarnings("unused")
    public ConnectivityCachingSignalEnrichmentProvider(final ActorSystem actorSystem,
            final ActorRef policyObserver,
            final ActorRef commandHandler,
            final SignalEnrichmentConfig signalEnrichmentConfig) {
        this.commandHandler = commandHandler;
        cachingSignalEnrichmentFacadeConfig =
                DefaultCachingSignalEnrichmentFacadeConfig.of(signalEnrichmentConfig.getProviderConfig());
        cacheLoaderExecutor = actorSystem.dispatchers().lookup("signal-enrichment-cache-dispatcher");
        // create a set of "Weak references" - that way entries in this set may be deleted by the GC whenever no other
        // reference to that object is existing any more:
        createdFacades = Collections.newSetFromMap(new WeakHashMap<>());
        policyObserver.tell(PolicyObserverActor.AddObserver.of(this), null);
    }

    @Override
    public void accept(final PolicyId policyId) {
        createdFacades.forEach(facade -> facade.accept(policyId));
    }

    @Override
    public SignalEnrichmentFacade createFacade(final ConnectionId connectionId) {
        final CachingSignalEnrichmentFacade facade = CachingSignalEnrichmentFacade.of(commandHandler,
                cachingSignalEnrichmentFacadeConfig.getAskTimeout(),
                cachingSignalEnrichmentFacadeConfig.getCacheConfig(),
                cacheLoaderExecutor);
        createdFacades.add(facade);
        return facade;
    }

    /**
     * For unit tests only.
     *
     * @return the created facades.
     */
    Set<CachingSignalEnrichmentFacade> getCreatedFacades() {
        return createdFacades;
    }
}
