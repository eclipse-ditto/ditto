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

import org.eclipse.ditto.services.utils.akka.streaming.DefaultSyncConfig;
import org.eclipse.ditto.services.utils.akka.streaming.SyncConfig;
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
    private final Duration activityCheckInterval;
    private final boolean eventProcessingActive;
    private final SyncConfig thingsSyncConfig;
    private final SyncConfig policiesSyncConfig;

    private DefaultUpdaterConfig(final ConfigWithFallback updaterScopedConfig) {
        maxIdleTime = updaterScopedConfig.getDuration(UpdaterConfigValue.MAX_IDLE_TIME.getConfigPath());
        maxBulkSize = updaterScopedConfig.getInt(UpdaterConfigValue.MAX_BULK_SIZE.getConfigPath());
        activityCheckInterval =
                updaterScopedConfig.getDuration(UpdaterConfigValue.ACTIVITY_CHECK_INTERVAL.getConfigPath());
        eventProcessingActive =
                updaterScopedConfig.getBoolean(UpdaterConfigValue.EVENT_PROCESSING_ACTIVE.getConfigPath());
        thingsSyncConfig = DefaultSyncConfig.getInstance(updaterScopedConfig, THINGS_SYNC_CONFIG_PATH);
        policiesSyncConfig = DefaultSyncConfig.getInstance(updaterScopedConfig, POLICIES_SYNC_CONFIG_PATH);
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
    public Duration getActivityCheckInterval() {
        return activityCheckInterval;
    }

    @Override
    public boolean isEventProcessingActive() {
        return eventProcessingActive;
    }

    @Override
    public SyncConfig getThingsSyncConfig() {
        return thingsSyncConfig;
    }

    @Override
    public SyncConfig getPoliciesSyncConfig() {
        return policiesSyncConfig;
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
                Objects.equals(activityCheckInterval, that.activityCheckInterval) &&
                Objects.equals(thingsSyncConfig, that.thingsSyncConfig) &&
                Objects.equals(policiesSyncConfig, that.policiesSyncConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(maxIdleTime, maxBulkSize, activityCheckInterval, eventProcessingActive, thingsSyncConfig,
                policiesSyncConfig);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "maxIdleTime=" + maxIdleTime +
                ", maxBulkSize=" + maxBulkSize +
                ", activityCheckInterval=" + activityCheckInterval +
                ", eventProcessingActive=" + eventProcessingActive +
                ", thingsSyncConfig=" + thingsSyncConfig +
                ", policiesSyncConfig=" + policiesSyncConfig +
                "]";
    }

}
