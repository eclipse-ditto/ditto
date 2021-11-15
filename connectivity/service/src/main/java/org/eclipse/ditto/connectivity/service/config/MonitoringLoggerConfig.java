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

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

/**
 * Configuration for monitoring loggers.
 */
@Immutable
public interface MonitoringLoggerConfig {

    /**
     * Returns the number of success logs stored for each {@link org.eclipse.ditto.connectivity.model.LogCategory} and
     * {@link org.eclipse.ditto.connectivity.model.LogType}.
     *
     * @return the success capacity.
     */
    int successCapacity();

    /**
     * Returns the number of failure logs stored for each {@link org.eclipse.ditto.connectivity.model.LogCategory} and
     * {@link org.eclipse.ditto.connectivity.model.LogType}.
     *
     * @return the failure capacity.
     */
    int failureCapacity();

    /**
     * Returns the maximum length of all log entries JSON representation.
     *
     * @return maximum length of all log entries JSON representation.
     */
    long maxLogSizeInBytes();

    /**
     * Returns how long logs will stay enabled after enabling them.
     *
     * @return the logging duration.
     */
    Duration logDuration();

    /**
     * Returns interval in which we check if logging timeframe was exceeded and logs need to be disabled.
     *
     * @return the interval.
     */
    Duration loggingActiveCheckInterval();

    /**
     * Returns the configuration for the connection log publisher to a fluentd/fluentbit endpoint.
     *
     * @return the configuration for the connection log publisher.
     */
    LoggerPublisherConfig getLoggerPublisherConfig();

    /**
     * An enumeration of the known config path expressions and their associated default values for {@code
     * ExponentialBackOffConfig}.
     */
    enum MonitoringLoggerConfigValue implements KnownConfigValue {

        /**
         * The number of success logs stored for each {@link org.eclipse.ditto.connectivity.model.LogCategory} and
         * {@link org.eclipse.ditto.connectivity.model.LogType}.
         */
        SUCCESS_CAPACITY("successCapacity", 10),

        /**
         * The number of failure logs stored for each {@link org.eclipse.ditto.connectivity.model.LogCategory} and
         * {@link org.eclipse.ditto.connectivity.model.LogType}.
         */
        FAILURE_CAPACITY("failureCapacity", 10),

        /**
         * The maximum length of aggregated log entries in JSON representation. This is related to maximum-frame-size.
         */
        MAX_LOG_SIZE_BYTES("maxLogSizeBytes", 250_000),

        /**
         * How long logs will stay enabled after enabling them.
         */
        LOG_DURATION("logDuration", Duration.ofHours(1)),

        /**
         * Interval in which we check if logging timeframe was exceeded and logs need to be disabled.
         */
        LOGGING_ACTIVE_CHECK_INTERVAL("loggingActiveCheckInterval", Duration.ofMinutes(5));

        private final String path;
        private final Object defaultValue;

        MonitoringLoggerConfigValue(final String thePath, final Object theDefaultValue) {
            path = thePath;
            defaultValue = theDefaultValue;
        }

        @Override
        public Object getDefaultValue() {
            return defaultValue;
        }

        @Override
        public String getConfigPath() {
            return path;
        }

    }

}
