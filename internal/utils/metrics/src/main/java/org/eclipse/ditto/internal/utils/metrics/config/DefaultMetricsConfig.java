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
package org.eclipse.ditto.internal.utils.metrics.config;

import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of {@link MetricsConfig}.
 */
@Immutable
public final class DefaultMetricsConfig implements MetricsConfig {

    private static final String CONFIG_PATH = "metrics";

    private final boolean systemMetricEnabled;
    private final boolean prometheusEnabled;
    private final String prometheusHostname;
    private final int prometheusPort;

    private DefaultMetricsConfig(final ConfigWithFallback metricsScopedConfig) {
        systemMetricEnabled = metricsScopedConfig.getBoolean(MetricsConfigValue.SYSTEM_METRICS_ENABLED.getConfigPath());
        prometheusEnabled = metricsScopedConfig.getBoolean(MetricsConfigValue.PROMETHEUS_ENABLED.getConfigPath());
        prometheusHostname = metricsScopedConfig.getString(MetricsConfigValue.PROMETHEUS_HOSTNAME.getConfigPath());
        prometheusPort = metricsScopedConfig.getNonNegativeIntOrThrow(MetricsConfigValue.PROMETHEUS_PORT);
    }

    /**
     * Returns an instance of {@code DittoMetricsConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the metrics config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultMetricsConfig of(final Config config) {
        return new DefaultMetricsConfig(
                ConfigWithFallback.newInstance(config, CONFIG_PATH, MetricsConfigValue.values()));
    }

    @Override
    public boolean isSystemMetricsEnabled() {
        return systemMetricEnabled;
    }

    @Override
    public boolean isPrometheusEnabled() {
        return prometheusEnabled;
    }

    @Override
    public String getPrometheusHostname() {
        return prometheusHostname;
    }

    @Override
    public int getPrometheusPort() {
        return prometheusPort;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultMetricsConfig that = (DefaultMetricsConfig) o;
        return systemMetricEnabled == that.systemMetricEnabled &&
                prometheusEnabled == that.prometheusEnabled &&
                prometheusPort == that.prometheusPort &&
                Objects.equals(prometheusHostname, that.prometheusHostname);
    }

    @Override
    public int hashCode() {
        return Objects.hash(systemMetricEnabled, prometheusEnabled, prometheusHostname, prometheusPort);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "systemMetricEnabled=" + systemMetricEnabled +
                ", prometheusEnabled=" + prometheusEnabled +
                ", prometheusHostname=" + prometheusHostname +
                ", prometheusPort=" + prometheusPort +
                "]";
    }

}
