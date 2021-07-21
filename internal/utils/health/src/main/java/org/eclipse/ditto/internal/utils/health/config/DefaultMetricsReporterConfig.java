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
package org.eclipse.ditto.internal.utils.health.config;

import java.time.Duration;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of the health check persistence {@code metrics-reporter} config.
 */
@Immutable
public final class DefaultMetricsReporterConfig implements MetricsReporterConfig {

    private static final String CONFIG_PATH = "metrics-reporter";

    private final Duration resolution;
    private final int history;

    private DefaultMetricsReporterConfig(final ScopedConfig scopedConfig) {
        resolution = scopedConfig.getNonNegativeDurationOrThrow(MetricsReporterConfigValue.RESOLUTION);
        history = scopedConfig.getPositiveIntOrThrow(MetricsReporterConfigValue.HISTORY);
    }

    /**
     * Returns an instance of {@code DefaultMetricsReporterConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the persistence config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultMetricsReporterConfig of(final Config config) {
        return new DefaultMetricsReporterConfig(
                ConfigWithFallback.newInstance(config, CONFIG_PATH, MetricsReporterConfigValue.values()));
    }

    @Override
    public Duration getResolution() {
        return resolution;
    }

    @Override
    public int getHistory() {
        return history;
    }


    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final DefaultMetricsReporterConfig that = (DefaultMetricsReporterConfig) o;
        return history == that.history &&
                Objects.equals(resolution, that.resolution);
    }

    @Override
    public int hashCode() {
        return Objects.hash(resolution, history);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "resolution=" + resolution +
                ", history=" + history +
                "]";
    }

}
