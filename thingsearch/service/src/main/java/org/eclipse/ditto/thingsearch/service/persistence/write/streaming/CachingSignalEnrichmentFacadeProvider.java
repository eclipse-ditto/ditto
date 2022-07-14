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

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.concurrent.Executor;

import org.eclipse.ditto.internal.models.signalenrichment.CachingSignalEnrichmentFacade;
import org.eclipse.ditto.internal.models.signalenrichment.SignalEnrichmentFacade;
import org.eclipse.ditto.internal.utils.cache.config.CacheConfig;
import org.eclipse.ditto.internal.utils.extension.DittoExtensionIds;
import org.eclipse.ditto.internal.utils.extension.DittoExtensionPoint;

import com.typesafe.config.Config;

import akka.actor.ActorSystem;

/**
 * Caching signal enrichment facade to be loaded by reflection.
 * Can be used as an extension point to use custom signal enrichment.
 * Implementations MUST have a public constructor taking an actorSystem and a Config as argument.
 */
public interface CachingSignalEnrichmentFacadeProvider extends DittoExtensionPoint {

    /**
     * Returns a the {@link org.eclipse.ditto.internal.models.signalenrichment.SignalEnrichmentFacade} loaded by reflection.
     *
     * @param actorSystem the actorSystem the signal enrichment facade provider belongs to.
     * @param cacheLoaderFacade the facade whose argument-result-pairs we are caching.
     * @param cacheConfig the cache configuration to use for the cache.
     * @param cacheLoaderExecutor the executor to use in order to asynchronously load cache entries.
     * @param cacheNamePrefix the prefix to use as cacheName of the cache.
     * @throws NullPointerException if any argument is null.
     */
    CachingSignalEnrichmentFacade getSignalEnrichmentFacade(
            final ActorSystem actorSystem,
            final SignalEnrichmentFacade cacheLoaderFacade,
            final CacheConfig cacheConfig,
            final Executor cacheLoaderExecutor,
            final String cacheNamePrefix);

    /**
     * Loads the implementation of {@code CachingSignalEnrichmentFacade} which is configured for the
     * {@code ActorSystem}.
     *
     * @param actorSystem the actorSystem in which the {@code CachingSignalEnrichmentFacade} should be loaded.
     * @param config the config the extension is configured.
     * @return the {@code CachingSignalEnrichmentFacade} implementation.
     * @throws NullPointerException if {@code actorSystem} is {@code null}.
     */
    static CachingSignalEnrichmentFacadeProvider get(final ActorSystem actorSystem, final Config config) {
        checkNotNull(actorSystem, "actorSystem");
        checkNotNull(config, "config");
        final var extensionIdConfig = ExtensionId.computeConfig(config);
        return DittoExtensionIds.get(actorSystem)
                .computeIfAbsent(extensionIdConfig, ExtensionId::new)
                .get(actorSystem);
    }

    final class ExtensionId extends DittoExtensionPoint.ExtensionId<CachingSignalEnrichmentFacadeProvider> {
        private static final String CONFIG_KEY = "caching-signal-enrichment-facade-provider";

        private ExtensionId(final ExtensionIdConfig<CachingSignalEnrichmentFacadeProvider> extensionIdConfig) {
            super(extensionIdConfig);
        }

        static ExtensionIdConfig<CachingSignalEnrichmentFacadeProvider> computeConfig(final Config config) {
            return ExtensionIdConfig.of(CachingSignalEnrichmentFacadeProvider.class, config, CONFIG_KEY);
        }

        @Override
        protected String getConfigKey() {
            return CONFIG_KEY;
        }

    }

}
