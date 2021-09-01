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
package org.eclipse.ditto.internal.utils.persistentactors.cleanup;

import java.time.Duration;

import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

/**
 * TODO: remove duplication.
 */
interface CreditDecisionConfig {

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
    int getCreditPerBatch();

    /**
     * Enumeration of known config keys and default values for {@code CreditDecisionConfig}
     */
    enum ConfigValue implements KnownConfigValue {

        /**
         * Credit decision interval.
         */
        INTERVAL("interval", Duration.ofMinutes(1L)),

        /**
         * Database latency threshold to give out any credit.
         */
        TIMER_THRESHOLD("timer-threshold", Duration.ofMillis(20L)),

        /**
         * Amount of credit to give out per decision.
         */
        CREDIT_PER_BATCH("credit-per-batch", 5);

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
