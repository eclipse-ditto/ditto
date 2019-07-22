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
 * Provides configuration settings for Connectivity service's reconnect behaviour.
 */
@Immutable
public interface ReconnectConfig {

    /**
     * Returns the initial delay for reconnection the connections after the ReconnectActor has been started.
     *
     * @return the initial delay.
     */
    Duration getInitialDelay();

    /**
     * Returns the interval for trying to reconnect all started connections.
     *
     * @return the interval.
     */
    Duration getInterval();

    /**
     * Returns the number of events to read from the event journal with one query.
     *
     * @return the number of events to read with one query.
     */
    int getReadJournalBatchSize();

    /**
     * Returns the config for recovery throttling.
     *
     * @return the config.
     */
    RateConfig getRateConfig();

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code ReconnectConfig}.
     */
    enum ReconnectConfigValue implements KnownConfigValue {

        /**
         * The initial delay for reconnection the connections after the ReconnectActor has been started.
         */
        INITIAL_DELAY("initial-delay", Duration.ofSeconds(0L)),

        /**
         * The interval for trying to reconnect all started connections.
         */
        INTERVAL("interval", Duration.ofMinutes(10L)),

        /**
         * The number of events to read in one query.
         */
        READ_JOURNAL_BATCH_SIZE("read-journal-batch-size", 500);

        private final String path;
        private final Object defaultValue;

        // enum constructors are always private.
        ReconnectConfigValue(final String thePath, final Object theDefaultValue) {
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

    /**
     * Provides configuration settings for throttling the recovery of connections.
     * The goal of this throttling is to achieve that not all connections are recovered at the same time.
     */
    @Immutable
    interface RateConfig {

        /**
         * Returns the duration (frequency) of recovery.
         * This value is used to limit the recovery rate.
         *
         * @return the frequency.
         */
        Duration getFrequency();

        /**
         * Returns the number of entities to be recovered per batch.
         * This value is used to limit the recovery rate.
         *
         * @return the number of entities.
         */
        int getEntityAmount();

        /**
         * An enumeration of the known config path expressions and their associated default values for
         * {@code RateConfig}.
         */
        enum RateConfigValue implements KnownConfigValue {

            /**
             * Returns the duration (frequency) of recovery.
             */
            FREQUENCY("frequency", Duration.ofSeconds(1L)),

            /**
             * Returns the number of entities to be recovered per batch.
             */
            ENTITIES("entities", 1);

            private final String path;
            private final Object defaultValue;

            private RateConfigValue(final String thePath, final Object theDefaultValue) {
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

}
