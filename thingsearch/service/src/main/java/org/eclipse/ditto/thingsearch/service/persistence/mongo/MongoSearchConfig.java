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
package org.eclipse.ditto.thingsearch.service.persistence.mongo;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.KnownConfigValue;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.IndexInitializationConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.MongoDbConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.indices.Index;
import org.eclipse.ditto.thingsearch.persistence.api.config.PersistenceStreamConfig;
import org.eclipse.ditto.thingsearch.persistence.api.config.SearchPersistenceConfig;

import com.mongodb.WriteConcern;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigValueFactory;

/**
 * The configuration the MongoDB search backend reads, built from the raw actor-system config: the
 * {@code ditto.mongodb} connection settings and the MongoDB specific settings below {@code ditto.search}, together
 * with the backend-neutral persistence settings the backend needs.
 */
@Immutable
public interface MongoSearchConfig {

    /**
     * Returns the MongoDB connection settings at {@code ditto.mongodb}.
     *
     * @return the MongoDB config.
     */
    MongoDbConfig getMongoDbConfig();

    /**
     * Returns the index initialization settings at {@code ditto.search.index-initialization}.
     *
     * @return the index initialization config.
     */
    IndexInitializationConfig getIndexInitializationConfig();

    /**
     * Returns the configured custom indexes ({@code ditto.search.index-initialization.custom-indexes}) as MongoDB
     * {@link Index} objects, in the configured order.
     *
     * @return the custom indexes.
     */
    List<Index> getCustomIndexesAsIndices();

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
     * Returns how simple fields are mapped during query parsing.
     *
     * @return the simple field mapping.
     */
    Map<String, String> getSimpleFieldMappings();

    /**
     * Returns the query persistence config at {@code ditto.search.query.persistence}.
     *
     * @return the config.
     */
    SearchPersistenceConfig getQueryPersistenceConfig();

    /**
     * Returns the updater persistence config at {@code ditto.search.updater.persistence}.
     *
     * @return the config.
     */
    SearchPersistenceConfig getUpdaterPersistenceConfig();

    /**
     * Returns the persistence config of the custom aggregation metrics queries at
     * {@code ditto.search.operator-metrics.custom-aggregation-metrics-persistence}, inheriting the read settings it
     * does not specify from {@link #getQueryPersistenceConfig()}.
     *
     * @return the config, or an empty Optional if the block is not configured.
     */
    Optional<SearchPersistenceConfig> getCustomAggregationMetricsPersistenceConfig();

    /**
     * Returns the updater stream persistence config at {@code ditto.search.updater.stream.persistence}.
     *
     * @return the config.
     */
    PersistenceStreamConfig getUpdaterStreamPersistenceConfig();

    /**
     * Returns the MongoDB {@link WriteConcern} to use for updating the search index for events which require
     * {@code "search-persisted"} acknowledgements.
     *
     * @return the write concern.
     */
    WriteConcern getWithAcknowledgementsWriteConcern();

    /**
     * Returns the MongoSearchConfig of the specified raw actor-system config.
     *
     * @param rawActorSystemConfig the actor-system config, providing the settings at {@code ditto}.
     * @return the config.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if the config is invalid.
     */
    static MongoSearchConfig of(final Config rawActorSystemConfig) {
        return DefaultMongoSearchConfig.of(rawActorSystemConfig);
    }

    /**
     * An enumeration of the known config path expressions, relative to {@code ditto.search}, and their associated
     * default values for {@code MongoSearchConfig}.
     */
    enum MongoSearchConfigValue implements KnownConfigValue {

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
         * Custom search indexes to create in addition to the hardcoded indexes.
         */
        CUSTOM_INDEXES("index-initialization.custom-indexes", Collections.emptyMap()),

        /**
         * The write concern used for search index updates requiring acknowledgements.
         * See {@link WriteConcern} for available options.
         */
        WITH_ACKS_WRITE_CONCERN("updater.stream.persistence.with-acks-writeConcern", "journaled");

        private final String path;
        private final Object defaultValue;

        MongoSearchConfigValue(final String path, final Object defaultValue) {
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
