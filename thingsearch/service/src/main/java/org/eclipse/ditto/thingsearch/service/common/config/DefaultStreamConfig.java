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

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.cacheloaders.config.AskWithRetryConfig;
import org.eclipse.ditto.internal.utils.cacheloaders.config.DefaultAskWithRetryConfig;
import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of {@link StreamConfig}.
 */
@Immutable
public final class DefaultStreamConfig implements StreamConfig {

    private static final String CONFIG_PATH = "stream";
    private static final String RETRIEVAL_CONFIG_PATH = "retrieval";
    private static final String ASK_WITH_RETRY_CONFIG_PATH = "ask-with-retry";

    private static final String POLICY_CACHE_CONFIG_PATH = "policy-cache";
    private static final String THING_CACHE_CONFIG_PATH = "thing-cache";

    private final int maxArraySize;
    private final Duration writeInterval;
    private final Duration thingDeletionTimeout;
    private final AskWithRetryConfig askWithRetryConfig;
    private final StreamStageConfig retrievalConfig;
    private final PersistenceStreamConfig persistenceStreamConfig;
    private final StreamCacheConfig policyCacheConfig;
    private final StreamCacheConfig thingCacheConfig;

    private DefaultStreamConfig(final ConfigWithFallback streamScopedConfig) {
        maxArraySize = streamScopedConfig.getInt(StreamConfigValue.MAX_ARRAY_SIZE.getConfigPath());
        writeInterval = streamScopedConfig.getNonNegativeDurationOrThrow(StreamConfigValue.WRITE_INTERVAL);
        thingDeletionTimeout =
                streamScopedConfig.getNonNegativeDurationOrThrow(StreamConfigValue.THING_DELETION_TIMEOUT);
        askWithRetryConfig = DefaultAskWithRetryConfig.of(streamScopedConfig, ASK_WITH_RETRY_CONFIG_PATH);
        retrievalConfig = DefaultStreamStageConfig.getInstance(streamScopedConfig, RETRIEVAL_CONFIG_PATH);
        persistenceStreamConfig = DefaultPersistenceStreamConfig.of(streamScopedConfig);
        policyCacheConfig = DefaultStreamCacheConfig.of(streamScopedConfig, POLICY_CACHE_CONFIG_PATH);
        thingCacheConfig = DefaultStreamCacheConfig.of(streamScopedConfig, THING_CACHE_CONFIG_PATH);
    }

    /**
     * Returns an instance of DefaultStreamConfig based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the stream config at {@value CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
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
    public Duration getThingDeletionTimeout() {
        return thingDeletionTimeout;
    }

    @Override
    public AskWithRetryConfig getAskWithRetryConfig() {
        return askWithRetryConfig;
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
    public StreamCacheConfig getPolicyCacheConfig() {
        return policyCacheConfig;
    }

    @Override
    public StreamCacheConfig getThingCacheConfig() {
        return thingCacheConfig;
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
                thingDeletionTimeout.equals(that.thingDeletionTimeout) &&
                askWithRetryConfig.equals(that.askWithRetryConfig) &&
                retrievalConfig.equals(that.retrievalConfig) &&
                persistenceStreamConfig.equals(that.persistenceStreamConfig) &&
                policyCacheConfig.equals(that.policyCacheConfig) &&
                thingCacheConfig.equals(that.thingCacheConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(maxArraySize, writeInterval, askWithRetryConfig, retrievalConfig,
                persistenceStreamConfig, policyCacheConfig, thingCacheConfig, thingDeletionTimeout);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "maxArraySize=" + maxArraySize +
                ", writeInterval=" + writeInterval +
                ", thingDeletionTimeout=" + thingDeletionTimeout +
                ", askWithRetryConfig=" + askWithRetryConfig +
                ", retrievalConfig=" + retrievalConfig +
                ", persistenceStreamConfig=" + persistenceStreamConfig +
                ", policyCacheConfig=" + policyCacheConfig +
                ", thingCacheConfig=" + thingCacheConfig +
                "]";
    }

}
