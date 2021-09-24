/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.models.signalenrichment;

import java.util.concurrent.Executor;

import org.eclipse.ditto.internal.utils.cache.config.CacheConfig;

import akka.actor.ActorSystem;

/**
 * Default {@link CachingSignalEnrichmentFacadeProvider} who provides a {@link DittoCachingSignalEnrichmentFacade}.
 */
public final class DittoCachingSignalEnrichmentFacadeProvider extends CachingSignalEnrichmentFacadeProvider {

    /**
     * Instantiate this provider. Called by reflection.
     */
    public DittoCachingSignalEnrichmentFacadeProvider(final ActorSystem actorSystem) {
        super(actorSystem);
    }

    @Override
    public CachingSignalEnrichmentFacade getSignalEnrichmentFacade(
            final ActorSystem actorSystem,
            final SignalEnrichmentFacade cacheLoaderFacade,
            final CacheConfig cacheConfig,
            final Executor cacheLoaderExecutor,
            final String cacheNamePrefix) {

        return DittoCachingSignalEnrichmentFacade.newInstance(cacheLoaderFacade, cacheConfig, cacheLoaderExecutor,
                cacheNamePrefix);
    }

}
