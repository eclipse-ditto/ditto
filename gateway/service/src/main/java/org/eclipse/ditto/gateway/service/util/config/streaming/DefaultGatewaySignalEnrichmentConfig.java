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
package org.eclipse.ditto.gateway.service.util.config.streaming;

import java.time.Duration;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.cache.config.CacheConfig;
import org.eclipse.ditto.internal.utils.cache.config.DefaultCacheConfig;
import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;

/**
 * Default implementation of {@link GatewaySignalEnrichmentConfig}.
 */
@Immutable
public final class DefaultGatewaySignalEnrichmentConfig implements GatewaySignalEnrichmentConfig {

    private static final String CACHE_CONFIG_PATH = "cache";

    private final Duration askTimeout;
    private final CacheConfig cacheConfig;
    private final String signalEnrichmentProvider;

    private DefaultGatewaySignalEnrichmentConfig(final ConfigWithFallback configWithFallback) {
        this.askTimeout = configWithFallback.getNonNegativeAndNonZeroDurationOrThrow(
                CachingSignalEnrichmentFacadeConfigValue.ASK_TIMEOUT);
        cacheConfig = DefaultCacheConfig.of(configWithFallback, CACHE_CONFIG_PATH);
        signalEnrichmentProvider = configWithFallback.getString(
                CachingSignalEnrichmentFacadeConfigValue.SIGNAL_ENRICHMENT_PROVIDER.getConfigPath());
    }

    /**
     * Returns an instance of {@code DefaultConnectionEnrichmentConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the provider specific config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultGatewaySignalEnrichmentConfig of(final Config config) {
        return new DefaultGatewaySignalEnrichmentConfig(ConfigWithFallback.newInstance(config, CONFIG_PATH,
                CachingSignalEnrichmentFacadeConfigValue.values()));
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
    public String getSignalEnrichmentProvider() {
        return signalEnrichmentProvider;
    }

    @Override
    public Config render() {
        return ConfigFactory.empty()
                .withValue(CachingSignalEnrichmentFacadeConfigValue.ASK_TIMEOUT.getConfigPath(),
                        ConfigValueFactory.fromAnyRef(askTimeout))
                .withValue(CachingSignalEnrichmentFacadeConfigValue.SIGNAL_ENRICHMENT_PROVIDER.getConfigPath(),
                        ConfigValueFactory.fromAnyRef(signalEnrichmentProvider))
                .withFallback(cacheConfig.render().atKey(CACHE_CONFIG_PATH))
                .atKey(CONFIG_PATH);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultGatewaySignalEnrichmentConfig
                that = (DefaultGatewaySignalEnrichmentConfig) o;
        return Objects.equals(askTimeout, that.askTimeout) &&
                Objects.equals(cacheConfig, that.cacheConfig) &&
                Objects.equals(signalEnrichmentProvider, that.signalEnrichmentProvider);
    }

    @Override
    public int hashCode() {
        return Objects.hash(askTimeout, cacheConfig, signalEnrichmentProvider);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "askTimeout=" + askTimeout +
                ", cacheConfig=" + cacheConfig +
                ", signalEnrichmentProvider=" + signalEnrichmentProvider +
                "]";
    }

}
