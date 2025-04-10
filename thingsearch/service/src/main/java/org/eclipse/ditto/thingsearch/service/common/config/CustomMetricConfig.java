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
        TAGS("tags", Map.of());

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
