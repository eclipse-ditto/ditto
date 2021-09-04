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
package org.eclipse.ditto.internal.utils.persistentactors.cleanup;

import java.time.Duration;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

import com.typesafe.config.Config;

/**
 * Config for persistence cleanup.
 */
public interface CleanUpConfig {

    /**
     * Create an instance of clean-up config from HOCON.
     *
     * @param config the HOCON object.
     * @return the clean-up config.
     */
    static CleanUpConfig of(final Config config) {
        return new DefaultCleanUpConfig(ConfigWithFallback.newInstance(config, DefaultCleanUpConfig.CONFIG_PATH,
                ConfigValue.values()));
    }

    /**
     * Returns quiet period between cleanup streams.
     *
     * @return the quiet period.
     */
    Duration getQuietPeriod();

    /**
     * Returns how often credit decision is made.
     *
     * @return delay between successive credit decisions.
     */
    Duration getInterval();

    /**
     * Returns maximum database latency to give out credit for cleanup actions.
     *
     * @return database latency threshold.
     */
    Duration getTimerThreshold();

    /**
     * Returns the amount of credit given out by 1 credit decision.
     * It limits the rate of cleanup actions to this many per credit decision interval.
     *
     * @return the amount of credit per decision.
     */
    int getCreditsPerBatch();

    /**
     * Returns the number of snapshots to scan per MongoDB query.
     *
     * @return the number of snapshots to scan per query.
     */
    int getReadsPerQuery();

    /**
     * Returns the number of documents to delete for each credit.
     *
     * @return the number of documents to delete.
     */
    int getWritesPerCredit();

    /**
     * Whether the final deleted snapshot should be deleted.
     * If true, the monotonicity constraint on the sequence numbers of a PID will be broken.
     *
     * @return whether to delete the final deleted snapshot..
     */
    boolean shouldDeleteFinalDeletedSnapshot();

    /**
     * Enumeration of known config keys and default values for {@code CreditDecisionConfig}
     */
    enum ConfigValue implements KnownConfigValue {

        /**
         * Quiet period.
         */
        QUIET_PERIOD("quiet-period", Duration.ofMinutes(5L)),

        /**
         * Credit decision interval.
         */
        INTERVAL("interval", Duration.ofSeconds(3L)),

        /**
         * Database latency threshold to give out any credit.
         */
        TIMER_THRESHOLD("timer-threshold", Duration.ofMillis(100L)),

        /**
         * Amount of credit to give out per decision.
         */
        CREDITS_PER_BATCH("credits-per-batch", 3),

        /**
         * How many snapshots to scan for each query.
         */
        READS_PER_QUERY("reads-per-query", 100),

        /**
         * How many delete operations to authorize for each credit.
         */
        WRITES_PER_CREDIT("writes-per-credit", 100),

        /**
         * Whether to delete the final deleted snapshot.
         */
        DELETE_FINAL_DELETED_SNAPSHOT("delete-final-deleted-snapshot", false);

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
