/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
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
    private static final String PATH_SYSTEM_METRICS_ENABLED = path( SYSTEM_METRICS_KEY, ENABLED_KEY);

    private static final String PROMETHEUS_KEY = "prometheus";
    private static final String PATH_PROMETHEUS_ENABLED = path(PROMETHEUS_KEY, ENABLED_KEY);

    private static final String JAEGER_KEY = "jaeger";
    private static final String PATH_JAEGER_ENABLED = path(JAEGER_KEY, ENABLED_KEY);

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
     * Indicates if jaeger is enabled.
     *
     * @return True if jaeger is enabled false if not.
     */
    public boolean isJaegerEnabled() {
        return getIfPresent(PATH_JAEGER_ENABLED, config::getBoolean).orElse(DEFAULT_METRICS_ENABLED);
    }
}
