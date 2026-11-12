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
package org.eclipse.ditto.internal.utils.cache.config;

import java.text.MessageFormat;
import java.time.Duration;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.DittoConfigError;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;

/**
 * Default implementation of {@link CacheConfig}.
 */
@Immutable
public final class DefaultCacheConfig implements CacheConfig {

    private final long maximumSize;
    private final Duration expireAfterWrite;
    private final double expireAfterWriteJitter;
    private final Duration expireAfterAccess;
    private final Duration expireAfterCreate;

    private DefaultCacheConfig(final ConfigWithFallback configWithFallback) {
        maximumSize = configWithFallback.getPositiveLongOrThrow(CacheConfigValue.MAXIMUM_SIZE);
        expireAfterWrite = configWithFallback.getNonNegativeDurationOrThrow(CacheConfigValue.EXPIRE_AFTER_WRITE);
        expireAfterWriteJitter = getJitterOrThrow(configWithFallback);
        expireAfterAccess = configWithFallback.getNonNegativeDurationOrThrow(CacheConfigValue.EXPIRE_AFTER_ACCESS);
        expireAfterCreate = configWithFallback.getNonNegativeDurationOrThrow(CacheConfigValue.EXPIRE_AFTER_CREATE);
    }

    private static double getJitterOrThrow(final ConfigWithFallback configWithFallback) {
        final var configPath = CacheConfigValue.EXPIRE_AFTER_WRITE_JITTER.getConfigPath();
        final var result = configWithFallback.getDouble(configPath);
        if (result < 0.0 || result >= 1.0) {
            final var msgPattern = "The double value at <{0}> must be between 0.0 (inclusive) and 1.0 (exclusive) " +
                    "but it was <{1}>!";
            throw new DittoConfigError(MessageFormat.format(msgPattern, configPath, result));
        }
        return result;
    }

    /**
     * Returns an instance of {@code DefaultCacheConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the cache config at {@code configPath}.
     * @param configPath the supposed path of the nested cache config settings.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
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
    public double getExpireAfterWriteJitter() {
        return expireAfterWriteJitter;
    }

    @Override
    public Duration getExpireAfterAccess() {
        return expireAfterAccess;
    }

    @Override
    public Duration getExpireAfterCreate() {
        return expireAfterCreate;
    }

    @Override
    public Config render() {
        return ConfigFactory.empty()
                .withValue(CacheConfigValue.MAXIMUM_SIZE.getConfigPath(), ConfigValueFactory.fromAnyRef(maximumSize))
                .withValue(CacheConfigValue.EXPIRE_AFTER_CREATE.getConfigPath(), ConfigValueFactory.fromAnyRef(expireAfterCreate))
                .withValue(CacheConfigValue.EXPIRE_AFTER_ACCESS.getConfigPath(), ConfigValueFactory.fromAnyRef(expireAfterAccess))
                .withValue(CacheConfigValue.EXPIRE_AFTER_WRITE.getConfigPath(), ConfigValueFactory.fromAnyRef(expireAfterWrite))
                .withValue(CacheConfigValue.EXPIRE_AFTER_WRITE_JITTER.getConfigPath(),
                        ConfigValueFactory.fromAnyRef(expireAfterWriteJitter));
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
                Double.compare(expireAfterWriteJitter, that.expireAfterWriteJitter) == 0 &&
                Objects.equals(expireAfterAccess, that.expireAfterAccess)&&
                Objects.equals(expireAfterCreate, that.expireAfterCreate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(maximumSize, expireAfterWrite, expireAfterWriteJitter, expireAfterAccess,
                expireAfterCreate);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "maximumSize=" + maximumSize +
                ", expireAfterWrite=" + expireAfterWrite +
                ", expireAfterWriteJitter=" + expireAfterWriteJitter +
                ", expireAfterAccess=" + expireAfterAccess +
                ", expireAfterCreate=" + expireAfterCreate +
                "]";
    }

}
