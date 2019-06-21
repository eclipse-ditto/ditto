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

import com.typesafe.config.Config;

/**
 * Provides configuration settings for persistence cleanup actions.
 */
public interface PersistenceCleanupConfig {

    /**
     * Returns how long to wait before scheduling persistence cleanup actions.
     *
     * @return duration of the quiet period.
     */
    Duration getQuietPeriod();

    /**
     * Returns how long to wait for a cleanup-response before considering it failed.
     *
     * @return timeout of cleanup commands.
     */
    Duration getCleanupTimeout();

    /**
     * Returns how many cleanup commands to execute in parallel.
     *
     * @return the parallelism.
     */
    int getParallelism();

    /**
     * Returns configuration settings for credit decision.
     *
     * @return the config.
     */
    CreditDecisionConfig getCreditDecisionConfig();

    /**
     * Returns configuration settings for the persistence ID stream.
     *
     * @return the config.
     */
    PersistenceIdsConfig getPersistenceIdsConfig();

    /**
     * Returns how many credit decisions to keep in the actor state.
     *
     * @return number of kept credit decisions.
     */
    int getKeptCreditDecisions();

    /**
     * Returns how many actions to keep in the actor state.
     *
     * @return number of kept actions.
     */
    int getKeptActions();

    /**
     * Returns how many events to keep in the actor state.
     *
     * @return number of kept events.
     */
    int getKeptEvents();

    /**
     * Return the config in HOCON format.
     *
     * @return the HOCON.
     */
    Config getConfig();

    /**
     * Create a persistence cleanup config from HOCON config.
     *
     * @param config the HOCON.
     * @return the corresponding persistence cleanup config.
     */
    static PersistenceCleanupConfig of(final Config config) {
        return DefaultPersistenceCleanupConfig.of(config);
    }

    /**
     * Enumeration of known config keys and default values for {@code PersistenceCleanupConfig}
     */
    enum ConfigValue implements KnownConfigValue {

        /**
         * Duration between service start-up and the beginning of cleanup actions.
         */
        QUIET_PERIOD("quiet-period", Duration.ofMinutes(5L)),

        /**
         * Timeout waiting for cleanup response.
         */
        CLEANUP_TIMEOUT("cleanup-timeout", Duration.ofSeconds(30L)),

        /**
         * Number of clewanup commands to execute in parallel.
         */
        PARALLELISM("parallelism", 1),

        /**
         * How many credit decisions to keep in the actor state.
         */
        KEEP_CREDIT_DECISIONS("keep.credit-decisions", 25),

        /**
         * How many actions to keep in the actor state.
         */
        KEEP_ACTIONS("keep.actions", 100),

        /**
         * How many events to keep in the actor state.
         */
        KEEP_EVENTS("keep.events", 25);

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
