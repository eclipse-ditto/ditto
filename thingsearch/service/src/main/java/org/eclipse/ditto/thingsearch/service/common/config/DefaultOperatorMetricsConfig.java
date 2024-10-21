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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigValue;

/**
 * This class is the default implementation for {@link OperatorMetricsConfig}.
 */
@Immutable
public final class DefaultOperatorMetricsConfig implements OperatorMetricsConfig {

    /**
     * Path where the operator metrics config values are expected.
     */
    static final String CONFIG_PATH = "operator-metrics";

    private final boolean enabled;
    private final Duration scrapeInterval;
    private final Map<String, CustomMetricConfig> customMetricConfigurations;
    private final Map<String, CustomAggregationMetricConfig> customAggregationMetricConfigs;

    private DefaultOperatorMetricsConfig(final ConfigWithFallback updaterScopedConfig) {
        enabled = updaterScopedConfig.getBoolean(OperatorMetricsConfigValue.ENABLED.getConfigPath());
        scrapeInterval = updaterScopedConfig.getNonNegativeDurationOrThrow(OperatorMetricsConfigValue.SCRAPE_INTERVAL);
        customMetricConfigurations = loadCustomMetricConfigurations(updaterScopedConfig,
                OperatorMetricsConfigValue.CUSTOM_METRICS);
        customAggregationMetricConfigs = loadCustomAggregatedMetricConfigurations(updaterScopedConfig,
                OperatorMetricsConfigValue.CUSTOM_AGGREGATION_METRIC);
    }

    /**
     * Returns an instance of DefaultOperatorMetricsConfig based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the updater config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultOperatorMetricsConfig of(final Config config) {
        return new DefaultOperatorMetricsConfig(
                ConfigWithFallback.newInstance(config, CONFIG_PATH, OperatorMetricsConfigValue.values()));
    }

    private static Map<String, CustomMetricConfig> loadCustomMetricConfigurations(final ConfigWithFallback config,
            final KnownConfigValue configValue) {

        final ConfigObject customMetricsConfig = config.getObject(configValue.getConfigPath());

        return customMetricsConfig.entrySet().stream().collect(CustomMetricConfigCollector.toMap());
    }

    private Map<String, CustomAggregationMetricConfig> loadCustomAggregatedMetricConfigurations(
            final ConfigWithFallback config, final KnownConfigValue configValue) {

        final ConfigObject customAggregatedMetricsConfig = config.getObject(configValue.getConfigPath());

        return customAggregatedMetricsConfig.entrySet().stream().collect(CustomAggregatedMetricConfigCollector.toMap());
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultOperatorMetricsConfig that = (DefaultOperatorMetricsConfig) o;
        return enabled == that.enabled &&
                Objects.equals(scrapeInterval, that.scrapeInterval);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, scrapeInterval, customMetricConfigurations);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "enabled=" + enabled +
                ", scrapeInterval=" + scrapeInterval +
                ", customMetricConfigurations=" + customMetricConfigurations +
                "]";
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public Duration getScrapeInterval() {
        return scrapeInterval;
    }

    @Override
    public Map<String, CustomMetricConfig> getCustomMetricConfigurations() {
        return customMetricConfigurations;
    }

    @Override
    public Map<String, CustomAggregationMetricConfig> getCustomAggregationMetricConfigs() {
        return customAggregationMetricConfigs;
    }

    private static class CustomMetricConfigCollector
            implements
            Collector<Map.Entry<String, ConfigValue>, Map<String, CustomMetricConfig>, Map<String, CustomMetricConfig>> {

        private static CustomMetricConfigCollector toMap() {
            return new CustomMetricConfigCollector();
        }

        @Override
        public Supplier<Map<String, CustomMetricConfig>> supplier() {
            return LinkedHashMap::new;
        }

        @Override
        public BiConsumer<Map<String, CustomMetricConfig>, Map.Entry<String, ConfigValue>> accumulator() {
            return (map, entry) -> map.put(entry.getKey(),
                    DefaultCustomMetricConfig.of(entry.getKey(), ConfigFactory.empty().withFallback(entry.getValue())));
        }

        @Override
        public BinaryOperator<Map<String, CustomMetricConfig>> combiner() {
            return (left, right) -> Stream.concat(left.entrySet().stream(), right.entrySet().stream())
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (u, v) -> {
                                throw new IllegalStateException(String.format("Duplicate key %s", u));
                            },
                            LinkedHashMap::new));
        }

        @Override
        public Function<Map<String, CustomMetricConfig>, Map<String, CustomMetricConfig>> finisher() {
            return map -> Collections.unmodifiableMap(new LinkedHashMap<>(map));
        }

        @Override
        public Set<Characteristics> characteristics() {
            return Collections.singleton(Characteristics.UNORDERED);
        }
    }

    private static class CustomAggregatedMetricConfigCollector implements
            Collector<Map.Entry<String, ConfigValue>, Map<String, CustomAggregationMetricConfig>, Map<String, CustomAggregationMetricConfig>> {

        private static CustomAggregatedMetricConfigCollector toMap() {
            return new CustomAggregatedMetricConfigCollector();
        }

        @Override
        public Supplier<Map<String, CustomAggregationMetricConfig>> supplier() {
            return LinkedHashMap::new;
        }

        @Override
        public BiConsumer<Map<String, CustomAggregationMetricConfig>, Map.Entry<String, ConfigValue>> accumulator() {
            return (map, entry) -> map.put(entry.getKey(),
                    DefaultCustomAggregationMetricConfig.of(entry.getKey(), ConfigFactory.empty().withFallback(entry.getValue())));
        }

        @Override
        public BinaryOperator<Map<String, CustomAggregationMetricConfig>> combiner() {
            return (left, right) -> Stream.concat(left.entrySet().stream(), right.entrySet().stream())
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (u, v) -> {
                                throw new IllegalStateException(String.format("Duplicate key %s", u));
                            },
                            LinkedHashMap::new));
        }

        @Override
        public Function<Map<String, CustomAggregationMetricConfig>, Map<String, CustomAggregationMetricConfig>> finisher() {
            return map -> Collections.unmodifiableMap(new LinkedHashMap<>(map));
        }

        @Override
        public Set<Characteristics> characteristics() {
            return Collections.singleton(Characteristics.UNORDERED);
        }
    }
}
