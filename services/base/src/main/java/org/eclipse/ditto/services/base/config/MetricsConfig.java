/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */package org.eclipse.ditto.services.base.config;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.KnownConfigValue;

/**
 * Provides the configuration settings of metrics.
 * <p>
 * Java serialization is supported for {@code MetricsConfig}.
 * </p>
 */
@Immutable
public interface MetricsConfig {

    /**
     * Indicates whether system metrics are enabled.
     *
     * @return {@code true} if system metrics are enabled, {@code false} if not.
     */
    boolean isSystemMetricsEnabled();

    /**
     * Indicates whether Prometheus is enabled.
     *
     * @return {@code true} if Prometheus is enabled, {@code false} if not.
     */
    boolean isPrometheusEnabled();

    /**
     * Returns the hostname to bind the Prometheus HTTP server to.
     *
     * @return the hostname.
     */
    String getPrometheusHostname();

    /**
     * Returns the port to bind the Prometheus HTTP server to.
     *
     * @return the port.
     */
    int getPrometheusPort();

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code MetricsConfig}.
     */
    enum MetricsConfigValue implements KnownConfigValue {

        /**
         * Determines whether system metrics are enabled.
         */
        SYSTEM_METRICS_ENABLED("systemMetrics.enabled", false),

        /**
         * Determines whether Prometheus is enabled.
         */
        PROMETHEUS_ENABLED("prometheus.enabled", false),

        /**
         * The hostname to bind the Prometheus HTTP server to.
         */
        PROMETHEUS_HOSTNAME("prometheus.hostname", "0.0.0.0"),

        /**
         * The port to bind the Prometheus HTTP server to.
         */
        PROMETHEUS_PORT("prometheus.port", 9095);

        private final String path;
        private final Object defaultValue;

        private MetricsConfigValue(final String thePath, final Object theDefaultValue) {
            path = thePath;
            defaultValue = theDefaultValue;
        }

        @Override
        public String getConfigPath() {
            return path;
        }

        @Override
        public Object getDefaultValue() {
            return defaultValue;
        }

    }

}
