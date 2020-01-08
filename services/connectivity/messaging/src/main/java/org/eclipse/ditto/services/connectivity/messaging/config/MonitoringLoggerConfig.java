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

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.KnownConfigValue;

/**
 * Configuration for monitoring loggers.
 */
@Immutable
public interface MonitoringLoggerConfig {

    /**
     * Returns the number of success logs stored for each {@link org.eclipse.ditto.model.connectivity.LogCategory} and
     * {@link org.eclipse.ditto.model.connectivity.LogType}.
     *
     * @return the success capacity.
     */
    int successCapacity();

    /**
     * Returns the number of failure logs stored for each {@link org.eclipse.ditto.model.connectivity.LogCategory} and
     * {@link org.eclipse.ditto.model.connectivity.LogType}.
     *
     * @return the failure capacity.
     */
    int failureCapacity();

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
     * An enumeration of the known config path expressions and their associated default values for {@code
     * ExponentialBackOffConfig}.
     */
    enum MonitoringLoggerConfigValue implements KnownConfigValue {

        /**
         * The number of success logs stored for each {@link org.eclipse.ditto.model.connectivity.LogCategory} and
         * {@link org.eclipse.ditto.model.connectivity.LogType}.
         */
        SUCCESS_CAPACITY("successCapacity", 10),

        /**
         * The number of failure logs stored for each {@link org.eclipse.ditto.model.connectivity.LogCategory} and
         * {@link org.eclipse.ditto.model.connectivity.LogType}.
         */
        FAILURE_CAPACITY("failureCapacity", 10),

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
