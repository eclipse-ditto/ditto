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
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.ditto.internal.utils.config.KnownConfigValue;
import org.eclipse.ditto.json.JsonValue;

/**
 * Provides the configuration settings for a single custom operator metric.
 */
public interface CustomMetricConfig {

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
     * Returns the filter (RQL statement) to include in the "CountThings" request or an empty string of no filter
     * should be applied.
     *
     * @return the filter RQL statement.
     */
    String getFilter();

    /**
     * Return optional tags to report to the custom Gauge metric.
     *
     * @return optional tags to report.
     */
    Map<String, String> getTags();

    /**
     * Returns the optional index hint to use for the MongoDB query of this custom metric.
     * The hint can be either a string (index name) or a JSON object (index key specification).
     *
     * @return the optional index hint.
     */
    Optional<JsonValue> getIndexHint();

    enum CustomMetricConfigValue implements KnownConfigValue {

        /**
         * Whether the metrics should be gathered.
         */
        ENABLED("enabled", true),

        /**
         * The optional custom scrape interval, how often the metrics should be gathered.
         * If this is {@code Duration.ZERO}, then there is no overwrite for the "global" scrape-interval to be applied.
         */
        SCRAPE_INTERVAL("scrape-interval", Duration.ZERO),

        /**
         * The namespaces the custom metric should be executed in or an empty list for gathering metrics in all
         * namespaces.
         */
        NAMESPACES("namespaces", List.of()),

        /**
         * The filter RQL statement.
         */
        FILTER("filter", null),

        /**
         * The optional tags to report to the custom Gauge metric.
         */
        TAGS("tags", Map.of()),

        /**
         * The optional index hint for the MongoDB query (string index name or object index key spec).
         */
        INDEX_HINT("index-hint", null);

        private final String path;
        private final Object defaultValue;

        CustomMetricConfigValue(final String thePath, final Object theDefaultValue) {
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
