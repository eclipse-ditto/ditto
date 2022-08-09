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
package org.eclipse.ditto.thingsearch.service.persistence.write.streaming;

import java.util.concurrent.Executor;

import org.eclipse.ditto.internal.models.signalenrichment.CachingSignalEnrichmentFacade;
import org.eclipse.ditto.internal.models.signalenrichment.DittoCachingSignalEnrichmentFacade;
import org.eclipse.ditto.internal.models.signalenrichment.SignalEnrichmentFacade;
import org.eclipse.ditto.internal.utils.cache.config.CacheConfig;

import com.typesafe.config.Config;

import akka.actor.ActorSystem;

/**
 * Default {@link CachingSignalEnrichmentFacadeProvider} who provides a {@link org.eclipse.ditto.internal.models.signalenrichment.DittoCachingSignalEnrichmentFacade}.
 */
public final class DittoCachingSignalEnrichmentFacadeProvider implements CachingSignalEnrichmentFacadeProvider {

    /**
     * Instantiate this provider. Called by reflection.
     * @param actorSystem the actor system in which to load the extension.
     * @param config the configuration for this extension.
     */
    @SuppressWarnings("unused")
    public DittoCachingSignalEnrichmentFacadeProvider(final ActorSystem actorSystem, final Config config) {
        // No-Op but required for extension initialisation
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
