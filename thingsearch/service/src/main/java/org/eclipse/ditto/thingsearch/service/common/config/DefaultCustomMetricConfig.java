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
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of the CustomMetricConfig.
 * It is instantiated for each {@code custom-metrics} entry containing the configuration for the custom metric.
 */
public final class DefaultCustomMetricConfig implements CustomMetricConfig {

    private final String customMetricName;
    private final boolean enabled;
    private final Duration scrapeInterval;
    private final List<String> namespaces;
    private final String filter;
    private final Map<String, String> tags;

    private DefaultCustomMetricConfig(final String customMetricName, final ConfigWithFallback configWithFallback) {
        this.customMetricName = customMetricName;
        enabled = configWithFallback.getBoolean(CustomMetricConfigValue.ENABLED.getConfigPath());
        scrapeInterval = configWithFallback.getDuration(CustomMetricConfigValue.SCRAPE_INTERVAL.getConfigPath());
        namespaces = configWithFallback.getStringList(CustomMetricConfigValue.NAMESPACES.getConfigPath());
        filter = configWithFallback.getString(CustomMetricConfigValue.FILTER.getConfigPath());
        tags = configWithFallback.getObject(CustomMetricConfigValue.TAGS.getConfigPath()).unwrapped()
                .entrySet()
                .stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> String.valueOf(e.getValue())));
    }

    /**
     * Returns an instance of {@code DefaultCustomMetricConfig} based on the settings of the specified Config.
     *
     * @param key the key of the {@code custom-metrics} entry config passed in the {@code config}.
     * @param config is supposed to provide the config for the issuer at its current level.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultCustomMetricConfig of(final String key, final Config config) {
        return new DefaultCustomMetricConfig(key,
                ConfigWithFallback.newInstance(config, CustomMetricConfigValue.values()));
    }

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
    public String getFilter() {
        return filter;
    }

    @Override
    public Map<String, String> getTags() {
        return tags;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultCustomMetricConfig that = (DefaultCustomMetricConfig) o;
        return enabled == that.enabled &&
                Objects.equals(scrapeInterval, that.scrapeInterval) &&
                Objects.equals(namespaces, that.namespaces) &&
                Objects.equals(filter, that.filter) &&
                Objects.equals(tags, that.tags);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, scrapeInterval, namespaces, filter, tags);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "enabled=" + enabled +
                ", scrapeInterval=" + scrapeInterval +
                ", namespaces=" + namespaces +
                ", filter=" + filter +
                ", tags=" + tags +
                "]";
    }
}
