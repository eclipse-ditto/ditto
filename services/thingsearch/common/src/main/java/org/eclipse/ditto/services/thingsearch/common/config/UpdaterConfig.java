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
package org.eclipse.ditto.services.thingsearch.common.config;

import java.time.Duration;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.akka.streaming.SyncConfig;
import org.eclipse.ditto.services.utils.config.KnownConfigValue;

/**
 * Provides the configuration settings for the search updating functionality.
 */
@Immutable
public interface UpdaterConfig {

    /**
     * Returns the lifetime of an idling ThingUpdater.
     *
     * @return the max idle time.
     */
    Duration getMaxIdleTime();

    /**
     * Returns the amount of write operations to perform in one bulk.
     *
     * @return the max bulk size.
     */
    int getMaxBulkSize();

    /**
     * Returns how often things-updater polls local sharding state and how often the new-event-forwarder polls
     * cluster state.
     *
     * @return the poll interval.
     */
    Duration getShardingStatePollInterval();

    /**
     * Indicates whether event processing should be active.
     *
     * @return {@code true} if event processing should be active, {@code false} else.
     */
    boolean isEventProcessingActive();

    /**
     * Returns the synchronization settings for the Things service.
     *
     * @return the config.
     */
    SyncConfig getThingsSyncConfig();

    /**
     * Returns the synchronization settings for the Policies service.
     *
     * @return the config.
     */
    SyncConfig getPoliciesSyncConfig();

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * UpdaterConfig.
     */
    enum UpdaterConfigValue implements KnownConfigValue {

        /**
         * Determines the lifetime of an idling ThingUpdater.
         */
        MAX_IDLE_TIME("max-idle-time", Duration.ofHours(25L)),

        /**
         * Determines how many write operations to perform in one bulk.
         */
        MAX_BULK_SIZE("max-bulk-size", Integer.MAX_VALUE),

        /**
         * How often sharding state is polled. The intervening events are lost after shard rebalancing
         * and require background sync to be accounted for.
         */
        SHARDING_STATE_POLL_INTERVAL("sharding-state-poll-interval", Duration.ofSeconds(10L)),

        /**
         * Determines whether event processing should be active.
         */
        EVENT_PROCESSING_ACTIVE("event-processing-active", true);

        private final String path;
        private final Object defaultValue;

        private UpdaterConfigValue(final String thePath, final Object theDefaultValue) {
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
