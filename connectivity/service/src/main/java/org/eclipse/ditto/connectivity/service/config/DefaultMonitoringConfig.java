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

package org.eclipse.ditto.connectivity.service.config;

import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * Config reader that provides access to the monitoring config values of a connection.
 */
@Immutable
final class DefaultMonitoringConfig implements MonitoringConfig {

    private static final String CONFIG_PATH = "monitoring";

    private final MonitoringLoggerConfig loggerConfig;

    private DefaultMonitoringConfig(final ScopedConfig config) {
        loggerConfig = DefaultMonitoringLoggerConfig.of(config);
    }

    /**
     * Returns an instance of {@code DefaultKafkaConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the Kafka config setting at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static MonitoringConfig of(final Config config) {
        return new DefaultMonitoringConfig(DefaultScopedConfig.newInstance(config, CONFIG_PATH));
    }

    /**
     * Get the config reader for the logger.
     *
     * @return the config reader for the logger.
     */
    @Override
    public MonitoringLoggerConfig logger() {
        return loggerConfig;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultMonitoringConfig that = (DefaultMonitoringConfig) o;
        return Objects.equals(loggerConfig, that.loggerConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(loggerConfig);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "loggerConfig=" + loggerConfig +
                "]";
    }

}
