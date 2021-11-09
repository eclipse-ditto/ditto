/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

import org.eclipse.ditto.base.service.config.ThrottlingConfig;
import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

import com.typesafe.config.Config;

/**
 * Contains additional configuration for throttling in scope of connections.
 */
public interface ConnectionThrottlingConfig extends ThrottlingConfig {

    /**
     * Return the factor of many unacknowledged messages are allowed in relation to {@link #getLimit()}.
     *
     * @return the factor of maximum number of messages in flight.
     */
    double getMaxInFlightFactor();

    /**
     * Returns how many unacknowledged messages are allowed utilizung {@link #getLimit()} and
     * {@link #getMaxInFlightFactor()}.
     *
     * @return the maximum number of messages in flight.
     */
    default int getMaxInFlight() {
        return (int) (getLimit() * getMaxInFlightFactor());
    }

    /**
     * Returns the tolerance in percent of the actual throttling limit when a source is considered throttled.
     *
     * @return the throttling detection tolerance
     */
    double getThrottlingDetectionTolerance();

    /**
     * Returns an instance of {@code ConnectionThrottlingConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    static ConnectionThrottlingConfig of(final Config config) {
        return DefaultConnectionThrottlingConfig.of(config);
    }


    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code ConnectionThrottlingConfig}.
     */
    enum ConfigValue implements KnownConfigValue {

        /**
         * The factor of maximum number of messages waiting for an acknowledgement per consumer in relation to the
         * {@code limit}.
         */
        MAX_IN_FLIGHT_FACTOR("max-in-flight-factor", 2.0),

        /**
         * The tolerance in percent of the actual throttling limit when a source is considered throttled.
         */
        THROTTLING_DETECTION_TOLERANCE("throttling-detection-tolerance", 0.05);

        private final String path;
        private final Object defaultValue;

        ConfigValue(final String thePath, final Object theDefaultValue) {
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
