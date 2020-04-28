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
package org.eclipse.ditto.services.gateway.util.config.streaming;

import java.time.Duration;

import javax.annotation.Nonnull;

import org.eclipse.ditto.services.utils.cache.config.CacheConfig;
import org.eclipse.ditto.services.utils.config.KnownConfigValue;

import com.typesafe.config.Config;

/**
 * Retrieve fixed parts of things by using an asynchronous Caffeine cache.
 */
public interface GatewaySignalEnrichmentConfig {

    /**
     * Relative path of the provider config inside signal-enrichment config.
     */
    String CONFIG_PATH = "signal-enrichment";

    /**
     * Returns the duration to wait for cache retrievals.
     *
     * @return the internal ask timeout duration.
     */
    Duration getAskTimeout();

    /**
     * Indicates whether the signal enrichment should make use of caching or not.
     *
     * @return true if caching should be used, otherwise false.
     */
    boolean isCachingEnabled();

    /**
     * Returns the cache config to apply for each connection scoped signal enrichment cache.
     * This config will have no effect if {@link #isCachingEnabled()} returns false.
     *
     * @return the cache config to apply.
     */
    CacheConfig getCacheConfig();


    /**
     * Render this object as a {@code Config}.
     *
     * @return the rendered {@code Config} object.
     */
    Config render();

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code CachingSignalEnrichmentFacadeConfig}.
     */
    enum CachingSignalEnrichmentFacadeConfigValue implements KnownConfigValue {

        /**
         * Indicates whether the signal enrichment should make use of caching or not.
         */
        CACHING_ENABLED("caching-enabled", true),

        /**
         * The ask timeout duration: the duration to wait for cache retrievals.
         */
        ASK_TIMEOUT("ask-timeout", Duration.ofSeconds(10));

        private final String path;
        private final Object defaultValue;

        CachingSignalEnrichmentFacadeConfigValue(final String thePath, final Object theDefaultValue) {
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
