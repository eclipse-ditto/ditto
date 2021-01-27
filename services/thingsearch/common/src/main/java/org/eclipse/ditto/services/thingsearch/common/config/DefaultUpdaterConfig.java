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

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.ConfigWithFallback;

import com.typesafe.config.Config;

/**
 * This class is the default implementation for {@link UpdaterConfig}.
 */
@Immutable
public final class DefaultUpdaterConfig implements UpdaterConfig {

    /**
     * Path where the updater config values are expected.
     */
    static final String CONFIG_PATH = "updater";
    static final String THINGS_SYNC_CONFIG_PATH = "sync.things";
    static final String POLICIES_SYNC_CONFIG_PATH = "sync.policies";

    private final Duration maxIdleTime;
    private final int maxBulkSize;
    private final Duration shardingStatePollInterval;
    private final boolean eventProcessingActive;
    private final BackgroundSyncConfig backgroundSyncConfig;
    private final StreamConfig streamConfig;

    private DefaultUpdaterConfig(final ConfigWithFallback updaterScopedConfig) {
        maxIdleTime = updaterScopedConfig.getDuration(UpdaterConfigValue.MAX_IDLE_TIME.getConfigPath());
        maxBulkSize = updaterScopedConfig.getInt(UpdaterConfigValue.MAX_BULK_SIZE.getConfigPath());
        shardingStatePollInterval =
                updaterScopedConfig.getDuration(UpdaterConfigValue.SHARDING_STATE_POLL_INTERVAL.getConfigPath());
        eventProcessingActive =
                updaterScopedConfig.getBoolean(UpdaterConfigValue.EVENT_PROCESSING_ACTIVE.getConfigPath());
        backgroundSyncConfig = DefaultBackgroundSyncConfig.fromUpdaterConfig(updaterScopedConfig);
        streamConfig = DefaultStreamConfig.of(updaterScopedConfig);
    }

    /**
     * Returns an instance of DefaultUpdaterConfig based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the updater config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultUpdaterConfig of(final Config config) {
        return new DefaultUpdaterConfig(
                ConfigWithFallback.newInstance(config, CONFIG_PATH, UpdaterConfigValue.values()));
    }

    @Override
    public Duration getMaxIdleTime() {
        return maxIdleTime;
    }

    @Override
    public int getMaxBulkSize() {
        return maxBulkSize;
    }

    @Override
    public Duration getShardingStatePollInterval() {
        return shardingStatePollInterval;
    }

    @Override
    public boolean isEventProcessingActive() {
        return eventProcessingActive;
    }

    @Override
    public BackgroundSyncConfig getBackgroundSyncConfig() {
        return backgroundSyncConfig;
    }

    @Override
    public StreamConfig getStreamConfig() {
        return streamConfig;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultUpdaterConfig that = (DefaultUpdaterConfig) o;
        return maxBulkSize == that.maxBulkSize &&
                eventProcessingActive == that.eventProcessingActive &&
                Objects.equals(maxIdleTime, that.maxIdleTime) &&
                Objects.equals(shardingStatePollInterval, that.shardingStatePollInterval) &&
                Objects.equals(backgroundSyncConfig, that.backgroundSyncConfig) &&
                Objects.equals(streamConfig, that.streamConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(maxIdleTime, maxBulkSize, shardingStatePollInterval, eventProcessingActive,
                backgroundSyncConfig, streamConfig);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "maxIdleTime=" + maxIdleTime +
                ", maxBulkSize=" + maxBulkSize +
                ", shardingStatePollInterval=" + shardingStatePollInterval +
                ", eventProcessingActive=" + eventProcessingActive +
                ", backgroundSyncConfig=" + backgroundSyncConfig +
                ", streamConfig=" + streamConfig +
                "]";
    }

}
