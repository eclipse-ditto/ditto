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

import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

/**
 * Provides the configuration settings of health check persistence.
 */
public interface PersistenceConfig {

    /**
     * Indicates whether the persistence health check should be enabled.
     *
     * @return {@code true} if the persistence health check should be enabled, {@code false} else.
     */
    boolean isEnabled();

    /**
     * Returns the timeout of the health check for persistence.
     * If the persistence takes longer than that to respond, it is considered "DOWN".
     *
     * @return the timeout of the health check for persistence.
     * @see #isEnabled()
     */
    Duration getTimeout();

    /**
     * Returns the configuration settings of the metrics reporter.
     *
     * @return the config.
     */
    MetricsReporterConfig getMetricsReporterConfig();

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code PersistenceConfig}.
     */
    enum PersistenceConfigValue implements KnownConfigValue {

        /**
         * Determines whether the persistence health check should be enabled.
         */
        ENABLED("enabled", true),

        /**
         * The timeout of the health check for persistence.
         */
        TIMEOUT("timeout", Duration.ofMinutes(1));

        private final String path;
        private final Object defaultValue;

        private PersistenceConfigValue(final String thePath, final Object theDefaultValue) {
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
