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
package org.eclipse.ditto.services.base.config;

import java.io.Serializable;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.base.config.ServiceSpecificConfig.MetricsConfig;
import org.eclipse.ditto.services.utils.config.ConfigWithFallback;
import org.eclipse.ditto.services.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of {@link MetricsConfig}.
 */
@Immutable
public final class DefaultMetricsConfig implements MetricsConfig, Serializable {

    private static final String CONFIG_PATH = "metrics";

    private static final long serialVersionUID = -7705553939958298105L;

    private final boolean systemMetricEnabled;
    private final boolean prometheusEnabled;
    private final String prometheusHostname;
    private final int prometheusPort;

    private DefaultMetricsConfig(final ScopedConfig config) {
        systemMetricEnabled = config.getBoolean(MetricsConfigValue.SYSTEM_METRICS_ENABLED.getConfigPath());
        prometheusEnabled = config.getBoolean(MetricsConfigValue.PROMETHEUS_ENABLED.getConfigPath());
        prometheusHostname = config.getString(MetricsConfigValue.PROMETHEUS_HOSTNAME.getConfigPath());
        prometheusPort = config.getInt(MetricsConfigValue.PROMETHEUS_PORT.getConfigPath());
    }

    /**
     * Returns an instance of {@code DittoMetricsConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the metrics config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if {@code config} is invalid.
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
    public boolean equals(final Object o) {
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
                prometheusHostname.equals(that.prometheusHostname);
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
