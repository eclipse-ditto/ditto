/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

import org.eclipse.ditto.base.service.config.DittoServiceConfig;
import org.eclipse.ditto.base.service.config.http.HttpConfig;
import org.eclipse.ditto.base.service.config.limits.LimitsConfig;
import org.eclipse.ditto.internal.utils.cluster.config.ClusterConfig;
import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;
import org.eclipse.ditto.internal.utils.config.WithConfigPath;
import org.eclipse.ditto.internal.utils.health.config.DefaultHealthCheckConfig;
import org.eclipse.ditto.internal.utils.health.config.HealthCheckConfig;
import org.eclipse.ditto.internal.utils.metrics.config.MetricsConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.DefaultIndexInitializationConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.DefaultMongoDbConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.IndexInitializationConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.MongoDbConfig;
import org.eclipse.ditto.internal.utils.persistence.operations.DefaultPersistenceOperationsConfig;
import org.eclipse.ditto.internal.utils.persistence.operations.PersistenceOperationsConfig;
import org.eclipse.ditto.internal.utils.tracing.config.TracingConfig;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigList;
import com.typesafe.config.ConfigValue;


/**
 * This class is the default implementation of {@link SearchConfig}.
 */
@Immutable
public final class DittoSearchConfig implements SearchConfig, WithConfigPath {

    private static final String CONFIG_PATH = "search";

    private static final String QUERY_PATH = "query";

    private final DittoServiceConfig dittoServiceConfig;
    @Nullable private final String mongoHintsByNamespace;
    private final UpdaterConfig updaterConfig;
    private final HealthCheckConfig healthCheckConfig;
    private final IndexInitializationConfig indexInitializationConfig;
    private final PersistenceOperationsConfig persistenceOperationsConfig;
    private final MongoDbConfig mongoDbConfig;
    private final SearchPersistenceConfig queryPersistenceConfig;
    private final Map<String, String> simpleFieldMappings;
    private final List<NamespaceSearchIndexConfig> namespaceIndexedFields;
    private final DefaultOperatorMetricsConfig operatorMetricsConfig;

    private DittoSearchConfig(final ScopedConfig dittoScopedConfig) {
        dittoServiceConfig = DittoServiceConfig.of(dittoScopedConfig, CONFIG_PATH);
        persistenceOperationsConfig = DefaultPersistenceOperationsConfig.of(dittoScopedConfig);
        mongoDbConfig = DefaultMongoDbConfig.of(dittoScopedConfig);
        healthCheckConfig = DefaultHealthCheckConfig.of(dittoScopedConfig);

        final var configWithFallback =
                ConfigWithFallback.newInstance(dittoScopedConfig, CONFIG_PATH, SearchConfigValue.values());
        mongoHintsByNamespace = configWithFallback.getStringOrNull(SearchConfigValue.MONGO_HINTS_BY_NAMESPACE);
        updaterConfig = DefaultUpdaterConfig.of(configWithFallback);
        indexInitializationConfig = DefaultIndexInitializationConfig.of(configWithFallback);

        final var queryConfig = configWithFallback.hasPath(QUERY_PATH)
                ? configWithFallback.getConfig(QUERY_PATH)
                : ConfigFactory.empty();
        queryPersistenceConfig = DefaultSearchPersistenceConfig.of(queryConfig);
        simpleFieldMappings =
                convertToMap(configWithFallback.getConfig(SearchConfigValue.SIMPLE_FIELD_MAPPINGS.getConfigPath()));
        namespaceIndexedFields = loadNamespaceSearchIndexList(configWithFallback);
        operatorMetricsConfig = DefaultOperatorMetricsConfig.of(configWithFallback);
    }

    /**
     * Returns an instance of DittoSearchConfig based on the settings of the specified Config.
     *
     * @param dittoScopedConfig is supposed to provide the settings of the service config at the {@code "ditto"} config
     * path.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DittoSearchConfig of(final ScopedConfig dittoScopedConfig) {
        return new DittoSearchConfig(dittoScopedConfig);
    }

    @Override
    public Optional<String> getMongoHintsByNamespace() {
        return Optional.ofNullable(mongoHintsByNamespace);
    }

    @Override
    public List<NamespaceSearchIndexConfig> getNamespaceIndexedFields() {
        return namespaceIndexedFields;
    }

    @Override
    public UpdaterConfig getUpdaterConfig() {
        return updaterConfig;
    }

    @Override
    public SearchPersistenceConfig getQueryPersistenceConfig() {
        return queryPersistenceConfig;
    }

    public Map<String, String> getSimpleFieldMappings() {
        return simpleFieldMappings;
    }

    @Override
    public DefaultOperatorMetricsConfig getOperatorMetricsConfig() {
        return operatorMetricsConfig;
    }

    @Override
    public ClusterConfig getClusterConfig() {
        return dittoServiceConfig.getClusterConfig();
    }

    @Override
    public LimitsConfig getLimitsConfig() {
        return dittoServiceConfig.getLimitsConfig();
    }

    @Override
    public HttpConfig getHttpConfig() {
        return dittoServiceConfig.getHttpConfig();
    }

    @Override
    public MetricsConfig getMetricsConfig() {
        return dittoServiceConfig.getMetricsConfig();
    }

    @Override
    public TracingConfig getTracingConfig() {
        return dittoServiceConfig.getTracingConfig();
    }

    @Override
    public HealthCheckConfig getHealthCheckConfig() {
        return healthCheckConfig;
    }

    @Override
    public IndexInitializationConfig getIndexInitializationConfig() {
        return indexInitializationConfig;
    }

    @Override
    public PersistenceOperationsConfig getPersistenceOperationsConfig() {
        return persistenceOperationsConfig;
    }

    @Override
    public MongoDbConfig getMongoDbConfig() {
        return mongoDbConfig;
    }

    @SuppressWarnings("OverlyComplexMethod")
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DittoSearchConfig that = (DittoSearchConfig) o;
        return Objects.equals(mongoHintsByNamespace, that.mongoHintsByNamespace) &&
                Objects.equals(updaterConfig, that.updaterConfig) &&
                Objects.equals(dittoServiceConfig, that.dittoServiceConfig) &&
                Objects.equals(healthCheckConfig, that.healthCheckConfig) &&
                Objects.equals(indexInitializationConfig, that.indexInitializationConfig) &&
                Objects.equals(persistenceOperationsConfig, that.persistenceOperationsConfig) &&
                Objects.equals(mongoDbConfig, that.mongoDbConfig) &&
                Objects.equals(queryPersistenceConfig, that.queryPersistenceConfig) &&
                Objects.equals(simpleFieldMappings, that.simpleFieldMappings) &&
                Objects.equals(operatorMetricsConfig, that.operatorMetricsConfig) &&
                Objects.equals(namespaceIndexedFields, that.namespaceIndexedFields);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mongoHintsByNamespace, updaterConfig, dittoServiceConfig, healthCheckConfig,
                indexInitializationConfig, persistenceOperationsConfig, mongoDbConfig, queryPersistenceConfig,
                simpleFieldMappings, operatorMetricsConfig, namespaceIndexedFields);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "mongoHintsByNamespace=" + mongoHintsByNamespace +
                ", updaterConfig=" + updaterConfig +
                ", dittoServiceConfig=" + dittoServiceConfig +
                ", healthCheckConfig=" + healthCheckConfig +
                ", indexInitializationConfig=" + indexInitializationConfig +
                ", persistenceOperationsConfig=" + persistenceOperationsConfig +
                ", mongoDbConfig=" + mongoDbConfig +
                ", queryPersistenceConfig=" + queryPersistenceConfig +
                ", simpleFieldMappings=" + simpleFieldMappings +
                ", namespaceIndexedFields=" + namespaceIndexedFields +
                ", operatorMetricsConfig=" + operatorMetricsConfig +
                "]";
    }

    @Override
    public String getConfigPath() {
        return CONFIG_PATH;
    }

    private static Map<String, String> convertToMap(final Config config) {
        return config.root()
                .unwrapped()
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue() instanceof String)
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> (String) entry.getValue()));
    }

    private static List<NamespaceSearchIndexConfig> loadNamespaceSearchIndexList(final ConfigWithFallback config) {

        final ConfigList namespaceIndexedFieldsConfig = config.getList(
                SearchConfigValue.NAMESPACE_INDEXED_FIELDS.getConfigPath());

        return namespaceIndexedFieldsConfig.stream().collect(NamespaceSearchIndexCollector.toNamespaceSearchIndexList());
    }

    private static class NamespaceSearchIndexCollector implements
            Collector<ConfigValue, List<NamespaceSearchIndexConfig>, List<NamespaceSearchIndexConfig>> {

        private static NamespaceSearchIndexCollector toNamespaceSearchIndexList() {
            return new NamespaceSearchIndexCollector();
        }

        @Override
        public Supplier<List<NamespaceSearchIndexConfig>> supplier() {
            return ArrayList::new;
        }

        @Override
        public BiConsumer<List<NamespaceSearchIndexConfig>, ConfigValue> accumulator() {
            return (list, entry) ->
                    list.add(DefaultNamespaceSearchIndexConfig.of(ConfigFactory.empty().withFallback(entry)));
        }

        @Override
        public BinaryOperator<List<NamespaceSearchIndexConfig>> combiner() {
            return (left, right) -> Stream.concat(left.stream(), right.stream())
                    .toList();
        }

        @Override
        public Function<List<NamespaceSearchIndexConfig>, List<NamespaceSearchIndexConfig>> finisher() {
            return List::copyOf;
        }

        @Override
        public Set<Characteristics> characteristics() {
            return Collections.singleton(Characteristics.UNORDERED);
        }
    }
}
