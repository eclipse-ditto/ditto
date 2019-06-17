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
     * Returns the minimum back-off when restarting the persistence ID stream.
     *
     * @return the minimum back-off.
     */
    Duration getBackOffMin();

    /**
     * Returns the maximum back-off when restarting the persistence ID stream.
     *
     * @return the maximum back-off.
     */
    Duration getBackOffMax();

    /**
     * Returns the back-off random factor when restarting the persistence ID stream.
     *
     * @return the back-off random factor.
     */
    Double getBackOffRandomFactor();

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
        STREAM_IDLE_TIMEOUT("stream-idle-timeout", Duration.ofMinutes(10L)),

        /**
         * Minimum back-off when restarting the stream.
         */
        BACK_OFF_MIN("back-off.min", Duration.ofSeconds(1L)),

        /**
         * Maximum back-off when restarting the stream.
         */
        BACK_OFF_MAX("back-off.max", Duration.ofMinutes(2L)),

        /**
         * Back-off random factor when restarting the stream.
         */
        BACK_OFF_RANDOM_FACTOR("back-off.random-factor", 0.5);

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
