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

import com.typesafe.config.Config;

/**
 * Metrics configuration
 */
public final class MetricsConfigReader extends AbstractConfigReader {

    private static final boolean DEFAULT_METRICS_ENABLED = false;

    private static final String ENABLED_KEY = "enabled";

    private static final String SYSTEM_METRICS_KEY = "systemMetrics";
    private static final String PATH_SYSTEM_METRICS_ENABLED = path(SYSTEM_METRICS_KEY, ENABLED_KEY);

    private static final String PROMETHEUS_KEY = "prometheus";
    private static final String PATH_PROMETHEUS_ENABLED = path(PROMETHEUS_KEY, ENABLED_KEY);
    private static final String PATH_PROMETHEUS_HOSTNAME = path(PROMETHEUS_KEY, "hostname");
    private static final String PATH_PROMETHEUS_PORT = path(PROMETHEUS_KEY, "port");

    MetricsConfigReader(final Config config) {
        super(config);
    }

    /**
     * Indicates if system metrics are enabled.
     *
     * @return True if system metrics are enabled false if not.
     */
    public boolean isSystemMetricsEnabled() {
        return getIfPresent(PATH_SYSTEM_METRICS_ENABLED, config::getBoolean).orElse(DEFAULT_METRICS_ENABLED);
    }

    /**
     * Indicates if prometheus is enabled.
     *
     * @return True if prometheus is enabled false if not.
     */
    public boolean isPrometheusEnabled() {
        return getIfPresent(PATH_PROMETHEUS_ENABLED, config::getBoolean).orElse(DEFAULT_METRICS_ENABLED);
    }

    /**
     * Returns the hostname to bind the prometheus HTTP server to.
     *
     * @return the hostname to bind the prometheus HTTP server to.
     */
    public String getPrometheusHostname() {
        return getIfPresent(PATH_PROMETHEUS_HOSTNAME, config::getString).orElse("0.0.0.0");
    }

    /**
     * Returns the port to bind the prometheus HTTP server to.
     *
     * @return the port to bind the prometheus HTTP server to.
     */
    public Integer getPrometheusPort() {
        return getIfPresent(PATH_PROMETHEUS_PORT, config::getInt).orElse(9095);
    }

}
