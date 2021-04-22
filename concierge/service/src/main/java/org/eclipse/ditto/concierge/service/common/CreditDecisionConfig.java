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
package org.eclipse.ditto.concierge.service.common;

import java.time.Duration;

import org.eclipse.ditto.services.utils.config.KnownConfigValue;

/**
 * Provides configuration settings for credit decisions of persistence cleanup actions.
 */
public interface CreditDecisionConfig {

    /**
     * Returns how often credit decision is made.
     *
     * @return delay between successive credit decisions.
     */
    Duration getInterval();

    /**
     * Returns maximum wait time for metric reports.
     *
     * @return how long to wait for metric reports before failing.
     */
    Duration getMetricReportTimeout();

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
     * Returns the amount of credit given out on request to persistence actors per interval.
     *
     * @return the amount of credit for requests.
     */
    int getCreditForRequests();

    /**
     * Returns the amount of requests to keep in a cache until credit is available.
     *
     * @return the maximum amount of requests.
     */
    int getMaxPendingRequests();

    /**
     * Enumeration of known config keys and default values for {@code CreditDecisionConfig}
     */
    enum ConfigValue implements KnownConfigValue {

        /**
         * Credit decision interval.
         */
        INTERVAL("interval", Duration.ofMinutes(1L)),

        /**
         * Metric report timeout.
         */
        METRIC_REPORT_TIMEOUT("metric-report-timeout", Duration.ofSeconds(10L)),

        /**
         * Database latency threshold to give out any credit.
         */
        TIMER_THRESHOLD("timer-threshold", Duration.ofMillis(20L)),

        /**
         * Amount of credit to give out per decision.
         */
        CREDIT_PER_BATCH("credit-per-batch", 5),

        /**
         * How many background cleanup actions can be requested by persistence actors per interval.
         */
        CREDIT_FOR_REQUESTS("credit-for-requests", 10),

        /**
         * Number of thing IDs to keep in the pending cleanup cache waiting for credits.
         */
        MAX_PENDING_REQUESTS("max-pending-requests", 100_000);

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
