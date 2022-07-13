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

import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

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

    Optional<String> getMongoHintsByNamespace();

    /**
     * Returns the {@code QueryCriteriaValidator} to be used for validating and decoding
     * {@link org.eclipse.ditto.rql.query.criteria.Criteria} of a
     * {@link org.eclipse.ditto.thingsearch.model.signals.commands.query.ThingSearchQueryCommand}.
     *
     * @return the query validator implementation or {@code null}.
     */
    @Nullable
    String getQueryValidatorImplementation();

    /**
     * Returns the {@code SearchUpdateMapper} to be used for additional processing of search updates.
     *
     * @return the search update mapper implementation or {@code null}.
     */
    @Nullable
    String getSearchUpdateMapperImplementation();

    /**
     * Returns the {@code SearchUpdateObserver} to be used for additional processing of search updates.
     *
     * @return the name of the implementing class or {@code null}.
     */
    @Nullable
    String getSearchUpdateObserverImplementation();

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
     * @since 2.5.0
     */
    Map<String, String> getSimpleFieldMappings();

    /**
     * An enumeration of the known config path expressions and their associated default values for SearchConfig.
     */
    enum SearchConfigValue implements KnownConfigValue {

        /**
         * Default value is {@code null}.
         */
        MONGO_HINTS_BY_NAMESPACE("mongo-hints-by-namespace", null),

        /**
         * The {@code QueryCriteriaValidator} used for decoding and validating {@link org.eclipse.ditto.rql.query.criteria.Criteria}
         * of a {@link org.eclipse.ditto.thingsearch.model.signals.commands.query.ThingSearchQueryCommand}.
         *
         * @since 1.6.0
         */
        QUERY_CRITERIA_VALIDATOR("query-criteria-validator.implementation",
                "org.eclipse.ditto.thingsearch.service.persistence.query.validation.DefaultQueryCriteriaValidator"),

        /**
         * The {@code SearchUpdateMapper} used for additional custom processing of search updates.
         *
         * @since 2.1.0
         */
        SEARCH_UPDATE_MAPPER("search-update-mapper.implementation",
                "org.eclipse.ditto.thingsearch.service.persistence.write.streaming.DefaultSearchUpdateMapper"),

        /**
         * The {@code SearchUpdateObserver} used for additional custom processing of thing events.
         *
         * @since 2.3.0
         */
        SEARCH_UPDATE_OBSERVER("search-update-observer.implementation",
                "org.eclipse.ditto.thingsearch.service.updater.actors.DefaultSearchUpdateObserver"),

        /**
         * How simple fields are mapped during query parsing.
         *
         * @since 2.5.0
         */
        SIMPLE_FIELD_MAPPINGS("simple-field-mappings", ConfigValueFactory.fromMap(Map.of(
                "thingId", "_id",
                "namespace", "_namespace",
                "policyId", "/policyId",
                "_revision", "/_revision",
                "_modified", "/_modified",
                "_created", "/_created",
                "definition", "/definition"
        )));

        private final String path;
        private final Object defaultValue;

        private SearchConfigValue(final String path, final Object defaultValue) {
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
