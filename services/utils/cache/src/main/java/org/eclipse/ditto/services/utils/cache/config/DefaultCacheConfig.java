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
package org.eclipse.ditto.services.utils.cache.config;

import java.time.Duration;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.ConfigWithFallback;

import com.typesafe.config.Config;

/**
 * Default implementation of {@link CacheConfig}.
 */
@Immutable
public final class DefaultCacheConfig implements CacheConfig {

    private final long maximumSize;
    private final Duration expireAfterWrite;
    private final Duration expireAfterAccess;

    private DefaultCacheConfig(final ConfigWithFallback configWithFallback) {
        maximumSize = configWithFallback.getLong(CacheConfigValue.MAXIMUM_SIZE.getConfigPath());
        expireAfterWrite = configWithFallback.getDuration(CacheConfigValue.EXPIRE_AFTER_WRITE.getConfigPath());
        expireAfterAccess = configWithFallback.getDuration(CacheConfigValue.EXPIRE_AFTER_ACCESS.getConfigPath());
    }

    /**
     * Returns an instance of {@code DittoConciergeCacheConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the cache config at {@code configPath}.
     * @param configPath the supposed path of the nested cache config settings.
     * @return the instance.
     * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultCacheConfig of(final Config config, final String configPath) {
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
    public Duration getExpireAfterAccess() {
        return expireAfterAccess;
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
                Objects.equals(expireAfterWrite, that.expireAfterWrite) &&
                Objects.equals(expireAfterAccess, that.expireAfterAccess);
    }

    @Override
    public int hashCode() {
        return Objects.hash(maximumSize, expireAfterWrite, expireAfterAccess);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "maximumSize=" + maximumSize +
                ", expireAfterWrite=" + expireAfterWrite +
                ", expireAfterAccess=" + expireAfterAccess +
                "]";
    }

}
