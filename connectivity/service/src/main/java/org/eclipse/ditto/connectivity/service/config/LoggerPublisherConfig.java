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

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

/**
 * Config for the connection log publisher to a fluentd/fluentbit endpoint.
 */
@Immutable
public interface LoggerPublisherConfig {

    /**
     * Indicates whether publishing connection logs to a fluentd/fluentbit endpoint should be enabled.
     *
     * @return {@code true} if connection logs should be published, {@code false} else.
     */
    boolean isEnabled();

    /**
     * Returns the config for the fluency library used to forward logs to fluentd/fluentbit.
     *
     * @return the fluency library config.
     */
    FluencyLoggerPublisherConfig getFluencyLoggerPublisherConfig();

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code LoggerPublisherConfig}.
     */
    enum ConfigValue implements KnownConfigValue {

        /**
         * Whether publishing to fluentd/fluentbit publishing is enabled.
         */
        ENABLED("enabled", false);


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

