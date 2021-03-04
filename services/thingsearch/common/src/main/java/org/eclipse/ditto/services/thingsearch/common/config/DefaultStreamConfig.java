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
package org.eclipse.ditto.services.thingsearch.common.config;

import java.time.Duration;
import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.ConfigWithFallback;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of {@link StreamConfig}.
 */
@Immutable
public final class DefaultStreamConfig implements StreamConfig {
    private static final String CONFIG_PATH = "stream";
    private static final String RETRIEVAL_CONFIG_PATH = "retrieval";

    private final int maxArraySize;
    private final Duration writeInterval;
    private final Duration askTimeout;
    private final DefaultStreamStageConfig retrievalConfig;
    private final DefaultPersistenceStreamConfig persistenceStreamConfig;
    private final DefaultStreamCacheConfig streamCacheConfig;

    private DefaultStreamConfig(final ConfigWithFallback streamScopedConfig) {
        maxArraySize = streamScopedConfig.getInt(StreamConfigValue.MAX_ARRAY_SIZE.getConfigPath());
        writeInterval = streamScopedConfig.getDuration(StreamConfigValue.WRITE_INTERVAL.getConfigPath());
        askTimeout = streamScopedConfig.getDuration(StreamConfigValue.ASK_TIMEOUT.getConfigPath());
        retrievalConfig = DefaultStreamStageConfig.getInstance(streamScopedConfig, RETRIEVAL_CONFIG_PATH);
        persistenceStreamConfig = DefaultPersistenceStreamConfig.of(streamScopedConfig);
        streamCacheConfig = DefaultStreamCacheConfig.of(streamScopedConfig);
    }

    /**
     * Returns an instance of DefaultStreamConfig based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the stream config at {@value CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultStreamConfig of(final Config config) {
        return new DefaultStreamConfig(ConfigWithFallback.newInstance(config, CONFIG_PATH, StreamConfigValue.values()));
    }

    @Override
    public int getMaxArraySize() {
        return maxArraySize;
    }

    @Override
    public Duration getWriteInterval() {
        return writeInterval;
    }

    @Override
    public Duration getAskTimeout() {
        return askTimeout;
    }

    @Override
    public StreamStageConfig getRetrievalConfig() {
        return retrievalConfig;
    }

    @Override
    public PersistenceStreamConfig getPersistenceConfig() {
        return persistenceStreamConfig;
    }

    @Override
    public StreamCacheConfig getCacheConfig() {
        return streamCacheConfig;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultStreamConfig that = (DefaultStreamConfig) o;
        return maxArraySize == that.maxArraySize &&
                writeInterval.equals(that.writeInterval) &&
                askTimeout.equals(that.askTimeout) &&
                retrievalConfig.equals(that.retrievalConfig) &&
                persistenceStreamConfig.equals(that.persistenceStreamConfig) &&
                streamCacheConfig.equals(that.streamCacheConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(maxArraySize, writeInterval, askTimeout, retrievalConfig, persistenceStreamConfig,
                streamCacheConfig);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "maxArraySize=" + maxArraySize +
                ", writeInterval=" + writeInterval +
                ", askTimeout=" + askTimeout +
                ", retrievalConfig=" + retrievalConfig +
                ", persistenceStreamConfig=" + persistenceStreamConfig +
                ", streamCacheConfig=" + streamCacheConfig +
                "]";
    }

}
