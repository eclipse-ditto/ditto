/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

import org.eclipse.ditto.internal.utils.config.KnownConfigValue;
import org.eclipse.ditto.internal.utils.health.config.BackgroundStreamingConfig;

import com.typesafe.config.Config;

/**
 * Configuration of the background sync actor.
 */
public interface BackgroundSyncConfig extends BackgroundStreamingConfig {

    @Override
    Config getConfig();

    @Override
    boolean isEnabled();

    /**
     * Return the quiet period, which is the delay before starting the background sync stream after start-up
     * and also the delay between flushing current progress into the bookmark persistence.
     *
     * @return the quiet period.
     */
    @Override
    Duration getQuietPeriod();

    @Override
    int getKeptEvents();

    /**
     * Get how recent an out-of-date search index entry may be without triggering reindexing.
     * Should be much larger than the normal delay between thing change and full replication in the search index.
     *
     * @return the tolerance window.
     */
    Duration getToleranceWindow();

    /**
     * Get how many things to update per throttle period.
     *
     * @return the number of things to update per  throttle period.
     */
    int getThrottleThroughput();

    /**
     * Get the throttle period.
     *
     * @return the throttle period.
     */
    Duration getThrottlePeriod();

    /**
     * How long to wait before failing the background sync stream when no element passed through for a while.
     * The stream stalls when other services are slow.
     */
    Duration getIdleTimeout();

    /**
     * How long to wait for the policy shard region for the most up-to-date policy revision.
     *
     * @return ask timeout for the policy shard region.
     */
    Duration getPolicyAskTimeout();

    /**
     * Minimum backoff on stream failure.
     *
     * @return the minimum back-off.
     */
    Duration getMinBackoff();

    /**
     * Maximum backoff on stream failure.
     *
     * @return the maximum back-off.
     */
    Duration getMaxBackoff();

    /**
     * Maximum restarts after repeated upstream failure before giving up.
     *
     * @return the maximum restarts.
     */
    int getMaxRestarts();

    /**
     * How long the stream must run error-free before resetting back-off to the minimum.
     *
     * @return the recovery period.
     */
    Duration getRecovery();

    /**
     * Enumeration of known config keys and default values for {@code PersistenceCleanupConfig}
     */
    enum ConfigValue implements KnownConfigValue {

        /**
         * Whether background sync is turned on.
         */
        ENABLED("enabled", true),

        /**
         * Duration between service start-up and the beginning of background sync.
         */
        QUIET_PERIOD("quiet-period", Duration.ofMinutes(8)),

        /**
         * How many events to keep in the actor state.
         */
        KEEP_EVENTS("keep.events", 25),

        /**
         * How long to wait before reacting to out-of-date search index entries.
         */
        TOLERANCE_WINDOW("tolerance-window", Duration.ofMinutes(5)),

        /**
         * Maximum number of PIDs to check per throttle period, and the maximum number of snapshots
         * to read in one batch.
         */
        THROTTLE_THROUGHPUT("throttle.throughput", 100),

        /**
         * The throttle period.
         */
        THROTTLE_PERIOD("throttle.period", Duration.ofSeconds(10L)),

        /**
         * How soon to close the remote stream if no element passed through it.
         */
        IDLE_TIMEOUT("idle-timeout", Duration.ofMinutes(2L)),

        /**
         * Timeout waiting for cleanup response.
         */
        POLICY_ASK_TIMEOUT("policy-ask-timeout", Duration.ofSeconds(10L)),

        /**
         * Minimum backoff in case of stream failure.
         */
        MIN_BACKOFF("min-backoff", Duration.ofSeconds(1L)),

        /**
         * Maximum backoff in case of stream failure.
         */
        MAX_BACKOFF("max-backoff", Duration.ofMinutes(2L)),

        /**
         * Maximum number of stream resumptions before giving up.
         */
        MAX_RESTARTS("max-restarts", 180),

        /**
         * Assume upstream healthy if no error happened for this long.
         */
        RECOVERY("recovery", Duration.ofMinutes(4L));

        private final String path;
        private final Object defaultValue;

        ConfigValue(final String thePath, final Object theDefaultValue) {
            path = thePath;
            defaultValue = theDefaultValue;
        }

        @Override
        public String getConfigPath() {
            return path;
        }

        @Override
        public Object getDefaultValue() {
            return defaultValue;
        }
    }
}
