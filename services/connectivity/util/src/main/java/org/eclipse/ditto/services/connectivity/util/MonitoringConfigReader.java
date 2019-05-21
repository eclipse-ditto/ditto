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

package org.eclipse.ditto.services.connectivity.util;

import java.time.Duration;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.AbstractConfigReader;

import com.typesafe.config.Config;

/**
 * Config reader that provides access to the monitoring config values of a connection.
 */
@Immutable
public final class MonitoringConfigReader extends AbstractConfigReader {

    /**
     * Creates a MonitoringConfigReader.
     *
     * @param config the underlying Config object.
     */
    MonitoringConfigReader(final Config config) {
        super(config);
    }

    /**
     * Get the config reader for the logger.
     * @return the config reader for the logger.
     */
    public MonitoringLoggerConfigReader logger() {
        return new MonitoringLoggerConfigReader(getChildOrEmpty("logger"));
    }

    /**
     *
     * Get the config reader for the counter.
     * @return the config reader for the counter.
     */
    public MonitoringCounterConfigReader counter() {
        return new MonitoringCounterConfigReader(getChildOrEmpty("counter"));
    }

    /**
     * Contains the configurations for the loggers used in monitoring.
     */
    public static final class MonitoringLoggerConfigReader extends AbstractConfigReader {

        /**
         * Creates a MonitoringLoggerConfigReader.
         *
         * @param config the underlying Config object.
         */
        MonitoringLoggerConfigReader(final Config config) {
            super(config);
        }

        public Duration logDuration() {
            return config.getDuration("logDuration");
        }

        public int successCapacity() {
            return config.getInt("successCapacity");
        }

        public int failureCapacity() {
            return config.getInt("failureCapacity");
        }

        public Duration loggingActiveCheckDuration() { return config.getDuration("loggingActiveCheckDuration"); }

    }

    /**
     * Contains the configurations for the counters used in monitoring.
     */
    public static final class MonitoringCounterConfigReader extends AbstractConfigReader {

        /**
         * Creates a MonitoringCounterConfigReader.
         *
         * @param config the underlying Config object.
         */
        MonitoringCounterConfigReader(final Config config) {
            super(config);
        }

    }

}
