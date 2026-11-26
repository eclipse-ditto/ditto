/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.thingsearch.persistence.mongo.config;

import java.text.MessageFormat;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.config.DittoConfigError;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.DefaultIndexInitializationConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.DefaultMongoDbConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.IndexInitializationConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.MongoDbConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.indices.DefaultIndexKey;
import org.eclipse.ditto.internal.utils.persistence.mongo.indices.Index;
import org.eclipse.ditto.internal.utils.persistence.mongo.indices.IndexDirection;
import org.eclipse.ditto.internal.utils.persistence.mongo.indices.IndexFactory;
import org.eclipse.ditto.internal.utils.persistence.mongo.indices.IndexKey;
import org.eclipse.ditto.thingsearch.persistence.api.config.CustomSearchIndexConfig;
import org.eclipse.ditto.thingsearch.persistence.api.config.CustomSearchIndexFieldConfig;
import org.eclipse.ditto.thingsearch.persistence.api.config.DefaultCustomSearchIndexConfig;
import org.eclipse.ditto.thingsearch.persistence.api.config.DefaultPersistenceStreamConfig;
import org.eclipse.ditto.thingsearch.persistence.api.config.DefaultSearchPersistenceConfig;
import org.eclipse.ditto.thingsearch.persistence.api.config.PersistenceStreamConfig;
import org.eclipse.ditto.thingsearch.persistence.api.config.SearchPersistenceConfig;

import com.mongodb.WriteConcern;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * This class is the default implementation of {@link MongoSearchConfig}.
 */
@Immutable
public final class DefaultMongoSearchConfig implements MongoSearchConfig {

    private static final String SEARCH_PATH = "search";
    private static final String QUERY_PATH = "query";
    private static final String UPDATER_PATH = "updater";
    private static final String STREAM_PATH = "stream";

    // the same paths as in the service's DefaultOperatorMetricsConfig, which this backend config must not depend on:
    private static final String OPERATOR_METRICS_PATH = "operator-metrics";
    private static final String CUSTOM_AGGREGATION_METRICS_PERSISTENCE_PATH = "custom-aggregation-metrics-persistence";

    private final MongoDbConfig mongoDbConfig;
    private final IndexInitializationConfig indexInitializationConfig;
    private final List<Index> customIndexesAsIndices;
    @Nullable private final String mongoHintsByNamespace;
    @Nullable private final String mongoCountHintIndexName;
    private final Map<String, String> simpleFieldMappings;
    private final SearchPersistenceConfig queryPersistenceConfig;
    private final SearchPersistenceConfig updaterPersistenceConfig;
    @Nullable private final SearchPersistenceConfig customAggregationMetricsPersistenceConfig;
    private final PersistenceStreamConfig updaterStreamPersistenceConfig;
    private final WriteConcern withAcknowledgementsWriteConcern;

    private DefaultMongoSearchConfig(final Config rawActorSystemConfig) {
        final DefaultScopedConfig dittoScopedConfig = DefaultScopedConfig.dittoScoped(rawActorSystemConfig);
        mongoDbConfig = DefaultMongoDbConfig.of(dittoScopedConfig);

        final ConfigWithFallback searchConfig =
                ConfigWithFallback.newInstance(dittoScopedConfig, SEARCH_PATH, MongoSearchConfigValue.values());
        indexInitializationConfig = DefaultIndexInitializationConfig.of(searchConfig);
        customIndexesAsIndices = loadCustomIndexesAsIndices(searchConfig);
        mongoHintsByNamespace = searchConfig.getStringOrNull(MongoSearchConfigValue.MONGO_HINTS_BY_NAMESPACE);
        mongoCountHintIndexName = searchConfig.getStringOrNull(MongoSearchConfigValue.MONGO_COUNT_HINT_INDEX_NAME);
        simpleFieldMappings =
                convertToMap(searchConfig.getConfig(MongoSearchConfigValue.SIMPLE_FIELD_MAPPINGS.getConfigPath()));

        queryPersistenceConfig = DefaultSearchPersistenceConfig.of(getConfigOrEmpty(searchConfig, QUERY_PATH));
        final Config updaterConfig = getConfigOrEmpty(searchConfig, UPDATER_PATH);
        updaterPersistenceConfig = DefaultSearchPersistenceConfig.of(updaterConfig);
        updaterStreamPersistenceConfig =
                DefaultPersistenceStreamConfig.of(getConfigOrEmpty(updaterConfig, STREAM_PATH));
        customAggregationMetricsPersistenceConfig =
                loadCustomAggregationMetricsPersistenceConfig(searchConfig, queryPersistenceConfig);
        withAcknowledgementsWriteConcern = loadWithAcknowledgementsWriteConcern(searchConfig);
    }

    /**
     * Returns an instance of DefaultMongoSearchConfig based on the settings of the specified raw actor-system config.
     *
     * @param rawActorSystemConfig the actor-system config, providing the settings at {@code ditto}.
     * @return the instance.
     * @throws DittoConfigError if the config is invalid.
     */
    public static DefaultMongoSearchConfig of(final Config rawActorSystemConfig) {
        return new DefaultMongoSearchConfig(rawActorSystemConfig);
    }

    private static Config getConfigOrEmpty(final Config config, final String path) {
        return config.hasPath(path) ? config.getConfig(path) : ConfigFactory.empty();
    }

    private static List<Index> loadCustomIndexesAsIndices(final ConfigWithFallback searchConfig) {
        final String path = MongoSearchConfigValue.CUSTOM_INDEXES.getConfigPath();
        final Map<String, CustomSearchIndexConfig> customIndexes = searchConfig.hasPath(path)
                ? DefaultCustomSearchIndexConfig.allOf(searchConfig.getObject(path))
                : Collections.emptyMap();
        return customIndexes.values().stream()
                .map(DefaultMongoSearchConfig::toIndex)
                .toList();
    }

    private static Index toIndex(final CustomSearchIndexConfig config) {
        final List<IndexKey> keys = config.getFields().stream()
                .map(field -> (IndexKey) DefaultIndexKey.of(field.getName(), toIndexDirection(field.getDirection())))
                .toList();
        return IndexFactory.newInstanceWithCustomKeys(config.getName(), keys, false);
    }

    private static IndexDirection toIndexDirection(final CustomSearchIndexFieldConfig.Direction direction) {
        return switch (direction) {
            case ASC -> IndexDirection.ASCENDING;
            case DESC -> IndexDirection.DESCENDING;
        };
    }

    private static Map<String, String> convertToMap(final Config config) {
        return config.root()
                .unwrapped()
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue() instanceof String)
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> (String) entry.getValue()));
    }

    @Nullable
    private static SearchPersistenceConfig loadCustomAggregationMetricsPersistenceConfig(
            final ConfigWithFallback searchConfig, final SearchPersistenceConfig queryPersistenceConfig) {

        final String path = OPERATOR_METRICS_PATH + "." + CUSTOM_AGGREGATION_METRICS_PERSISTENCE_PATH;
        if (!searchConfig.hasPath(path)) {
            return null;
        }
        return DefaultSearchPersistenceConfig.ofBlockInheriting(searchConfig.getConfig(path), queryPersistenceConfig);
    }

    private static WriteConcern loadWithAcknowledgementsWriteConcern(final ConfigWithFallback searchConfig) {
        final String writeConcernString =
                searchConfig.getString(MongoSearchConfigValue.WITH_ACKS_WRITE_CONCERN.getConfigPath());
        return Optional.ofNullable(WriteConcern.valueOf(writeConcernString))
                .orElseThrow(() -> new DittoConfigError(
                        MessageFormat.format("Could not parse a WriteConcern from configured string <{0}>",
                                writeConcernString)));
    }

    @Override
    public MongoDbConfig getMongoDbConfig() {
        return mongoDbConfig;
    }

    @Override
    public IndexInitializationConfig getIndexInitializationConfig() {
        return indexInitializationConfig;
    }

    @Override
    public List<Index> getCustomIndexesAsIndices() {
        return customIndexesAsIndices;
    }

    @Override
    public Optional<String> getMongoHintsByNamespace() {
        return Optional.ofNullable(mongoHintsByNamespace);
    }

    @Override
    public Optional<String> getMongoCountHintIndexName() {
        return Optional.ofNullable(mongoCountHintIndexName);
    }

    @Override
    public Map<String, String> getSimpleFieldMappings() {
        return simpleFieldMappings;
    }

    @Override
    public SearchPersistenceConfig getQueryPersistenceConfig() {
        return queryPersistenceConfig;
    }

    @Override
    public SearchPersistenceConfig getUpdaterPersistenceConfig() {
        return updaterPersistenceConfig;
    }

    @Override
    public Optional<SearchPersistenceConfig> getCustomAggregationMetricsPersistenceConfig() {
        return Optional.ofNullable(customAggregationMetricsPersistenceConfig);
    }

    @Override
    public PersistenceStreamConfig getUpdaterStreamPersistenceConfig() {
        return updaterStreamPersistenceConfig;
    }

    @Override
    public WriteConcern getWithAcknowledgementsWriteConcern() {
        return withAcknowledgementsWriteConcern;
    }

    @SuppressWarnings("OverlyComplexMethod")
    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultMongoSearchConfig that = (DefaultMongoSearchConfig) o;
        return Objects.equals(mongoDbConfig, that.mongoDbConfig) &&
                Objects.equals(indexInitializationConfig, that.indexInitializationConfig) &&
                Objects.equals(customIndexesAsIndices, that.customIndexesAsIndices) &&
                Objects.equals(mongoHintsByNamespace, that.mongoHintsByNamespace) &&
                Objects.equals(mongoCountHintIndexName, that.mongoCountHintIndexName) &&
                Objects.equals(simpleFieldMappings, that.simpleFieldMappings) &&
                Objects.equals(queryPersistenceConfig, that.queryPersistenceConfig) &&
                Objects.equals(updaterPersistenceConfig, that.updaterPersistenceConfig) &&
                Objects.equals(customAggregationMetricsPersistenceConfig,
                        that.customAggregationMetricsPersistenceConfig) &&
                Objects.equals(updaterStreamPersistenceConfig, that.updaterStreamPersistenceConfig) &&
                Objects.equals(withAcknowledgementsWriteConcern, that.withAcknowledgementsWriteConcern);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mongoDbConfig, indexInitializationConfig, customIndexesAsIndices, mongoHintsByNamespace,
                mongoCountHintIndexName, simpleFieldMappings, queryPersistenceConfig, updaterPersistenceConfig,
                customAggregationMetricsPersistenceConfig, updaterStreamPersistenceConfig,
                withAcknowledgementsWriteConcern);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "mongoDbConfig=" + mongoDbConfig +
                ", indexInitializationConfig=" + indexInitializationConfig +
                ", customIndexesAsIndices=" + customIndexesAsIndices +
                ", mongoHintsByNamespace=" + mongoHintsByNamespace +
                ", mongoCountHintIndexName=" + mongoCountHintIndexName +
                ", simpleFieldMappings=" + simpleFieldMappings +
                ", queryPersistenceConfig=" + queryPersistenceConfig +
                ", updaterPersistenceConfig=" + updaterPersistenceConfig +
                ", customAggregationMetricsPersistenceConfig=" + customAggregationMetricsPersistenceConfig +
                ", updaterStreamPersistenceConfig=" + updaterStreamPersistenceConfig +
                ", withAcknowledgementsWriteConcern=" + withAcknowledgementsWriteConcern +
                "]";
    }

}
