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
package org.eclipse.ditto.internal.utils.persistentactors.config;

import java.time.Duration;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

/**
 * Provides configuration settings for the {@code PersistencePingActor}.
 */
@Immutable
public interface PingConfig {

    /**
     * Returns the journal {@code tag} to use for querying the event journal collection. Only persistenceIds which have
     * at least one event in the event journal with that tag are selected for sending the ping-message to.
     * May also be kept empty intentionally in order to select all persistenceIds, no matter if tagged or not.
     *
     * @return the journal tag to use for querying the event journal.
     */
    String getJournalTag();

    /**
     * Returns the initial delay for waking up the persistent actors after the PersistenceWakingUpActor has been started.
     *
     * @return the initial delay.
     */
    Duration getInitialDelay();

    /**
     * Returns the interval for trying to ping all started persistent actors which should always be alive.
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
     * Returns the order in which the {@code PersistencePingActor} will stream the elements.
     *
     * @return the streaming order.
     */
    StreamingOrder getStreamingOrder();

    /**
     * The order in which the {@code PersistencePingActor} will stream the elements.
     */
    enum StreamingOrder {

        /**
         * Elements will be ordered by their document ID.
         */
        ID,

        /**
         * Elements will be ordered by their tags.
         */
        TAGS

    }

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code PingConfig}.
     */
    enum PingConfigValue implements KnownConfigValue {

        /**
         * The journal tag to use for querying the event journal. Empty if all persistenceIds in the journal should be
         * selected.
         */
        JOURNAL_TAG("journal-tag", ""),

        /**
         * The initial delay for waking up the persistent actors after the PersistenceWakingUpActor has been started.
         */
        INITIAL_DELAY("initial-delay", Duration.ofSeconds(0L)),

        /**
         * The interval for trying to ping all started persistent actors which should always be alive.
         */
        INTERVAL("interval", Duration.ofMinutes(10L)),

        /**
         * The number of events to read in one query.
         */
        READ_JOURNAL_BATCH_SIZE("read-journal-batch-size", 500),

        /**
         * The field to order the events in the source.
         */
        STREAMING_ORDER("streaming-order", StreamingOrder.ID.name());

        private final String path;
        private final Object defaultValue;

        PingConfigValue(final String thePath, final Object theDefaultValue) {
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
