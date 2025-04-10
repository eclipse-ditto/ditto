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

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.thingsearch.service.placeholders.GroupByPlaceholderResolver;

import com.typesafe.config.Config;

@Immutable
public final class DefaultCustomAggregationMetricConfig implements CustomAggregationMetricConfig {

    private final String metricName;
    private final boolean enabled;
    private final Duration scrapeInterval;
    private final List<String> namespaces;
    private final Map<String, String> groupBy;
    private final Map<String, String> tags;
    @Nullable
    private final String filter;

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
        filter = configWithFallback.getStringOrNull(CustomMetricConfig.CustomMetricConfigValue.FILTER);
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
    public Optional<String> getFilter() {
        return (filter == null || filter.isEmpty()) ? Optional.empty() : Optional.of(filter);
    }


    private void validateConfig() {
        if (getGroupBy().isEmpty()) {
            throw new IllegalArgumentException("Custom search metric Gauge for metric <" + metricName
                    + "> must have at least one groupBy tag configured or else disable.");
        }
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

        final Set<String> requiredGroupByPlaceholders = getDeclaredGroupByPlaceholdersExpressions(getTags());
        List<String> missing = new ArrayList<>();
        requiredGroupByPlaceholders.forEach(placeholder -> {
            if (!getGroupBy().containsKey(placeholder)) {
                missing.add(placeholder);
            }
        });
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Custom search metric Gauge for metric <" + metricName
                    + "> must contain in the groupBy fields all of the fields used by placeholder expressions in tags. Missing: "
                    + missing + " Configured: " + getGroupBy().keySet());
        }
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
                Objects.equals(tags, that.tags) && Objects.equals(filter, that.filter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(metricName, enabled, scrapeInterval, namespaces, groupBy, tags, filter);
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
                ", filter=" + filter +
                '}';
    }
}
