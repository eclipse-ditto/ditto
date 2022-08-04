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
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.cache.config.CacheConfig;
import org.eclipse.ditto.internal.utils.cache.config.DefaultCacheConfig;
import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;

import com.typesafe.config.Config;

/**
 * Default implementation of {@link SignalEnrichmentProviderConfig}.
 */
@Immutable
public final class DefaultSignalEnrichmentProviderConfig implements SignalEnrichmentProviderConfig {

    private static final String CACHE_CONFIG_PATH = "cache";

    private final Duration askTimeout;
    private final CacheConfig cacheConfig;
    private final boolean cachingEnabled;

    private DefaultSignalEnrichmentProviderConfig(final ConfigWithFallback configWithFallback) {
        askTimeout = configWithFallback.getDuration(ConfigValue.ASK_TIMEOUT.getConfigPath());
        cacheConfig = DefaultCacheConfig.of(configWithFallback, CACHE_CONFIG_PATH);
        cachingEnabled = configWithFallback.getBoolean(ConfigValue.CACHE_ENABLED.getConfigPath());
    }

    /**
     * Returns an instance of {@code DefaultConnectionEnrichmentConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the provider specific config.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultSignalEnrichmentProviderConfig of(final Config config) {
        return new DefaultSignalEnrichmentProviderConfig(ConfigWithFallback.newInstance(config, ConfigValue.values()));
    }

    @Override
    public Duration getAskTimeout() {
        return askTimeout;
    }

    @Override
    public CacheConfig getCacheConfig() {
        return cacheConfig;
    }

    @Override
    public boolean isCachingEnabled() {
        return cachingEnabled;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultSignalEnrichmentProviderConfig that = (DefaultSignalEnrichmentProviderConfig) o;
        return Objects.equals(askTimeout, that.askTimeout) &&
                Objects.equals(cacheConfig, that.cacheConfig) &&
                cachingEnabled == that.cachingEnabled;
    }

    @Override
    public int hashCode() {
        return Objects.hash(askTimeout, cacheConfig, cachingEnabled);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "askTimeout=" + askTimeout +
                ", cacheConfig=" + cacheConfig +
                ", cachingEnabled=" + cachingEnabled +
                "]";
    }
}
