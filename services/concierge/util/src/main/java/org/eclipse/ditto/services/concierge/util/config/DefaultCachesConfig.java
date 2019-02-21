/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.concierge.util.config;

import java.io.Serializable;
import java.time.Duration;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.ConfigWithFallback;
import org.eclipse.ditto.services.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class implements {@link org.eclipse.ditto.services.concierge.util.config.ConciergeConfig.CachesConfig} for Ditto's Concierge service.
 */
@Immutable
public final class DefaultCachesConfig implements ConciergeConfig.CachesConfig, Serializable {

    private static final long serialVersionUID = -3628650470707259581L;

    private static final String CONFIG_PATH = "caches";

    private final Duration askTimeout;
    private final CacheConfig idCacheConfig;
    private final CacheConfig enforcerCacheConfig;

    private DefaultCachesConfig(final ScopedConfig config) {
        askTimeout = config.getDuration(CachesConfigValue.ASK_TIMEOUT.getConfigPath());
        idCacheConfig = DefaultCacheConfig.getInstance(config, "id");
        enforcerCacheConfig = DefaultCacheConfig.getInstance(config, "enforcer");
    }

    /**
     * Returns an instance of {@code DittoConciergeCachesConfig} based on the settings of the specified Config.
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
