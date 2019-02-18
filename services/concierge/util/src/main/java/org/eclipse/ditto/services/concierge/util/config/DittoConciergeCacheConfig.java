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

import java.time.Duration;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.ConfigWithFallback;
import org.eclipse.ditto.services.utils.config.KnownConfigValue;

import com.typesafe.config.Config;

/**
 * Default implementation of {@link CacheConfig}.
 */
@Immutable
public final class DittoConciergeCacheConfig implements CacheConfig {

    private enum ConciergeCacheConfigValue implements KnownConfigValue {

        MAXIMUM_SIZE("maximum-size", 50_000L),

        EXPIRE_AFTER_WRITE("expire-after-write", Duration.ofMinutes(15L));

        private final String path;
        private final Object defaultValue;

        private ConciergeCacheConfigValue(final String thePath, final Object theDefaultValue) {
            path = thePath;
            defaultValue = theDefaultValue;
        }

        @Override
        public String getConfigPath() {
            return path;
        }

        @Override
        public Object getDefaultValue() {
            return defaultValue;
        }

    }

    private final Config config;

    private DittoConciergeCacheConfig(final Config theConfig) {
        config = theConfig;
    }

    /**
     * Returns an instance of {@code DittoConciergeCacheConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the cache config at {@code configPath}.
     * @param configPath the supposed path of the nested cache config settings.
     * @return the instance.
     * @throws NullPointerException if {@code config} is {@code null}.
     * @throws com.typesafe.config.ConfigException.WrongType if {@code config} did not contain a nested
     * {@code Config} for {@code configPath}.
     */
    public static DittoConciergeCacheConfig getInstance(final Config config, final String configPath) {
        return new DittoConciergeCacheConfig(
                ConfigWithFallback.newInstance(config, configPath, ConciergeCacheConfigValue.values()));
    }

    @Override
    public long getMaximumSize() {
        return config.getLong(ConciergeCacheConfigValue.MAXIMUM_SIZE.getConfigPath());
    }

    @Override
    public Duration getExpireAfterWrite() {
        return config.getDuration(ConciergeCacheConfigValue.EXPIRE_AFTER_WRITE.getConfigPath());
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DittoConciergeCacheConfig that = (DittoConciergeCacheConfig) o;
        return config.equals(that.config);
    }

    @Override
    public int hashCode() {
        return Objects.hash(config);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "config=" + config +
                "]";
    }

}
