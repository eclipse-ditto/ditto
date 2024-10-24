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

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.thingsearch.service.placeholders.GroupByPlaceholderResolver;
import org.eclipse.ditto.thingsearch.service.placeholders.InlinePlaceholderResolver;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

@Immutable
public final class DefaultCustomAggregationMetricConfig implements CustomAggregationMetricConfig {

    private final String metricName;
    private final boolean enabled;
    private final Duration scrapeInterval;
    private final List<String> namespaces;
    private final Map<String, String> groupBy;
    private final Map<String, String> tags;
    private final List<FilterConfig> filterConfigs;

    private DefaultCustomAggregationMetricConfig(final String key, final ConfigWithFallback configWithFallback) {
        this.metricName = key;
        enabled = configWithFallback.getBoolean(CustomSearchMetricConfigValue.ENABLED.getConfigPath());
        scrapeInterval = configWithFallback.getDuration(CustomSearchMetricConfigValue.SCRAPE_INTERVAL.getConfigPath());
        namespaces = Collections.unmodifiableList(new ArrayList<>(
                configWithFallback.getStringList(CustomSearchMetricConfigValue.NAMESPACES.getConfigPath())));
        groupBy = Collections.unmodifiableMap(new LinkedHashMap<>(
                configWithFallback.getObject(CustomSearchMetricConfigValue.GROUP_BY.getConfigPath()).unwrapped()
                        .entrySet()
                        .stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, e -> String.valueOf(e.getValue()),
                                (u, v) -> {
                                    throw new IllegalStateException(String.format("Duplicate key %s", u));
                                },
                                LinkedHashMap::new))
        ));
        tags = Collections.unmodifiableMap(new LinkedHashMap<>(
                configWithFallback.getObject(CustomSearchMetricConfigValue.TAGS.getConfigPath()).unwrapped()
                        .entrySet()
                        .stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, e -> String.valueOf(e.getValue()),
                                (u, v) -> {
                                    throw new IllegalStateException(String.format("Duplicate key %s", u));
                                },
                                LinkedHashMap::new))
        ));
        filterConfigs =
                Collections.unmodifiableList(new ArrayList<>(
                        configWithFallback.getObject(CustomSearchMetricConfigValue.FILTERS.getConfigPath())
                                .entrySet()
                                .stream()
                                .map(entry -> DefaultFilterConfig.of(entry.getKey(),
                                        ConfigFactory.empty().withFallback(entry.getValue())))
                                .toList()));
        validateConfig();
    }

    public static DefaultCustomAggregationMetricConfig of(final String key, final Config config) {
        return new DefaultCustomAggregationMetricConfig(key,
                ConfigWithFallback.newInstance(config, CustomSearchMetricConfigValue.values()));
    }

    @Override
    public String getMetricName() {
        return metricName;
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
    public Map<String, String> getGroupBy() {
        return groupBy;
    }

    @Override
    public Map<String, String> getTags() {
        return tags;
    }

    @Override
    public List<FilterConfig> getFilterConfigs() {
        return filterConfigs;
    }


    private void validateConfig() {
            if (getGroupBy().isEmpty()) {
                throw new IllegalArgumentException("Custom search metric Gauge for metric <" + metricName
                        + "> must have at least one groupBy tag configured or else disable.");
            }
            getFilterConfigs().forEach(filterConfig -> {
                if (filterConfig.getFilter().isEmpty()) {
                    throw new IllegalArgumentException("Custom search metric Gauge for metric <" + metricName
                            + "> must have at least one filter configured or else disable.");
                }
                if (filterConfig.getFilterName().contains("-")) {
                    throw new IllegalArgumentException("Custom search metric Gauge for metric <" + metricName
                            + "> filter name <" + filterConfig.getFilterName()
                            + "> must not contain the character '-'. Not supported in Mongo aggregations.");
                }
            });
            getTags().values().stream()
                    .filter(this::isPlaceHolder)
                    .map(value -> value.substring(2, value.length() - 2).trim())
                    .forEach(placeholder -> {
                        if (!placeholder.contains("inline:") && !placeholder.contains("group-by:")) {
                            throw new IllegalArgumentException("Custom search metric Gauge for metric <" + metricName
                                    + "> tag placeholder <" + placeholder
                                    + "> is not supported. Supported placeholder types are 'inline' and 'group-by'.");
                        }
                    });

            final Set<String> requiredInlinePlaceholders = getDeclaredInlinePlaceholderExpressions(getTags());
            getFilterConfigs().forEach(filterConfig -> {
                final Set<String> definedInlinePlaceholderValues = filterConfig.getInlinePlaceholderValues().keySet();
                if (!requiredInlinePlaceholders.equals(definedInlinePlaceholderValues)) {
                    throw new IllegalArgumentException("Custom search metric Gauge for metric <" + metricName
                            + "> filter <" + filterConfig.getFilterName()
                            + "> must have the same inline-placeholder-values keys as the configured placeholders in tags.");
                }
            });

            final Set<String> requiredGroupByPlaceholders = getDeclaredGroupByPlaceholdersExpressions(getTags());
            if (!requiredGroupByPlaceholders.equals(getGroupBy().keySet())) {
                throw new IllegalArgumentException("Custom search metric Gauge for metric <"
                + metricName + "> must have the same groupBy fields as the configured placeholder expressions in tags. Required: " + requiredGroupByPlaceholders + " Configured: " + getGroupBy().keySet());
            }
    }

    private Set<String> getDeclaredInlinePlaceholderExpressions(final Map<String, String> tags) {
        return tags.values().stream()
                .filter(this::isPlaceHolder)
                .map(value -> value.substring(2, value.length() - 2).trim())
                .filter(value -> value.startsWith(InlinePlaceholderResolver.PREFIX + ":"))
                .map(value -> value.substring((InlinePlaceholderResolver.PREFIX + ":").length()))
                .collect(Collectors.toSet());
    }

    private Set<String> getDeclaredGroupByPlaceholdersExpressions(final Map<String, String> tags) {
        return tags.values().stream()
                .filter(this::isPlaceHolder)
                .map(value -> value.substring(2, value.length() - 2).trim())
                .filter(value -> value.startsWith(GroupByPlaceholderResolver.PREFIX + ":"))
                .map(value -> value.substring((GroupByPlaceholderResolver.PREFIX + ":").length()))
                .map(value -> Arrays.stream(value.split("\\|")).findFirst().map(String::trim).orElse(""))
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toSet());
    }

    private boolean isPlaceHolder(final String value) {
        return value.startsWith("{{") && value.endsWith("}}");
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final DefaultCustomAggregationMetricConfig that = (DefaultCustomAggregationMetricConfig) o;
        return enabled == that.enabled && Objects.equals(metricName, that.metricName) &&
                Objects.equals(scrapeInterval, that.scrapeInterval) &&
                Objects.equals(namespaces, that.namespaces) && Objects.equals(groupBy, that.groupBy) &&
                Objects.equals(tags, that.tags) && Objects.equals(filterConfigs, that.filterConfigs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(metricName, enabled, scrapeInterval, namespaces, groupBy, tags, filterConfigs);
    }

    @Override
    public String toString() {
        return "DefaultCustomSearchMetricConfig{" +
                "metricName='" + metricName + '\'' +
                ", enabled=" + enabled +
                ", scrapeInterval=" + scrapeInterval +
                ", namespaces=" + namespaces +
                ", groupBy=" + groupBy +
                ", tags=" + tags +
                ", filterConfigs=" + filterConfigs +
                '}';
    }

    @Immutable
    public static final class DefaultFilterConfig implements FilterConfig {

        private final String filterName;
        private final String filter;
        private final Map<String, String> inlinePlaceholderValues;

        private DefaultFilterConfig(final String name, final ConfigWithFallback configWithFallback) {
            this(name, configWithFallback.getString(FilterConfigValues.FILTER.getConfigPath()),
                    configWithFallback.getObject(FilterConfigValues.INLINE_PLACEHOLDER_VALUES.getConfigPath())
                            .unwrapped()
                            .entrySet()
                            .stream()
                            .collect(Collectors.toMap(Map.Entry::getKey, e -> String.valueOf(e.getValue()),
                                    (u, v) -> {
                                        throw new IllegalStateException(String.format("Duplicate key %s", u));
                                    },
                                    LinkedHashMap::new))
            );
        }

        private DefaultFilterConfig(final String filterName, final String filter, final Map<String, String> inlinePlaceholderValues) {
            this.filterName = filterName;
            this.filter = filter;
            this.inlinePlaceholderValues = Collections.unmodifiableMap(new LinkedHashMap<>(inlinePlaceholderValues));
        }

        public static FilterConfig of(final String name, final Config config) {
            return new DefaultFilterConfig(
                    name, ConfigWithFallback.newInstance(config, CustomSearchMetricConfigValue.values()));
        }

        public static FilterConfig of(final FilterConfig filterConfig) {
            return new DefaultFilterConfig(filterConfig.getFilterName(), filterConfig.getFilter(),
                    filterConfig.getInlinePlaceholderValues());
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
        public Map<String, String> getInlinePlaceholderValues() {
            return inlinePlaceholderValues;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            final DefaultFilterConfig that = (DefaultFilterConfig) o;
            return Objects.equals(filterName, that.filterName) && Objects.equals(filter, that.filter) &&
                    Objects.equals(inlinePlaceholderValues, that.inlinePlaceholderValues);
        }

        @Override
        public int hashCode() {
            return Objects.hash(filterName, filter, inlinePlaceholderValues);
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + " [" +
                    "filterName=" + filterName +
                    ", filter=" + filter +
                    ", inlinePlaceholderValues=" + inlinePlaceholderValues +
                    ']';
        }
    }
}
