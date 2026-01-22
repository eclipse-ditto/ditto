/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.cluster.config;

import java.time.Duration;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of {@link LiveEntitiesMetricsConfig}.
 */
@Immutable
public final class DefaultLiveEntitiesMetricsConfig implements LiveEntitiesMetricsConfig {

    private static final String CONFIG_PATH = "live-entities-metrics";

    private final boolean enabled;
    private final Duration refreshInterval;

    private DefaultLiveEntitiesMetricsConfig(final ConfigWithFallback config) {
        enabled = config.getBoolean(LiveEntitiesMetricsConfigValue.ENABLED.getConfigPath());
        refreshInterval = config.getNonNegativeAndNonZeroDurationOrThrow(LiveEntitiesMetricsConfigValue.REFRESH_INTERVAL);
    }

    /**
     * Returns an instance of {@code DefaultLiveEntitiesMetricsConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the live entities metrics config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultLiveEntitiesMetricsConfig of(final Config config) {
        return new DefaultLiveEntitiesMetricsConfig(
                ConfigWithFallback.newInstance(config, CONFIG_PATH, LiveEntitiesMetricsConfigValue.values()));
    }

    /**
     * Returns an instance of {@code DefaultLiveEntitiesMetricsConfig} with default values.
     *
     * @return the instance with default values.
     */
    public static DefaultLiveEntitiesMetricsConfig ofDefaults() {
        return new DefaultLiveEntitiesMetricsConfig(
                ConfigWithFallback.newInstance(com.typesafe.config.ConfigFactory.empty(), CONFIG_PATH,
                        LiveEntitiesMetricsConfigValue.values()));
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public Duration getRefreshInterval() {
        return refreshInterval;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultLiveEntitiesMetricsConfig that = (DefaultLiveEntitiesMetricsConfig) o;
        return enabled == that.enabled &&
                Objects.equals(refreshInterval, that.refreshInterval);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, refreshInterval);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "enabled=" + enabled +
                ", refreshInterval=" + refreshInterval +
                "]";
    }

}
