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
package org.eclipse.ditto.thingsearch.service.common.config;

import java.time.Duration;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

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
     * Get the probability to perform a force update even when incremental update is possible to guarantee eventual
     * consistency.
     *
     * @return the force update probability.
     */
    double getForceUpdateProbability();

    /**
     * Get whether force-update-after-start is enabled.
     *
     * @return whether a force update is executed after thing updater starts.
     */
    boolean isForceUpdateAfterStartEnabled();

    /**
     * Get the timeout after when to explicitly do a "force update" after a ThingUpdater was started.
     * This is needed in order to guarantee that during a rolling update (when shard regions are moved to another
     * cluster node) the thing will eventually be consistent again (within the configured timeout), even if pending
     * search updates were still processed on the "old" things-search cluster node where the ThingUpdater previously
     * ran.
     *
     * @return the timeout after when to explicitly do a "force update" after a ThingUpdater was started.
     */
    Duration getForceUpdateAfterStartTimeout();

    /**
     * Get the factor of the random delay added to "force-update-after-start".
     * A random delay is introduced so that the active things will not perform force update at the same time and
     * generate a load spike at the database.
     *
     * @return the factor of the random delay.
     */
    double getForceUpdateAfterStartRandomFactor();

    /**
     * Returns configuration for the background sync actor.
     *
     * @return the config.
     */
    BackgroundSyncConfig getBackgroundSyncConfig();

    /**
     * Returns the configuration settings
     *
     * @return the config.
     */
    StreamConfig getStreamConfig();

    /**
     * Returns the updater persistence config.
     *
     * @return the config.
     */
    UpdaterPersistenceConfig getUpdaterPersistenceConfig();

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
         * How often sharding state is polled. The intervening events are lost after shard rebalancing
         * and require background sync to be accounted for.
         */
        SHARDING_STATE_POLL_INTERVAL("sharding-state-poll-interval", Duration.ofSeconds(10L)),

        /**
         * Determines whether event processing should be active.
         */
        EVENT_PROCESSING_ACTIVE("event-processing-active", true),

        /**
         * Probability to do a replacement update regardless whether incremental update is possible.
         */
        FORCE_UPDATE_PROBABILITY("force-update-probability", 0.00),

        /**
         * Whether force-update-after-start is enabled.
         */
        FORCE_UPDATE_AFTER_START_ENABLED("force-update-after-start-enabled", true),

        /**
         * The timeout after when to explicitly do a "force update" after a ThingUpdater was started.
         */
        FORCE_UPDATE_AFTER_START_TIMEOUT("force-update-after-start-timeout", Duration.ofMinutes(5)),

        /**
         * Random factor added to "force-update-after-start-timeout" to avoid database load spikes.
         */
        FORCE_UPDATE_AFTER_START_RANDOM_FACTOR("force-update-after-start-random-factor", 1.0);

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
