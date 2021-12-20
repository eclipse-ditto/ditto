/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

import java.time.Duration;
import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;

import com.typesafe.config.Config;

/**
 * Default implementation of {@link MonitoringLoggerConfig}.
 */
@Immutable
public final class DefaultMonitoringLoggerConfig implements MonitoringLoggerConfig {

    private static final String CONFIG_PATH = "logger";

    private final int successCapacity;
    private final int failureCapacity;
    private final long maxLogSizeInBytes;
    private final Duration logDuration;
    private final Duration loggingActiveCheckInterval;
    private final LoggerPublisherConfig loggerPublisherConfig;

    private DefaultMonitoringLoggerConfig(final ConfigWithFallback config) {
        successCapacity = config.getNonNegativeIntOrThrow(MonitoringLoggerConfigValue.SUCCESS_CAPACITY);
        failureCapacity = config.getNonNegativeIntOrThrow(MonitoringLoggerConfigValue.FAILURE_CAPACITY);
        maxLogSizeInBytes = config.getNonNegativeLongOrThrow(MonitoringLoggerConfigValue.MAX_LOG_SIZE_BYTES);
        logDuration = config.getNonNegativeAndNonZeroDurationOrThrow(MonitoringLoggerConfigValue.LOG_DURATION);
        loggingActiveCheckInterval =
                config.getNonNegativeAndNonZeroDurationOrThrow(MonitoringLoggerConfigValue.LOGGING_ACTIVE_CHECK_INTERVAL);
        loggerPublisherConfig = DefaultLoggerPublisherConfig.of(config);
    }

    /**
     * Returns {@link MonitoringLoggerConfig}.
     *
     * @param config is supposed to provide the settings of the connection config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
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
    public long maxLogSizeInBytes() {
        return maxLogSizeInBytes;
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
    public LoggerPublisherConfig getLoggerPublisherConfig() {
        return loggerPublisherConfig;
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
                maxLogSizeInBytes == that.maxLogSizeInBytes &&
                Objects.equals(logDuration, that.logDuration) &&
                Objects.equals(loggingActiveCheckInterval, that.loggingActiveCheckInterval) &&
                Objects.equals(loggerPublisherConfig, that.loggerPublisherConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(successCapacity, failureCapacity, maxLogSizeInBytes, logDuration,
                loggingActiveCheckInterval, loggerPublisherConfig);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "successCapacity=" + successCapacity +
                ", failureCapacity=" + failureCapacity +
                ", maxLogSizeInBytes=" + maxLogSizeInBytes +
                ", logDuration=" + logDuration +
                ", loggingActiveCheckInterval=" + loggingActiveCheckInterval +
                ", loggerPublisherConfig=" + loggerPublisherConfig +
                "]";
    }

}
