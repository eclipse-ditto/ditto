/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.policies.enforcement.config;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.cache.config.CacheConfig;
import org.eclipse.ditto.internal.utils.cache.config.DefaultCacheConfig;
import org.eclipse.ditto.internal.utils.cacheloaders.config.AskWithRetryConfig;
import org.eclipse.ditto.internal.utils.cacheloaders.config.DefaultAskWithRetryConfig;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class implements {@link CachesConfig} for Ditto's Concierge service.
 */
@Immutable
public final class DefaultCachesConfig implements CachesConfig {

    private static final String CONFIG_PATH = "caches";
    private static final String ASK_WITH_RETRY_CONFIG_PATH = "ask-with-retry";

    private final AskWithRetryConfig askWithRetryConfig;
    private final CacheConfig idCacheConfig;
    private final CacheConfig enforcerCacheConfig;

    private DefaultCachesConfig(final ScopedConfig config) {
        askWithRetryConfig = DefaultAskWithRetryConfig.of(config, ASK_WITH_RETRY_CONFIG_PATH);
        idCacheConfig = DefaultCacheConfig.of(config, "id");
        enforcerCacheConfig = DefaultCacheConfig.of(config, "enforcer");
    }

    /**
     * Returns an instance of {@code DefaultCachesConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the caches config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultCachesConfig of(final Config config) {
        return new DefaultCachesConfig(DefaultScopedConfig.newInstance(config, CONFIG_PATH));
    }

    @Override
    public AskWithRetryConfig getAskWithRetryConfig() {
        return askWithRetryConfig;
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
        return askWithRetryConfig.equals(that.askWithRetryConfig) &&
                idCacheConfig.equals(that.idCacheConfig) && enforcerCacheConfig.equals(that.enforcerCacheConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(askWithRetryConfig, idCacheConfig, enforcerCacheConfig);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "askWithRetryConfig=" + askWithRetryConfig +
                ", idCacheConfig=" + idCacheConfig +
                ", enforcerCacheConfig=" + enforcerCacheConfig +
                "]";
    }
}
