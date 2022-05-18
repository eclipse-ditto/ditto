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

import java.util.List;
import java.util.concurrent.Executor;

import org.eclipse.ditto.internal.utils.akka.AkkaClassLoader;
import org.eclipse.ditto.internal.utils.cache.config.CacheConfig;

import akka.actor.AbstractExtensionId;
import akka.actor.ActorSystem;
import akka.actor.ExtendedActorSystem;
import akka.actor.Extension;

/**
 * Caching signal enrichment facade to be loaded by reflection.
 * Can be used as an extension point to use custom signal enrichment.
 * Implementations MUST have a public constructor taking an actorSystem as argument.
 */
public abstract class CachingSignalEnrichmentFacadeProvider implements Extension {

    private static final ExtensionId EXTENSION_ID = new ExtensionId();

    protected final ActorSystem actorSystem;

    protected CachingSignalEnrichmentFacadeProvider(final ActorSystem actorSystem) {
        this.actorSystem = actorSystem;
    }

    /**
     * Returns a the {@link SignalEnrichmentFacade} loaded by reflection.
     *
     * @param actorSystem the actorSystem the signal enrichment facade provider belongs to.
     * @param cacheLoaderFacade the facade whose argument-result-pairs we are caching.
     * @param cacheConfig the cache configuration to use for the cache.
     * @param cacheLoaderExecutor the executor to use in order to asynchronously load cache entries.
     * @param cacheNamePrefix the prefix to use as cacheName of the cache.
     * @throws NullPointerException if any argument is null.
     */
    public abstract CachingSignalEnrichmentFacade getSignalEnrichmentFacade(
            final ActorSystem actorSystem,
            final SignalEnrichmentFacade cacheLoaderFacade,
            final CacheConfig cacheConfig,
            final Executor cacheLoaderExecutor,
            final String cacheNamePrefix);

    /**
     * Load a {@code CachingSignalEnrichmentFacadeProvider} dynamically according to the signal enrichment
     * configuration.
     *
     * @param actorSystem The actor system in which to load the facade.
     * @return The facade.
     */
    public static CachingSignalEnrichmentFacadeProvider get(final ActorSystem actorSystem) {

        return EXTENSION_ID.get(actorSystem);
    }

    /**
     * ID of the actor system extension to validate the {@code CachingSignalEnrichmentFacadeProvider}.
     */
    private static final class ExtensionId extends AbstractExtensionId<CachingSignalEnrichmentFacadeProvider> {

        private static final String CONFIG_PATH = "ditto.signal-enrichment.caching-signal-enrichment-facade.provider";

        @Override
        public CachingSignalEnrichmentFacadeProvider createExtension(final ExtendedActorSystem system) {
            final String implementation = system.settings().config().getString(CONFIG_PATH);
            return AkkaClassLoader.instantiate(system, CachingSignalEnrichmentFacadeProvider.class,
                    implementation,
                    List.of(ActorSystem.class),
                    List.of(system));
        }
    }

}
