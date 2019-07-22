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
package org.eclipse.ditto.services.concierge.common;

import java.time.Duration;

import org.eclipse.ditto.services.utils.config.KnownConfigValue;

/**
 * Provides configuration settings for persistence ID streaming of persistence cleanup actions.
 */
public interface PersistenceIdsConfig {

    /**
     * Return the amount of persistence IDs to request from a remote service per message.
     *
     * @return the amount of persistence IDs to send in one batch.
     */
    int getBurst();

    /**
     * Returns the maximum time to wait for a {@code SourceRef} reply when requesting a stream.
     *
     * @return timeout waiting for a {@code SourceRef}.
     */
    Duration getStreamRequestTimeout();

    /**
     * Returns the maximum time to keep the persistence ID stream open in the absence of downstream consumption.
     * The downstream typically stalls when the database is under high load or when the cluster is unhealthy.
     *
     * @return idle timeout of the persistence ID stream.
     */
    Duration getStreamIdleTimeout();

    /**
     * Enumeration of known config keys and default values for {@code PersistenceIdsConfig}
     */
    enum ConfigValue implements KnownConfigValue {

        /**
         * Persistence IDs per message.
         */
        BURST("burst", 25),

        /**
         * Timeout when starting a stream.
         */
        STREAM_REQUEST_TIMEOUT("stream-request-timeout", Duration.ofSeconds(10L)),

        /**
         * Idle timeout of the stream.
         */
        STREAM_IDLE_TIMEOUT("stream-idle-timeout", Duration.ofMinutes(10L));

        private final String path;
        private final Object defaultValue;

        ConfigValue(final String path, final Object defaultValue) {
            this.path = path;
            this.defaultValue = defaultValue;
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
