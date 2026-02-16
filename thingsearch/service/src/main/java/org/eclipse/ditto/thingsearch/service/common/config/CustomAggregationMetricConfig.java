/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
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

import org.eclipse.ditto.internal.utils.config.KnownConfigValue;
import org.eclipse.ditto.json.JsonValue;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Provides the configuration settings for a single custom search metric.
 */
public interface CustomAggregationMetricConfig {


    /**
     * Returns the name of the custom metric.
     *
     * @return the name of the custom metric.
     */
    String getMetricName();

    /**
     * Returns whether this specific search operator metric gathering is turned on.
     *
     * @return true or false.
     */
    boolean isEnabled();

    /**
     * Returns the optional scrape interval override for this specific custom metric, how often the metrics should be
     * gathered.
     *
     * @return the optional scrape interval override.
     */
    Optional<Duration> getScrapeInterval();

    /**
     * Returns the namespaces the custom metric should be executed in or an empty list for gathering metrics in all
     * namespaces.
     *
     * @return a list of namespaces.
     */
    List<String> getNamespaces();

    /**
     * Returns the fields we want our metric aggregation to be grouped by.
     * Field name and thing json pointer
     *
     * @return the fields we want our metric aggregation to be grouped by.
     */
    Map<String, String> getGroupBy();

    /**
     * Return optional tags to report to the custom Gauge metric.
     *
     * @return optional tags to report.
     */
    Map<String, String> getTags();

    /**
     * Returns the filter for this custom metric.
     *
     * @return the filter or empty optional if not configured.
     */
    Optional<String> getFilter();

    /**
     * Returns the optional index hint to use for the MongoDB aggregation of this custom metric.
     * The hint can be either a string (index name) or a JSON object (index key specification).
     *
     * @return the optional index hint.
     */
    Optional<JsonValue> getIndexHint();

    enum CustomSearchMetricConfigValue implements KnownConfigValue {
        /**
         * Whether the metrics should be gathered.
         */
        ENABLED("enabled", true),

        /**
         * The optional custom scrape interval, how often the metrics should be gathered.
         * If this is {@code Duration.ZERO}, then there no overwriting for the "global" scrape-interval to be applied.
         */
        SCRAPE_INTERVAL("scrape-interval", Duration.ZERO),

        /**
         * The namespaces the custom metric should be executed in or an empty list for gathering metrics in all
         * namespaces.
         */
        NAMESPACES("namespaces", List.of()),

        /**
         * The fields we want our metric aggregation to be grouped by.
         */
        GROUP_BY("group-by", Map.of()),

        /**
         * The optional tags to report to the custom Gauge metric.
         */
        TAGS("tags", Map.of()),

        /**
         * The filter for this custom metric.
         */
        FILTER("filter", ""),

        /**
         * The optional index hint for the MongoDB aggregation (string index name or object index key spec).
         */
        INDEX_HINT("index-hint", null);

        private final String path;
        private final Object defaultValue;

        CustomSearchMetricConfigValue(final String thePath, final Object theDefaultValue) {
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
