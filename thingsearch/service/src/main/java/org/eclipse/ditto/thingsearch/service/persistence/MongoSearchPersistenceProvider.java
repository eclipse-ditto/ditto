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
package org.eclipse.ditto.thingsearch.service.persistence;

import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Props;
import org.apache.pekko.event.Logging;
import org.apache.pekko.event.LoggingAdapter;
import org.apache.pekko.stream.SystemMaterializer;
import org.eclipse.ditto.base.service.config.limits.LimitsConfig;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.pekko.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.pekko.logging.ThreadSafeDittoLogger;
import org.eclipse.ditto.internal.utils.pekko.streaming.TimestampPersistence;
import org.eclipse.ditto.internal.utils.persistence.mongo.DittoMongoClient;
import org.eclipse.ditto.internal.utils.persistence.mongo.streaming.MongoTimestampPersistence;
import org.eclipse.ditto.rql.query.Query;
import org.eclipse.ditto.rql.query.QueryBuilderFactory;
import org.eclipse.ditto.thingsearch.persistence.api.SearchPersistenceProvider;
import org.eclipse.ditto.thingsearch.persistence.api.ThingsAggregationPersistence;
import org.eclipse.ditto.thingsearch.persistence.api.ThingsSearchPersistence;
import org.eclipse.ditto.thingsearch.persistence.api.ThingsSearchUpdaterPersistence;
import org.eclipse.ditto.thingsearch.persistence.api.write.SearchUpdaterFlow;
import org.eclipse.ditto.thingsearch.service.common.config.DittoSearchConfig;
import org.eclipse.ditto.thingsearch.service.common.config.SearchConfig;
import org.eclipse.ditto.thingsearch.service.persistence.read.MongoThingsAggregationPersistence;
import org.eclipse.ditto.thingsearch.service.persistence.read.MongoThingsSearchPersistence;
import org.eclipse.ditto.thingsearch.service.persistence.read.criteria.visitors.CreateBsonVisitor;
import org.eclipse.ditto.thingsearch.service.persistence.read.query.MongoQueryBuilderFactory;
import org.eclipse.ditto.thingsearch.service.persistence.write.impl.MongoThingsSearchUpdaterPersistence;
import org.eclipse.ditto.thingsearch.service.persistence.write.streaming.MongoSearchUpdaterFlow;
import org.eclipse.ditto.thingsearch.service.starter.actors.MongoClientExtension;

import com.typesafe.config.Config;

import org.eclipse.ditto.internal.utils.persistence.mongo.MongoHealthChecker;

/**
 * MongoDB-backed implementation of {@link SearchPersistenceProvider} — the bundled default thing-search backend,
 * selected via {@code ditto.extensions.search-persistence-provider}.
 * <p>
 * Constructed reflectively by {@code DittoExtensionPoint} with {@code (ActorSystem, Config)}. The constructor is
 * intentionally lightweight (it opens no MongoDB connection): the {@link SearchConfig} is rebuilt lazily from the
 * actor-system config and all MongoDB client acquisition happens inside the {@code create*}/{@link #bootstrapSchema()}
 * methods (via {@link MongoClientExtension}) or inside the Mongo implementations themselves. This mirrors the
 * {@code MongoPersistenceBackendProvider} precedent used by the things/policies/connectivity services.
 *
 * @since 4.0.0
 */
public final class MongoSearchPersistenceProvider implements SearchPersistenceProvider {

    private static final ThreadSafeDittoLogger LOGGER =
            DittoLoggerFactory.getThreadSafeLogger(MongoSearchPersistenceProvider.class);

    private final ActorSystem actorSystem;

    @Nullable
    private SearchConfig searchConfig;
    @Nullable
    private ThingsSearchPersistence searchPersistence;

    /**
     * Constructor invoked reflectively by {@code PekkoClassLoader} during extension loading.
     *
     * @param actorSystem the actor system the extension is loaded into.
     * @param extensionConfig the {@code extension-config} subtree of the extension declaration (unused by the Mongo
     * provider, which reads its configuration from the actor-system config, matching today's behaviour).
     */
    public MongoSearchPersistenceProvider(final ActorSystem actorSystem, final Config extensionConfig) {
        this.actorSystem = Objects.requireNonNull(actorSystem, "actorSystem");
        Objects.requireNonNull(extensionConfig, "extensionConfig");
    }

    @Override
    public void bootstrapSchema() {
        final MongoThingsSearchPersistence persistence = (MongoThingsSearchPersistence) getOrBuildSearchPersistence();
        final var indexInitializationConfig = searchConfig().getIndexInitializationConfig();
        if (indexInitializationConfig.isIndexInitializationConfigEnabled()) {
            // Preserve today's semantics exactly: fire-and-forget async index initialization whose failures are
            // swallowed by MongoThingsSearchPersistence#initializeIndices (NOT joined). Mongo creates collections
            // lazily and never fail-fasts here; a fail-fast backend (Postgres) overrides bootstrapSchema().
            persistence.initializeIndices(indexInitializationConfig);
        } else {
            LOGGER.info("Skipping IndexInitializer because it is disabled.");
        }
    }

    @Override
    public ThingsSearchPersistence createSearchPersistence() {
        return getOrBuildSearchPersistence();
    }

    @Override
    public ThingsSearchUpdaterPersistence createUpdaterPersistence() {
        final DittoMongoClient updaterClient = MongoClientExtension.get(actorSystem).getUpdaterClient();
        return MongoThingsSearchUpdaterPersistence.of(updaterClient.getDefaultDatabase(),
                searchConfig().getUpdaterConfig().getUpdaterPersistenceConfig());
    }

    @Override
    public ThingsAggregationPersistence createAggregationPersistence() {
        final DittoMongoClient searchClient = MongoClientExtension.get(actorSystem).getSearchClient();
        final LoggingAdapter log = Logging.getLogger(actorSystem, MongoThingsAggregationPersistence.class);
        return MongoThingsAggregationPersistence.of(searchClient, searchConfig(), log);
    }

    @Override
    public SearchUpdaterFlow createUpdaterFlow() {
        final DittoMongoClient updaterClient = MongoClientExtension.get(actorSystem).getUpdaterClient();
        final var streamConfig = searchConfig().getUpdaterConfig().getStreamConfig();
        return MongoSearchUpdaterFlow.of(updaterClient.getDefaultDatabase(), streamConfig.getPersistenceConfig(),
                updaterClient.getMaxWireVersion());
    }

    @Override
    public TimestampPersistence createBackgroundSyncBookmarkPersistence() {
        final DittoMongoClient searchClient = MongoClientExtension.get(actorSystem).getSearchClient();
        return MongoTimestampPersistence.initializedInstance(PersistenceConstants.BACKGROUND_SYNC_COLLECTION_NAME,
                searchClient, SystemMaterializer.get(actorSystem).materializer());
    }

    @Override
    public Props healthCheckProps() {
        return MongoHealthChecker.props();
    }

    @Override
    public QueryBuilderFactory queryBuilderFactory(final LimitsConfig limitsConfig) {
        return new MongoQueryBuilderFactory(limitsConfig);
    }

    @Override
    public String renderForDiagnostics(final Query query, @Nullable final List<String> authorizationSubjectIds) {
        final var bson = authorizationSubjectIds == null
                ? CreateBsonVisitor.sudoApply(query.getCriteria())
                : CreateBsonVisitor.apply(query.getCriteria(), authorizationSubjectIds);
        return bson.toBsonDocument().toJson();
    }

    private synchronized ThingsSearchPersistence getOrBuildSearchPersistence() {
        if (searchPersistence == null) {
            final DittoMongoClient searchClient = MongoClientExtension.get(actorSystem).getSearchClient();
            searchPersistence = new MongoThingsSearchPersistence(searchClient, actorSystem,
                    searchConfig().getQueryPersistenceConfig(), searchConfig());
        }
        return searchPersistence;
    }

    private synchronized SearchConfig searchConfig() {
        if (searchConfig == null) {
            searchConfig = DittoSearchConfig.of(DefaultScopedConfig.dittoScoped(actorSystem.settings().config()));
        }
        return searchConfig;
    }

}
