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
package org.eclipse.ditto.services.concierge.cache.config;

import java.io.Serializable;
import java.time.Duration;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.ConfigWithFallback;
import org.eclipse.ditto.services.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * Default implementation of {@link CacheConfig}.
 */
@Immutable
public final class DefaultCacheConfig implements CacheConfig, Serializable {

    private static final long serialVersionUID = -5185056342757809881L;

    private final long maximumSize;
    private final Duration expireAfterWrite;

    private DefaultCacheConfig(final ScopedConfig config) {
        maximumSize = config.getLong(CacheConfigValue.MAXIMUM_SIZE.getConfigPath());
        expireAfterWrite = config.getDuration(CacheConfigValue.EXPIRE_AFTER_WRITE.getConfigPath());
    }

    /**
     * Returns an instance of {@code DittoConciergeCacheConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the cache config at {@code configPath}.
     * @param configPath the supposed path of the nested cache config settings.
     * @return the instance.
     * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultCacheConfig getInstance(final Config config, final String configPath) {
        return new DefaultCacheConfig(ConfigWithFallback.newInstance(config, configPath, CacheConfigValue.values()));
    }

    @Override
    public long getMaximumSize() {
        return maximumSize;
    }

    @Override
    public Duration getExpireAfterWrite() {
        return expireAfterWrite;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultCacheConfig that = (DefaultCacheConfig) o;
        return maximumSize == that.maximumSize &&
                expireAfterWrite.equals(that.expireAfterWrite);
    }

    @Override
    public int hashCode() {
        return Objects.hash(maximumSize, expireAfterWrite);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "maximumSize=" + maximumSize +
                ", expireAfterWrite=" + expireAfterWrite +
                "]";
    }

}
