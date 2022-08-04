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
package org.eclipse.ditto.internal.models.signalenrichment;

import java.time.Duration;

import javax.annotation.Nonnull;

import org.eclipse.ditto.internal.utils.cache.config.CacheConfig;
import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

/**
 * Configuration for SignalEnrichmentProviders.
 */
public interface SignalEnrichmentProviderConfig {

    /**
     * Returns the duration to wait for cache retrievals.
     *
     * @return the internal ask timeout duration.
     */
    Duration getAskTimeout();

    /**
     * Returns the cache config to apply for each connection scoped signal enrichment cache.
     *
     * @return the cache config to apply.
     */
    CacheConfig getCacheConfig();

    /**
     * @return indicates whether caching is enabled or not.
     */
    boolean isCachingEnabled();

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code CachingSignalEnrichmentFacadeConfig}.
     */
    enum ConfigValue implements KnownConfigValue {

        /**
         * The ask timeout duration: the duration to wait for cache retrievals.
         */
        ASK_TIMEOUT("ask-timeout", Duration.ofSeconds(10)),

        CACHE_ENABLED("cache.enabled", true);

        private final String path;
        private final Object defaultValue;

        ConfigValue(final String thePath, final Object theDefaultValue) {
            path = thePath;
            defaultValue = theDefaultValue;
        }

        @Override
        @Nonnull
        public Object getDefaultValue() {
            return defaultValue;
        }

        @Override
        @Nonnull
        public String getConfigPath() {
            return path;
        }

    }
}
