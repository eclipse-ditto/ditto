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
package org.eclipse.ditto.gateway.service.util.config.endpoints;

import java.time.Duration;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

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
     * Return the time buffer for smart channel commands. They need extra time to account for the roundtrip to
     * thing persistence.
     *
     * @return the smart channel buffer.
     */
    Duration getSmartChannelBuffer();

    /**
     * Return the limit of how many connections can be retrieved.
     * If not limited the response may become few MB in size.
     *
     * @return the limit for connections to be retrieved
     */
    int connectionsRetrieveLimit();

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
        MAX_TIMEOUT("max-timeout", "1m"),

        /**
         * The timeout buffer for smart channel commands. They need extra time to account for the roundtrip to
         * thing persistence.
         */
        SMART_CHANNEL_BUFFER("smart-channel-buffer", "10s"),

        /**
         * The limit of how many connections can be retrieved.
         * If not limited the response may become few MB in size.
         */
        CONNECTIONS_RETRIEVE_LIMIT("connections-retrieve-limit", 100);

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
