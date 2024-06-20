/*
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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
import java.util.Collections;
import java.util.Map;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

/**
 * Provides the configuration settings for the search operator metrics.
 */
@Immutable
public interface OperatorMetricsConfig {

    /**
     * Returns whether search operator metrics gathering is turned on.
     *
     * @return true or false.
     */
    boolean isEnabled();

    /**
     * Returns the default scrape interval, how often the metrics should be gathered.
     *
     * @return the default scrape interval.
     */
    Duration getScrapeInterval();

    /**
     * Returns all registered custom metrics with the key being the metric name to use.
     *
     * @return the registered custom metrics.
     */
    Map<String, CustomMetricConfig> getCustomMetricConfigurations();

    /**
     * Returns all registered custom search metrics with the key being the metric name to use.
     *
     * @return the registered custom search metrics.
     */
    Map<String, CustomSearchMetricConfig> getCustomSearchMetricConfigurations();

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * OperatorMetricsConfig.
     */
    enum OperatorMetricsConfigValue implements KnownConfigValue {

        /**
         * Whether the metrics should be gathered.
         */
        ENABLED("enabled", true),

        /**
         * The default scrape interval, how often the metrics should be gathered.
         */
        SCRAPE_INTERVAL("scrape-interval", Duration.ofMinutes(15)),

        /**
         * All registered custom metrics with the key being the metric name to use.
         */
        CUSTOM_METRICS("custom-metrics", Collections.emptyMap()),

        /**
         * All registered custom search metrics with the key being the metric name to use.
         */
        CUSTOM_SEARCH_METRICS("custom-search-metrics", Collections.emptyMap());

        private final String path;
        private final Object defaultValue;

        OperatorMetricsConfigValue(final String thePath, final Object theDefaultValue) {
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
