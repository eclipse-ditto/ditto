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
 * Provides the configuration settings of metrics reporting inside of persistence health checking.
 */
public interface MetricsReporterConfig {

    /**
     * Returns the resolution how far apart each measurement should be done.
     *
     * @return the resolution how far apart each measurement should be done.
     */
    Duration getResolution();

    /**
     * Returns how many historical items to keep.
     *
     * @return how many historical items to keep.
     */
    int getHistory();

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code MetricsReporterConfig}.
     */
    enum MetricsReporterConfigValue implements KnownConfigValue {

        /**
         * The resolution how far apart each measurement should be done
         */
        RESOLUTION("resolution", Duration.ofSeconds(5)),

        /**
         * The amount of historical items to keep.
         */
        HISTORY("history", 5);

        private final String path;
        private final Object defaultValue;

        private MetricsReporterConfigValue(final String thePath, final Object theDefaultValue) {
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
