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

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;

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

    private final Duration maxIdleTime;
    private final Duration shardingStatePollInterval;
    private final boolean eventProcessingActive;
    private final double forceUpdateProbability;
    private final boolean forceUpdateAfterStartEnabled;
    private final Duration forceUpdateAfterStartTimeout;
    private final double forceUpdateAfterStartRandomFactor;
    private final BackgroundSyncConfig backgroundSyncConfig;
    private final StreamConfig streamConfig;
    private final SearchPersistenceConfig updaterPersistenceConfig;

    private DefaultUpdaterConfig(final ConfigWithFallback updaterScopedConfig) {
        maxIdleTime = updaterScopedConfig.getNonNegativeDurationOrThrow(UpdaterConfigValue.MAX_IDLE_TIME);
        shardingStatePollInterval =
                updaterScopedConfig.getNonNegativeDurationOrThrow(UpdaterConfigValue.SHARDING_STATE_POLL_INTERVAL);
        eventProcessingActive =
                updaterScopedConfig.getBoolean(UpdaterConfigValue.EVENT_PROCESSING_ACTIVE.getConfigPath());
        forceUpdateProbability =
                updaterScopedConfig.getDouble(UpdaterConfigValue.FORCE_UPDATE_PROBABILITY.getConfigPath());
        forceUpdateAfterStartEnabled =
                updaterScopedConfig.getBoolean(UpdaterConfigValue.FORCE_UPDATE_AFTER_START_ENABLED.getConfigPath());
        forceUpdateAfterStartTimeout = updaterScopedConfig.getNonNegativeDurationOrThrow(
                UpdaterConfigValue.FORCE_UPDATE_AFTER_START_TIMEOUT);
        forceUpdateAfterStartRandomFactor = updaterScopedConfig.getDouble(
                UpdaterConfigValue.FORCE_UPDATE_AFTER_START_RANDOM_FACTOR.getConfigPath());
        backgroundSyncConfig = DefaultBackgroundSyncConfig.fromUpdaterConfig(updaterScopedConfig);
        streamConfig = DefaultStreamConfig.of(updaterScopedConfig);
        updaterPersistenceConfig = DefaultSearchPersistenceConfig.of(updaterScopedConfig);
    }

    /**
     * Returns an instance of DefaultUpdaterConfig based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the updater config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
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
    public Duration getShardingStatePollInterval() {
        return shardingStatePollInterval;
    }

    @Override
    public boolean isEventProcessingActive() {
        return eventProcessingActive;
    }

    @Override
    public double getForceUpdateProbability() {
        return forceUpdateProbability;
    }

    @Override
    public boolean isForceUpdateAfterStartEnabled() {
        return forceUpdateAfterStartEnabled;
    }

    @Override
    public Duration getForceUpdateAfterStartTimeout() {
        return forceUpdateAfterStartTimeout;
    }

    @Override
    public double getForceUpdateAfterStartRandomFactor() {
        return forceUpdateAfterStartRandomFactor;
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
    public SearchPersistenceConfig getUpdaterPersistenceConfig() {
        return updaterPersistenceConfig;
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
        return eventProcessingActive == that.eventProcessingActive &&
                Objects.equals(maxIdleTime, that.maxIdleTime) &&
                Objects.equals(shardingStatePollInterval, that.shardingStatePollInterval) &&
                Double.compare(forceUpdateProbability, that.forceUpdateProbability) == 0 &&
                forceUpdateAfterStartEnabled == that.forceUpdateAfterStartEnabled &&
                Objects.equals(forceUpdateAfterStartTimeout, that.forceUpdateAfterStartTimeout) &&
                Double.compare(forceUpdateAfterStartRandomFactor, that.forceUpdateAfterStartRandomFactor) == 0 &&
                Objects.equals(backgroundSyncConfig, that.backgroundSyncConfig) &&
                Objects.equals(streamConfig, that.streamConfig) &&
                Objects.equals(updaterPersistenceConfig, that.updaterPersistenceConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(maxIdleTime, shardingStatePollInterval, eventProcessingActive, forceUpdateProbability,
                forceUpdateAfterStartEnabled, forceUpdateAfterStartTimeout, forceUpdateAfterStartRandomFactor,
                backgroundSyncConfig, streamConfig, updaterPersistenceConfig);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "maxIdleTime=" + maxIdleTime +
                ", shardingStatePollInterval=" + shardingStatePollInterval +
                ", eventProcessingActive=" + eventProcessingActive +
                ", forceUpdateProbability=" + forceUpdateProbability +
                ", forceUpdateAfterStartEnabled=" + forceUpdateAfterStartEnabled +
                ", forceUpdateAfterStartTimeout=" + forceUpdateAfterStartTimeout +
                ", forceUpdateAfterStartRandomFactor=" + forceUpdateAfterStartRandomFactor +
                ", backgroundSyncConfig=" + backgroundSyncConfig +
                ", streamConfig=" + streamConfig +
                ", updaterPersistenceConfig=" + updaterPersistenceConfig +
                "]";
    }

}
