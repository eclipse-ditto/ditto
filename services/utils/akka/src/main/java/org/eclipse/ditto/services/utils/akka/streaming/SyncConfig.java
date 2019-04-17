/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.utils.akka.streaming;

import java.time.Duration;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.KnownConfigValue;

/**
 * Provides configuration settings for the synchronization of service properties towards Search service.
 * <p>
 * Java serialization is supported for SyncConfig.
 * </p>
 */
@Immutable
public interface SyncConfig {

    /**
     * Indicates whether synchronization of properties of a particular service should be active.
     *
     * @return {@code true} if synchronization should be active, {@code false} else.
     */
    boolean isEnabled();

    /**
     * Returns the offset for the start timestamp.
     * It is needed to make sure that we don't lose events, because the timestamp of a thing-event is created before the
     * actual insert to the DB.
     *
     * @return the offset.
     */
    Duration getStartOffset();

    /**
     * Returns the duration starting from which the modified tags are requested for the first time (further syncs will
     * know the last-success timestamp).
     *
     * @return the offset.
     */
    Duration getInitialStartOffset();

    /**
     * Returns the interval that defines the minimum and maximum creation time of the entities to be queried by the
     * underlying stream.
     *
     * @return the interval.
     */
    Duration getStreamInterval();

    /**
     * If a query-start is more than this offset in the past, a warning will be logged.
     *
     * @return the offset.
     */
    Duration getOutdatedWarningOffset();

    /**
     * If a query-start is more than this offset in the past, an error will be logged.
     *
     * @return the offset.
     */
    Duration getOutdatedErrorOffset();

    /**
     * Returns the maximum idle time of a stream forwarder.
     * A stream is considered idle when it does not retrieve any message.
     *
     * @return the max idle time.
     */
    Duration getMaxIdleTime();

    /**
     * Returns the timeout at the streaming actor (server) side.
     *
     * @return the timeout.
     */
    Duration getStreamingActorTimeout();

    /**
     * Returns the amount of elements to be streamed per batch.
     *
     * @return the batch size.
     */
    int getElementsStreamedPerBatch();

    /**
     * Returns the minimal delay between streams.
     *
     * @return the minimal delay.
     */
    Duration getMinimalDelayBetweenStreams();

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * SyncConfig.
     */
    enum SyncConfigValue implements KnownConfigValue {

        /**
         * Determines whether synchronization of properties of a particular service should be enabled.
         */
        ENABLED("active", true),

        /**
         * The offset for the start timestamp.
         * It is needed to make sure that we don't lose events, because the timestamp of a thing-event is created before
         * the actual insert to the DB.
         */
        START_OFFSET("start-offset", Duration.ofMinutes(30L)),

        /**
         * The duration starting from which the modified tags are requested for the first time (further syncs will know
         * the last-success timestamp).
         */
        INITIAL_START_OFFSET("initial-start-offset", Duration.ofHours(2L)),

        /**
         * This interval defines the minimum and maximum creation time of the entities to be queried by the underlying
         * stream.
         */
        STREAM_INTERVAL("stream-interval", Duration.ofMinutes(5L)),

        /**
         * If a query-start is more than this offset in the past, a warning will be logged.
         */
        OUTDATED_WARNING_OFFSET("outdated-warning-offset", Duration.ofHours(3L)),

        /**
         * If a query-start is more than this offset in the past, a error will be logged.
         */
        OUTDATED_ERROR_OFFSET("outdated-error-offset", Duration.ofHours(4L)),

        /**
         * Determines the maximum idle time of a stream forwarder.
         * A stream is considered idle when it does not retrieve any messages.
         */
        MAX_IDLE_TIME("max-idle-time", Duration.ofMinutes(1L)),

        /**
         * Determines timeout at the streaming actor (server) side.
         */
        STREAMING_ACTOR_TIMEOUT("streaming-actor-timeout", Duration.ofMinutes(5L)),

        /**
         * Determines the amount of elements to be streamed per batch.
         */
        ELEMENT_STREAM_BATCH_SIZE("elements-streamed-per-batch", 10),

        /**
         * Determines the minimal delay between streams.
         */
        MINIMAL_DELAY_BETWEEN_STREAMS("minimal-delay-between-streams", Duration.ofSeconds(3L));

        private final String path;
        private final Object defaultValue;

        private SyncConfigValue(final String thePath, final Object theDefaultValue) {
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
