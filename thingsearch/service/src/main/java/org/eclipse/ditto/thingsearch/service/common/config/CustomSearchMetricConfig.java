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

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Provides the configuration settings for a single custom search metric.
 */
public interface CustomSearchMetricConfig {


    /**
     * Returns the name of the custom metric.
     *
     * @return the name of the custom metric.
     */
    String getCustomMetricName();

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
     * Return optional tags to report to the custom Gauge metric.
     *
     * @return optional tags to report.
     */
    Map<String, String> getTags();

    /**
     * Returns the filter configurations for this custom metric.
     *
     * @return the filter configurations.
     */
    List<FilterConfig> getFilterConfigs();

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
         * The optional tags to report to the custom Gauge metric.
         */
        TAGS("tags", Map.of()),

        FILTERS("filters", List.of());

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

    interface FilterConfig {

        String getFilterName();

        String getFilter();

        List<String> getFields();

        Map<String, String> getInlinePlaceholderValues();

        enum FilterConfigValues implements KnownConfigValue {
            FILTER("filter", ""),
            FIELDS("fields", Collections.emptyList()),
            INLINE_PLACEHOLDER_VALUES("inline-placeholder-values", Map.of());

            private final String path;
            private final Object defaultValue;

            FilterConfigValues(final String thePath, final Object theDefaultValue) {
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
}
