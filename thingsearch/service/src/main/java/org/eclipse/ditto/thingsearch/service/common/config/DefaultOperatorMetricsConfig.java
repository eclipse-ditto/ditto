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
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.KnownConfigValue;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.MongoDbConfig;

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

    /**
     * Path of the optional persistence config block used for the count based {@code custom-metrics} queries.
     */
    static final String CUSTOM_METRICS_PERSISTENCE_PATH = "custom-metrics-persistence";

    /**
     * Path of the optional persistence config block used for the {@code custom-aggregation-metrics} queries.
     */
    static final String CUSTOM_AGGREGATION_METRICS_PERSISTENCE_PATH = "custom-aggregation-metrics-persistence";

    private final boolean enabled;
    private final Duration scrapeInterval;
    private final Map<String, CustomMetricConfig> customMetricConfigurations;
    private final Map<String, CustomAggregationMetricConfig> customAggregationMetricConfigs;
    @Nullable private final SearchPersistenceConfig customMetricsPersistenceConfig;
    @Nullable private final SearchPersistenceConfig customAggregationMetricsPersistenceConfig;

    private DefaultOperatorMetricsConfig(final ConfigWithFallback updaterScopedConfig,
            final SearchPersistenceConfig queryPersistenceConfig) {
        enabled = updaterScopedConfig.getBoolean(OperatorMetricsConfigValue.ENABLED.getConfigPath());
        scrapeInterval = updaterScopedConfig.getNonNegativeDurationOrThrow(OperatorMetricsConfigValue.SCRAPE_INTERVAL);
        customMetricConfigurations = loadCustomMetricConfigurations(updaterScopedConfig,
                OperatorMetricsConfigValue.CUSTOM_METRICS);
        customAggregationMetricConfigs = loadCustomAggregatedMetricConfigurations(updaterScopedConfig,
                OperatorMetricsConfigValue.CUSTOM_AGGREGATION_METRIC);
        customMetricsPersistenceConfig = loadPersistenceConfig(updaterScopedConfig,
                CUSTOM_METRICS_PERSISTENCE_PATH, queryPersistenceConfig);
        customAggregationMetricsPersistenceConfig = loadPersistenceConfig(updaterScopedConfig,
                CUSTOM_AGGREGATION_METRICS_PERSISTENCE_PATH, queryPersistenceConfig);
    }

    /**
     * Returns an instance of DefaultOperatorMetricsConfig based on the settings of the specified Config, using the
     * {@code SearchPersistenceConfig} defaults as fallback for partially configured persistence blocks.
     *
     * @param config is supposed to provide the settings of the updater config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultOperatorMetricsConfig of(final Config config) {
        return of(config, DefaultSearchPersistenceConfig.of(ConfigFactory.empty()));
    }

    /**
     * Returns an instance of DefaultOperatorMetricsConfig based on the settings of the specified Config, inheriting
     * read settings which the optional persistence blocks leave out from the passed {@code queryPersistenceConfig}.
     *
     * @param config is supposed to provide the settings of the updater config at {@value #CONFIG_PATH}.
     * @param queryPersistenceConfig the general {@code query.persistence} config which individual read settings are
     * inherited from when a configured persistence block does not specify them.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     * @since 3.9.7
     */
    public static DefaultOperatorMetricsConfig of(final Config config,
            final SearchPersistenceConfig queryPersistenceConfig) {
        return new DefaultOperatorMetricsConfig(
                ConfigWithFallback.newInstance(config, CONFIG_PATH, OperatorMetricsConfigValue.values()),
                queryPersistenceConfig);
    }

    @Nullable
    private static SearchPersistenceConfig loadPersistenceConfig(final ConfigWithFallback updaterScopedConfig,
            final String configPath, final SearchPersistenceConfig queryPersistenceConfig) {

        if (!updaterScopedConfig.hasPath(configPath)) {
            return null;
        }
        // read settings the block does not specify are inherited from "query.persistence" instead of falling back to
        // the SearchPersistenceConfig defaults, so a partially configured block only overrides what it names:
        final Config blockConfig = updaterScopedConfig.getConfig(configPath)
                .withFallback(asConfig(queryPersistenceConfig));
        return DefaultSearchPersistenceConfig.ofScopedConfig(blockConfig);
    }

    private static Config asConfig(final SearchPersistenceConfig persistenceConfig) {
        return ConfigFactory.parseMap(Map.of(
                MongoDbConfig.OptionsConfig.OptionsConfigValue.READ_PREFERENCE.getConfigPath(),
                persistenceConfig.readPreference().getName(),
                MongoDbConfig.OptionsConfig.OptionsConfigValue.READ_CONCERN.getConfigPath(),
                persistenceConfig.readConcern().getName()));
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
                Objects.equals(scrapeInterval, that.scrapeInterval) &&
                Objects.equals(customMetricConfigurations, that.customMetricConfigurations) &&
                Objects.equals(customAggregationMetricConfigs, that.customAggregationMetricConfigs) &&
                Objects.equals(customMetricsPersistenceConfig, that.customMetricsPersistenceConfig) &&
                Objects.equals(customAggregationMetricsPersistenceConfig,
                        that.customAggregationMetricsPersistenceConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, scrapeInterval, customMetricConfigurations, customAggregationMetricConfigs,
                customMetricsPersistenceConfig, customAggregationMetricsPersistenceConfig);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "enabled=" + enabled +
                ", scrapeInterval=" + scrapeInterval +
                ", customMetricConfigurations=" + customMetricConfigurations +
                ", customAggregationMetricConfigs=" + customAggregationMetricConfigs +
                ", customMetricsPersistenceConfig=" + customMetricsPersistenceConfig +
                ", customAggregationMetricsPersistenceConfig=" + customAggregationMetricsPersistenceConfig +
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

    @Override
    public Optional<SearchPersistenceConfig> getCustomMetricsPersistenceConfig() {
        return Optional.ofNullable(customMetricsPersistenceConfig);
    }

    @Override
    public Optional<SearchPersistenceConfig> getCustomAggregationMetricsPersistenceConfig() {
        return Optional.ofNullable(customAggregationMetricsPersistenceConfig);
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
