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

package org.eclipse.ditto.services.connectivity.messaging.config;

import java.time.Duration;
import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.ConfigWithFallback;

import com.typesafe.config.Config;

/**
 * Default implementation of {@link org.eclipse.ditto.services.connectivity.messaging.config.MonitoringLoggerConfig}.
 */
@Immutable
public final class DefaultMonitoringLoggerConfig implements MonitoringLoggerConfig {

    private static final String CONFIG_PATH = "logger";

    private final int successCapacity;
    private final int failureCapacity;
    private final Duration logDuration;
    private final Duration loggingActiveCheckInterval;

    private DefaultMonitoringLoggerConfig(final ConfigWithFallback config) {
        successCapacity = config.getInt(MonitoringLoggerConfigValue.SUCCESS_CAPACITY.getConfigPath());
        failureCapacity = config.getInt(MonitoringLoggerConfigValue.FAILURE_CAPACITY.getConfigPath());
        logDuration = config.getDuration(MonitoringLoggerConfigValue.LOG_DURATION.getConfigPath());
        loggingActiveCheckInterval =
                config.getDuration(MonitoringLoggerConfigValue.LOGGING_ACTIVE_CHECK_INTERVAL.getConfigPath());
    }

    /**
     * Returns {@link org.eclipse.ditto.services.connectivity.messaging.config.MonitoringLoggerConfig}.
     *
     * @param config is supposed to provide the settings of the connection config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static MonitoringLoggerConfig of(final Config config) {
        return new DefaultMonitoringLoggerConfig(
                ConfigWithFallback.newInstance(config, CONFIG_PATH, MonitoringLoggerConfigValue.values()));
    }

    @Override
    public int successCapacity() {
        return successCapacity;
    }

    @Override
    public int failureCapacity() {
        return failureCapacity;
    }

    @Override
    public Duration logDuration() {
        return logDuration;
    }

    @Override
    public Duration loggingActiveCheckInterval() {
        return loggingActiveCheckInterval;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultMonitoringLoggerConfig that = (DefaultMonitoringLoggerConfig) o;
        return successCapacity == that.successCapacity &&
                failureCapacity == that.failureCapacity &&
                Objects.equals(logDuration, that.logDuration) &&
                Objects.equals(loggingActiveCheckInterval, that.loggingActiveCheckInterval);
    }

    @Override
    public int hashCode() {
        return Objects.hash(successCapacity, failureCapacity, logDuration, loggingActiveCheckInterval);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                ", successCapacity=" + successCapacity +
                ", failureCapacity=" + failureCapacity +
                ", logDuration=" + logDuration +
                ", loggingActiveCheckInterval=" + loggingActiveCheckInterval +
                "]";
    }

}
