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

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.base.config.ServiceSpecificConfig.MetricsConfig;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of {@link MetricsConfig}.
 */
@Immutable
public final class DefaultMetricsConfig implements MetricsConfig {

    private enum MetricsConfigValue implements KnownConfigValue {

        SYSTEM_METRICS_ENABLED("systemMetrics.enabled", false),

        PROMETHEUS_ENABLED("prometheus.enabled", false),

        PROMETHEUS_HOSTNAME("prometheus.hostname", "0.0.0.0"),

        PROMETHEUS_PORT("prometheus.port", 9095);

        private final String path;
        private final Object defaultValue;

        private MetricsConfigValue(final String thePath, final Object theDefaultValue) {
            path = thePath;
            defaultValue = theDefaultValue;
        }

        @Override
        public String getPath() {
            return path;
        }

        @Override
        public Object getDefaultValue() {
            return defaultValue;
        }

    }

    private static final String CONFIG_PATH = "metrics";

    private final Config config;

    private DefaultMetricsConfig(final Config theConfig) {
        config = theConfig;
    }

    /**
     * Returns an instance of {@code DittoMetricsConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the metrics config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws NullPointerException if {@code config} is {@code null}.
     * @throws com.typesafe.config.ConfigException.WrongType if {@code config} did not contain a nested {@code Config}
     * for {@value #CONFIG_PATH}.
     */
    public static DefaultMetricsConfig of(final Config config) {
        return new DefaultMetricsConfig(
                ConfigWithFallback.newInstance(config, CONFIG_PATH, MetricsConfigValue.values()));
    }

    @Override
    public boolean isSystemMetricsEnabled() {
        return config.getBoolean(MetricsConfigValue.SYSTEM_METRICS_ENABLED.getPath());
    }

    @Override
    public boolean isPrometheusEnabled() {
        return config.getBoolean(MetricsConfigValue.PROMETHEUS_ENABLED.getPath());
    }

    @Override
    public String getPrometheusHostname() {
        return config.getString(MetricsConfigValue.PROMETHEUS_HOSTNAME.getPath());
    }

    @Override
    public int getPrometheusPort() {
        return config.getInt(MetricsConfigValue.PROMETHEUS_PORT.getPath());
    }

}
