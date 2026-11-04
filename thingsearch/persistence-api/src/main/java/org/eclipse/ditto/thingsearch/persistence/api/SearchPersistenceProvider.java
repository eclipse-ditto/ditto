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
package org.eclipse.ditto.thingsearch.persistence.api;

import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Props;
import org.eclipse.ditto.base.service.config.limits.LimitsConfig;
import org.eclipse.ditto.internal.utils.extension.DittoExtensionIds;
import org.eclipse.ditto.internal.utils.extension.DittoExtensionPoint;
import org.eclipse.ditto.internal.utils.pekko.streaming.TimestampPersistence;
import org.eclipse.ditto.rql.query.Query;
import org.eclipse.ditto.rql.query.QueryBuilderFactory;
import org.eclipse.ditto.thingsearch.persistence.api.write.SearchUpdaterFlow;

import com.typesafe.config.Config;

/**
 * Extension point for a pluggable thing-search persistence backend (MongoDB, PostgreSQL, ...).
 * <p>
 * A provider yields the backend-specific collaborators the thing-search service wires into its actors: the
 * read/updater/aggregation persistences, the updater flow, the background-sync bookmark persistence, the
 * health-check actor {@link Props}, the query-builder factory and a diagnostics renderer for slow-query
 * logging. The provider is selected at runtime via the Ditto extension config path
 * {@code ditto.extensions.search-persistence-provider}.
 * <p>
 * Constructed with {@code (ActorSystem, Config)} per the {@link DittoExtensionPoint} pattern (exactly like
 * {@code PersistenceBackendProvider}); implementations read their own config block from the actor-system
 * config. Factory methods take NO service config, because the service's {@code SearchConfig} is
 * Mongo-coupled and would leak backend-specific concerns through this neutral seam.
 *
 * @since 4.0.0
 */
public interface SearchPersistenceProvider extends DittoExtensionPoint {

    /**
     * Bootstraps the backend's search schema/indices, if any, before any search read/write happens. SYNCHRONOUS: it
     * blocks until the schema/indices are established and MUST throw if they cannot be, so the service refuses to
     * serve traffic against an unusable index. While the database is not reachable yet, a backend may wait for a
     * bounded, configured time before it throws (PostgreSQL: {@code ditto.postgresql.bootstrap}). The Mongo
     * implementation performs index initialization here.
     *
     * @throws RuntimeException if the schema/indices cannot be bootstrapped (boot must fail).
     */
    void bootstrapSchema();

    /**
     * @return the backend's read persistence used to answer search and count queries.
     */
    ThingsSearchPersistence createSearchPersistence();

    /**
     * @return the backend's updater persistence used by namespace operations and policy-reference lookups.
     */
    ThingsSearchUpdaterPersistence createUpdaterPersistence();

    /**
     * @return the backend's aggregation persistence used by the operator metrics feature.
     */
    ThingsAggregationPersistence createAggregationPersistence();

    /**
     * @return the backend's search updater flow that applies write models to the index.
     */
    SearchUpdaterFlow createUpdaterFlow();

    /**
     * @return the backend's timestamp persistence backing the background-sync bookmark.
     */
    TimestampPersistence createBackgroundSyncBookmarkPersistence();

    /**
     * @return the {@link Props} of the backend's search persistence health-check actor.
     */
    Props healthCheckProps();

    /**
     * @param limitsConfig the limits configuration constraining query result sizes.
     * @return the backend's query-builder factory that turns criteria into backend queries.
     */
    QueryBuilderFactory queryBuilderFactory(LimitsConfig limitsConfig);

    /**
     * Render the given query into a backend-specific string for slow-query diagnostics logging.
     * <p>
     * The rendering reproduces the backend filter the query would actually execute against, including the
     * authorization scoping. When {@code authorizationSubjectIds} is {@code null} the query is rendered as a
     * <em>sudo</em> query (no authorization filter is applied); otherwise the authorization filter for the given
     * subject IDs is folded into the rendering.
     *
     * @param query the query to render.
     * @param authorizationSubjectIds the authorization subject IDs to scope the rendering with, or {@code null} to
     * render the query without an authorization filter (sudo).
     * @return the backend-specific diagnostic rendering of the query.
     */
    String renderForDiagnostics(Query query, @Nullable List<String> authorizationSubjectIds);

    /**
     * Loads the configured {@code SearchPersistenceProvider} for the actor system.
     *
     * @param system the actor system in which the provider should be loaded.
     * @param config the configuration the extension is loaded from.
     * @return the configured provider instance.
     */
    static SearchPersistenceProvider get(final ActorSystem system, final Config config) {
        Objects.requireNonNull(system, "system");
        Objects.requireNonNull(config, "config");
        final var extensionIdConfig = ExtensionId.computeConfig(config);
        return DittoExtensionIds.get(system)
                .computeIfAbsent(extensionIdConfig, ExtensionId::new)
                .get(system);
    }

    /**
     * Extension ID for the search persistence provider.
     */
    final class ExtensionId extends DittoExtensionPoint.ExtensionId<SearchPersistenceProvider> {

        private static final String CONFIG_KEY = "search-persistence-provider";

        private ExtensionId(final ExtensionIdConfig<SearchPersistenceProvider> extensionIdConfig) {
            super(extensionIdConfig);
        }

        static ExtensionIdConfig<SearchPersistenceProvider> computeConfig(final Config config) {
            return ExtensionIdConfig.of(SearchPersistenceProvider.class, config, CONFIG_KEY);
        }

        @Override
        protected String getConfigKey() {
            return CONFIG_KEY;
        }

    }

}
