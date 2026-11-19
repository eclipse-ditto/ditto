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

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.persistence.api.operations.WithPersistenceOperationsConfig;

import org.eclipse.ditto.base.service.config.ServiceSpecificConfig;
import org.eclipse.ditto.internal.utils.config.KnownConfigValue;
import org.eclipse.ditto.internal.utils.health.config.WithHealthCheckConfig;
import org.eclipse.ditto.thingsearch.persistence.api.config.CustomSearchIndexConfig;
import org.eclipse.ditto.thingsearch.persistence.api.config.SearchPersistenceConfig;

import com.typesafe.config.ConfigValueFactory;

/**
 * Provides the configuration settings of the Search service.
 */
@Immutable
public interface SearchConfig extends ServiceSpecificConfig, WithHealthCheckConfig, WithPersistenceOperationsConfig {

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
     * Returns the slow query log configuration for logging queries that exceed a configurable threshold.
     *
     * @return the slow query log configuration.
     */
    SlowQueryLogConfig getSlowQueryLogConfig();

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
     * {@code Indices}.
     *
     * @return the map of custom index configurations (index name to config).
     * @since 3.9.0
     */
    Map<String, CustomSearchIndexConfig> getCustomIndexes();

    /**
     * An enumeration of the known config path expressions and their associated default values for SearchConfig.
     */
    enum SearchConfigValue implements KnownConfigValue {

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
