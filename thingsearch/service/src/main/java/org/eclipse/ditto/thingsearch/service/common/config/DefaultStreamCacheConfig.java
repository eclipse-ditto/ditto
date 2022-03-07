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
package org.eclipse.ditto.thingsearch.service.common.config;

import java.time.Duration;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.cache.config.DefaultCacheConfig;
import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;

/**
 * This class is the default implementation of {@link StreamCacheConfig}.
 */
@Immutable
public final class DefaultStreamCacheConfig implements StreamCacheConfig {

    private static final String CONFIG_PATH = "cache";

    private final String dispatcherName;
    private final Duration retryDelay;
    private final DefaultCacheConfig genericCacheConfig;

    private DefaultStreamCacheConfig(final ConfigWithFallback streamCacheScopedConfig,
            final DefaultCacheConfig genericCacheConfig) {

        dispatcherName = streamCacheScopedConfig.getString(StreamCacheConfigValue.DISPATCHER_NAME.getConfigPath());
        retryDelay = streamCacheScopedConfig.getNonNegativeDurationOrThrow(StreamCacheConfigValue.RETRY_DELAY);
        this.genericCacheConfig = genericCacheConfig;
    }

    /**
     * Returns an instance of DefaultStreamCacheConfig based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the stream cache config at {@value CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultStreamCacheConfig of(final Config config) {
        return new DefaultStreamCacheConfig(
                ConfigWithFallback.newInstance(config, CONFIG_PATH, StreamCacheConfigValue.values()),
                DefaultCacheConfig.of(config, CONFIG_PATH));
    }

    @Override
    public String getDispatcherName() {
        return dispatcherName;
    }

    @Override
    public Duration getRetryDelay() {
        return retryDelay;
    }

    @Override
    public long getMaximumSize() {
        return genericCacheConfig.getMaximumSize();
    }

    @Override
    public Duration getExpireAfterWrite() {
        return genericCacheConfig.getExpireAfterWrite();
    }

    @Override
    public Duration getExpireAfterAccess() {
        return genericCacheConfig.getExpireAfterAccess();
    }

    @Override
    public Duration getExpireAfterCreate() {
        return genericCacheConfig.getExpireAfterCreate();
    }

    @Override
    public Config render() {
            return ConfigFactory.empty()
                    .withFallback(genericCacheConfig.render())
                    .withValue(StreamCacheConfigValue.DISPATCHER_NAME.getConfigPath(), ConfigValueFactory.fromAnyRef(dispatcherName))
                    .withValue(StreamCacheConfigValue.RETRY_DELAY.getConfigPath(), ConfigValueFactory.fromAnyRef(retryDelay))
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
        final DefaultStreamCacheConfig that = (DefaultStreamCacheConfig) o;
        return dispatcherName.equals(that.dispatcherName) &&
                retryDelay.equals(that.retryDelay) &&
                genericCacheConfig.equals(that.genericCacheConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dispatcherName, retryDelay, genericCacheConfig);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "dispatcherName=" + dispatcherName +
                ", retryDelay=" + retryDelay +
                ", genericCacheConfig=" + genericCacheConfig +
                "]";
    }

}
