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
package org.eclipse.ditto.services.gateway.util.config.endpoints;

import java.time.Duration;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.KnownConfigValue;

/**
 * Provides configuration settings for incoming commands (via HTTP requests) in the gateway.
 */
@Immutable
public interface CommandConfig {

    /**
     * Returns the default timeout of requested command.
     *
     * @return the default timeout.
     */
    Duration getDefaultTimeout();

    /**
     * Returns the maximum possible timeout of requested command.
     *
     * @return the maximum timeout.
     */
    Duration getMaxTimeout();

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code CommandConfig}.
     */
    enum CommandConfigValue implements KnownConfigValue {

        /**
         * The default timeout of requested command.
         */
        DEFAULT_TIMEOUT("default-timeout", "10s"),

        /**
         * The maximum possible timeout of requested command.
         */
        MAX_TIMEOUT("max-timeout", "1m");

        private final String path;
        private final Object defaultValue;

        CommandConfigValue(final String thePath, final Object theDefaultValue) {
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
