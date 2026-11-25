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
package org.eclipse.ditto.internal.utils.persistence.postgres.search.parity;

import java.time.Duration;

import org.apache.pekko.actor.ActorSystem;
import org.eclipse.ditto.base.service.config.limits.DefaultLimitsConfig;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.DittoMongoClient;
import org.eclipse.ditto.internal.utils.persistence.mongo.MongoClientWrapper;
import org.eclipse.ditto.internal.utils.test.docker.mongo.MongoDbResource;
import org.eclipse.ditto.rql.query.QueryBuilderFactory;
import org.eclipse.ditto.thingsearch.persistence.api.ThingsSearchPersistence;
import org.eclipse.ditto.thingsearch.persistence.api.write.SearchUpdaterFlow;
import org.eclipse.ditto.thingsearch.service.common.config.DefaultPersistenceStreamConfig;
import org.eclipse.ditto.thingsearch.service.common.config.DittoSearchConfig;
import org.eclipse.ditto.thingsearch.service.persistence.read.MongoThingsSearchPersistence;
import org.eclipse.ditto.thingsearch.service.persistence.read.query.MongoQueryBuilderFactory;
import org.eclipse.ditto.thingsearch.service.persistence.write.streaming.MongoSearchUpdaterFlow;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * The Mongo side of the parity matrix: {@code MongoSearchUpdaterFlow} writing into a real Mongo container (through
 * the neutral write-model bridge including {@code SearchIndexDocumentMongoEncoder}), reads via the real
 * {@code MongoThingsSearchPersistence} + {@code MongoQueryBuilderFactory}. All wired classes are MAIN classes of
 * {@code ditto-thingsearch-service} (a documented test-scope dependency of this module); the minimal wiring mirrors
 * that module's {@code AbstractThingSearchPersistenceITBase}/{@code TestSearchUpdaterStream} (which are test classes
 * and therefore not consumable here).
 */
final class MongoParityBackend implements ParityBackend {

    /**
     * The Mongo max wire version selecting the incremental-update representation; 13 mirrors
     * thingsearch/service's own test wiring ({@code TestSearchUpdaterStream}).
     */
    private static final int MAX_WIRE_VERSION = 13;

    private final DittoMongoClient mongoClient;
    private final MongoThingsSearchPersistence persistence;
    private final MongoSearchUpdaterFlow updaterFlow;
    private final MongoQueryBuilderFactory queryBuilderFactory;

    private MongoParityBackend(final DittoMongoClient mongoClient,
            final MongoThingsSearchPersistence persistence,
            final MongoSearchUpdaterFlow updaterFlow,
            final MongoQueryBuilderFactory queryBuilderFactory) {
        this.mongoClient = mongoClient;
        this.persistence = persistence;
        this.updaterFlow = updaterFlow;
        this.queryBuilderFactory = queryBuilderFactory;
    }

    static MongoParityBackend start(final MongoDbResource mongoResource, final ActorSystem system,
            final Config config) {

        final DittoMongoClient client = MongoClientWrapper.getBuilder()
                .connectionString("mongodb://" + mongoResource.getBindIp() + ":" + mongoResource.getPort()
                        + "/searchParityIT")
                .connectionPoolMaxSize(20)
                .connectionPoolMaxWaitTime(Duration.ofSeconds(30))
                .build();

        final var persistenceConfig =
                org.eclipse.ditto.thingsearch.service.common.config.DefaultSearchPersistenceConfig.of(
                        ConfigFactory.empty());
        final var searchConfig = DittoSearchConfig.of(DefaultScopedConfig.dittoScoped(config));
        final var persistence = new MongoThingsSearchPersistence(client, system, persistenceConfig, searchConfig);
        // create the production indices (incl. the wildcard index) before any data is written, as the service does
        persistence.initializeIndices(searchConfig.getIndexInitializationConfig()).toCompletableFuture().join();

        final var flow = MongoSearchUpdaterFlow.of(client.getDefaultDatabase(),
                DefaultPersistenceStreamConfig.of(ConfigFactory.empty()), MAX_WIRE_VERSION);
        final var qbf = new MongoQueryBuilderFactory(DefaultLimitsConfig.of(config.getConfig("ditto")));
        return new MongoParityBackend(client, persistence, flow, qbf);
    }

    @Override
    public String name() {
        return "mongo";
    }

    @Override
    public QueryBuilderFactory queryBuilderFactory() {
        return queryBuilderFactory;
    }

    @Override
    public ThingsSearchPersistence searchPersistence() {
        return persistence;
    }

    @Override
    public SearchUpdaterFlow updaterFlow() {
        return updaterFlow;
    }

    @Override
    public void close() {
        mongoClient.close();
    }
}
