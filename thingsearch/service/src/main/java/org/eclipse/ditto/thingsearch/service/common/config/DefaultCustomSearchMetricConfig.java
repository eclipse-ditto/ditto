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

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;

public final class DefaultCustomSearchMetricConfig implements CustomSearchMetricConfig {

    private final String customMetricName;
    private final boolean enabled;
    private final Duration scrapeInterval;
    private final List<String> namespaces;
    private final Map<String, String> tags;
    private final List<FilterConfig> filterConfigs;

    private DefaultCustomSearchMetricConfig(final String key, final ConfigWithFallback configWithFallback) {
        this.customMetricName = key;
        enabled = configWithFallback.getBoolean(CustomSearchMetricConfigValue.ENABLED.getConfigPath());
        scrapeInterval = configWithFallback.getDuration(CustomSearchMetricConfigValue.SCRAPE_INTERVAL.getConfigPath());
        namespaces = configWithFallback.getStringList(CustomSearchMetricConfigValue.NAMESPACES.getConfigPath());
        tags = configWithFallback.getObject(CustomSearchMetricConfig.CustomSearchMetricConfigValue.TAGS.getConfigPath()).unwrapped()
                .entrySet()
                .stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> String.valueOf(e.getValue())));
        filterConfigs = configWithFallback.getObject(CustomSearchMetricConfigValue.FILTERS.getConfigPath()).entrySet().stream()
                .map(entry -> DefaultFilterConfig.of(entry.getKey(), ConfigFactory.empty().withFallback(entry.getValue())))
                .collect(Collectors.toList());
    }

    @Override
    public String getCustomMetricName() {
        return customMetricName;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public Optional<Duration> getScrapeInterval() {
        return scrapeInterval.isZero() ? Optional.empty() : Optional.of(scrapeInterval);
    }

    @Override
    public List<String> getNamespaces() {
        return namespaces;
    }

    @Override
    public Map<String, String> getTags() {
        return tags;
    }

    @Override
    public List<FilterConfig> getFilterConfigs() {
        return filterConfigs;
    }

    public static DefaultCustomSearchMetricConfig of(final String key, final Config config){
        return new DefaultCustomSearchMetricConfig(key , ConfigWithFallback.newInstance(config, CustomSearchMetricConfigValue.values()));
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultCustomSearchMetricConfig that = (DefaultCustomSearchMetricConfig) o;
        return enabled == that.enabled &&
                Objects.equals(scrapeInterval, that.scrapeInterval) &&
                Objects.equals(namespaces, that.namespaces) &&
                Objects.equals(tags, that.tags) &&
                Objects.equals(filterConfigs, that.filterConfigs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, scrapeInterval, namespaces, tags, filterConfigs);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "enabled=" + enabled +
                ", scrapeInterval=" + scrapeInterval +
                ", namespaces=" + namespaces +
                ", tags=" + tags +
                ", filterConfig=" + filterConfigs +
                "]";
    }

    public static final class DefaultFilterConfig implements FilterConfig {

        private final String filterName;
        private final String filter;
        private final List<String> fields;
        private final Map<String, String> inlinePlaceholderValues;

        private DefaultFilterConfig(final String name, final ConfigWithFallback configWithFallback) {
            this.filterName = name;
            this.filter = configWithFallback.getString(FilterConfigValues.FILTER.getConfigPath());
            this.fields = configWithFallback.getStringList(FilterConfigValues.FIELDS.getConfigPath());
            this.inlinePlaceholderValues = configWithFallback.getObject(FilterConfigValues.INLINE_PLACEHOLDER_VALUES.getConfigPath()).unwrapped()
                    .entrySet()
                    .stream()
                    .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> String.valueOf(e.getValue())));
        }

        @Override
        public String getFilterName() {
            return filterName;
        }

        @Override
        public String getFilter() {
            return filter;
        }

        @Override
        public List<String> getFields() {
            return fields;
        }

        @Override
        public Map<String, String> getInlinePlaceholderValues() {
            return inlinePlaceholderValues;
        }

        public static FilterConfig of(final String name, final Config config) {
            return new DefaultFilterConfig(
                    name, ConfigWithFallback.newInstance(config, CustomSearchMetricConfigValue.values()));
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            final DefaultFilterConfig that = (DefaultFilterConfig) o;
            return Objects.equals(filterName, that.filterName) && Objects.equals(filter, that.filter) &&
                    Objects.equals(fields, that.fields) &&
                    Objects.equals(inlinePlaceholderValues, that.inlinePlaceholderValues);
        }

        @Override
        public int hashCode() {
            return Objects.hash(filterName, filter, fields, inlinePlaceholderValues);
        }

        @Override
        public String toString() {
            return  getClass().getSimpleName() + " [" +
                    "filterName='" + filterName + '\'' +
                    ", filter='" + filter + '\'' +
                    ", fields=" + fields +
                    ", inlinePlaceholderValues=" + inlinePlaceholderValues +
                    ']';
        }
    }
}
