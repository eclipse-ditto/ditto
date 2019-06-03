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
package org.eclipse.ditto.services.concierge.cache.config;

import java.time.Duration;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.cache.config.CacheConfig;
import org.eclipse.ditto.services.utils.cache.config.DefaultCacheConfig;
import org.eclipse.ditto.services.utils.config.ConfigWithFallback;
import org.eclipse.ditto.services.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class implements {@link CachesConfig} for Ditto's Concierge service.
 */
@Immutable
public final class DefaultCachesConfig implements CachesConfig {

    private static final String CONFIG_PATH = "caches";

    private final Duration askTimeout;
    private final CacheConfig idCacheConfig;
    private final CacheConfig enforcerCacheConfig;

    private DefaultCachesConfig(final ScopedConfig config) {
        askTimeout = config.getDuration(CachesConfigValue.ASK_TIMEOUT.getConfigPath());
        idCacheConfig = DefaultCacheConfig.of(config, "id");
        enforcerCacheConfig = DefaultCacheConfig.of(config, "enforcer");
    }

    /**
     * Returns an instance of {@code DefaultCachesConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the caches config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultCachesConfig of(final Config config) {
        return new DefaultCachesConfig(ConfigWithFallback.newInstance(config, CONFIG_PATH, CachesConfigValue.values()));
    }

    @Override
    public Duration getAskTimeout() {
        return askTimeout;
    }

    @Override
    public CacheConfig getIdCacheConfig() {
        return idCacheConfig;
    }

    @Override
    public CacheConfig getEnforcerCacheConfig() {
        return enforcerCacheConfig;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultCachesConfig that = (DefaultCachesConfig) o;
        return askTimeout.equals(that.askTimeout) &&
                idCacheConfig.equals(that.idCacheConfig) &&
                enforcerCacheConfig.equals(that.enforcerCacheConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(askTimeout, idCacheConfig, enforcerCacheConfig);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "askTimeout=" + askTimeout +
                ", idCacheConfig=" + idCacheConfig +
                ", enforcerCacheConfig=" + enforcerCacheConfig +
                "]";
    }

}
