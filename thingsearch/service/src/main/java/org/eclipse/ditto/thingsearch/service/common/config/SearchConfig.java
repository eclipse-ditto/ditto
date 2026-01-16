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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.persistence.mongo.indices.Index;

import org.eclipse.ditto.base.service.config.ServiceSpecificConfig;
import org.eclipse.ditto.internal.utils.config.KnownConfigValue;
import org.eclipse.ditto.internal.utils.health.config.WithHealthCheckConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.WithIndexInitializationConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.WithMongoDbConfig;
import org.eclipse.ditto.internal.utils.persistence.operations.WithPersistenceOperationsConfig;

import com.typesafe.config.ConfigValueFactory;

/**
 * Provides the configuration settings of the Search service.
 */
@Immutable
public interface SearchConfig extends ServiceSpecificConfig, WithHealthCheckConfig, WithPersistenceOperationsConfig,
        WithMongoDbConfig, WithIndexInitializationConfig {

    /**
     * Returns a JSON string with hints where the namespace are the keys and the hints either the index name or a
     * JSON object, containing the fields of the index to hint for, the values. Example:
     * <pre>
     * {
     *     "org.eclipse.ditto": {
     *        "gr": 1,
     *        "_id": 1
     *     },
     *     "org.eclipse.other": "v_wildcard"
     * }
     * </pre>
     *
     * @return the JSON string with hints.
     */
    Optional<String> getMongoHintsByNamespace();

    /**
     * Returns the optional default index name to use for doing count queries at the search collection, e.g.
     * preventing use of a certain index.
     *
     * @return the index name to hint for doing count queries.
     */
    Optional<String> getMongoCountHintIndexName();

    /**
     * Returns the configuration settings for the search updating functionality.
     *
     * @return the config.
     */
    UpdaterConfig getUpdaterConfig();

    /**
     * Returns the query persistence config.
     *
     * @return the config.
     */
    SearchPersistenceConfig getQueryPersistenceConfig();

    /**
     * Returns how simple fields are mapped during query parsing.
     *
     * @return the simple field mapping.
     */
    Map<String, String> getSimpleFieldMappings();

    /**
     * Returns the operator metrics configuration containing metrics to be exposed via Prometheus based on configured
     * search "count" queries.
     *
     * @return the operator metrics configuration.
     */
    OperatorMetricsConfig getOperatorMetricsConfig();

    /**
     * Returns a map of fields scoped by namespaces that will be explicitly included in the search index.
     *
     * @return the search projection fields.
     * @since 3.5.0
     */
    List<NamespaceSearchIndexConfig> getNamespaceIndexedFields();

    /**
     * Returns the custom search indexes configuration.
     * These indexes are created in addition to the hardcoded indexes defined in
     * {@link org.eclipse.ditto.thingsearch.service.persistence.Indices}.
     *
     * @return the map of custom index configurations (index name to config).
     * @since 3.9.0
     */
    Map<String, CustomSearchIndexConfig> getCustomIndexes();

    /**
     * Converts the configured custom indexes to MongoDB {@link Index} objects.
     *
     * @return list of Index objects created from the configuration.
     * @since 3.8.0
     */
    List<Index> getCustomIndexesAsIndices();

    /**
     * An enumeration of the known config path expressions and their associated default values for SearchConfig.
     */
    enum SearchConfigValue implements KnownConfigValue {

        /**
         * Default value is {@code null}.
         */
        MONGO_HINTS_BY_NAMESPACE("mongo-hints-by-namespace", null),

        /**
         * Default value is {@code null}.
         */
        MONGO_COUNT_HINT_INDEX_NAME("mongo-count-hint-index-name", null),

        /**
         * How simple fields are mapped during query parsing.
         *
         * @since 3.0.0
         */
        SIMPLE_FIELD_MAPPINGS("simple-field-mappings", ConfigValueFactory.fromMap(
                Map.of(
                "thingId", "_id",
                "namespace", "_namespace",
                "policyId", "/policyId",
                "_revision", "/_revision",
                "_modified", "/_modified",
                "_created", "/_created",
                "definition", "/definition",
                "_metadata", "/_metadata"
                )
        )),

        /**
         * Any fields to include in the search index, scoped by namespace.
         *
         * @since 3.5.0
         */
        NAMESPACE_INDEXED_FIELDS("namespace-indexed-fields", Collections.emptyList()),

        /**
         * Custom search indexes to create in addition to hardcoded indexes.
         *
         * @since 3.8.0
         */
        CUSTOM_INDEXES("index-initialization.custom-indexes", Collections.emptyMap());

        private final String path;
        private final Object defaultValue;

        SearchConfigValue(final String path, final Object defaultValue) {
            this.path = path;
            this.defaultValue = defaultValue;
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
