/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;

import org.bson.Document;
import org.eclipse.ditto.base.service.config.limits.DefaultLimitsConfig;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.DittoMongoClient;
import org.eclipse.ditto.internal.utils.persistence.mongo.MongoClientWrapper;
import org.eclipse.ditto.internal.utils.test.mongo.MongoDbResource;
import org.eclipse.ditto.rql.query.Query;
import org.eclipse.ditto.rql.query.QueryBuilderFactory;
import org.eclipse.ditto.rql.query.criteria.CriteriaFactory;
import org.eclipse.ditto.rql.query.expression.FieldExpressionUtil;
import org.eclipse.ditto.rql.query.expression.ThingsFieldExpressionFactory;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.thingsearch.service.common.config.DefaultSearchPersistenceConfig;
import org.eclipse.ditto.thingsearch.service.common.model.ResultList;
import org.eclipse.ditto.thingsearch.service.persistence.read.MongoThingsSearchPersistence;
import org.eclipse.ditto.thingsearch.service.persistence.read.query.MongoQueryBuilderFactory;
import org.eclipse.ditto.thingsearch.service.persistence.write.streaming.SearchUpdateMapper;
import org.eclipse.ditto.thingsearch.service.persistence.write.streaming.TestSearchUpdaterStream;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;

import com.mongodb.reactivestreams.client.MongoCollection;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.event.LoggingAdapter;
import akka.stream.Materializer;
import akka.stream.SystemMaterializer;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.testkit.javadsl.TestKit;


/**
 * Abstract base class for search persistence tests.
 */
public abstract class AbstractThingSearchPersistenceITBase {

    @ClassRule
    public static final MongoDbResource MONGO_RESOURCE = new MongoDbResource();

    protected static final List<String> KNOWN_SUBJECTS = Collections.singletonList("abc:mySid");

    private static final Map<String, String> mongoSimpleFieldMappings = new HashMap<>();

    static {
        mongoSimpleFieldMappings.put(FieldExpressionUtil.FIELD_NAME_THING_ID, FieldExpressionUtil.FIELD_ID);
        mongoSimpleFieldMappings.put(FieldExpressionUtil.FIELD_NAME_NAMESPACE, FieldExpressionUtil.FIELD_NAMESPACE);
    }

    protected static final CriteriaFactory cf = CriteriaFactory.getInstance();
    protected static final ThingsFieldExpressionFactory fef = ThingsFieldExpressionFactory.of(mongoSimpleFieldMappings);

    protected static QueryBuilderFactory qbf;

    protected static DittoMongoClient mongoClient;

    protected MongoCollection<Document> thingsCollection;
    protected MongoThingsSearchPersistence readPersistence;
    protected TestSearchUpdaterStream writePersistence;

    protected ActorSystem actorSystem;
    protected LoggingAdapter log;

    @BeforeClass
    public static void startMongoResource() {
        final Config rawTestConfig = ConfigFactory.load("test");
        final DefaultLimitsConfig limitsConfig = DefaultLimitsConfig.of(rawTestConfig.getConfig("ditto"));
        qbf = new MongoQueryBuilderFactory(limitsConfig);
        mongoClient = provideClientWrapper();
    }

    @Before
    public void before() {
        final Config config = ConfigFactory.load("test");
        actorSystem = ActorSystem.create("AkkaTestSystem", config);
        log = actorSystem.log();
        readPersistence = provideReadPersistence();
        writePersistence = provideWritePersistence();
        thingsCollection = mongoClient.getDefaultDatabase().getCollection(PersistenceConstants.THINGS_COLLECTION_NAME);
    }

    private MongoThingsSearchPersistence provideReadPersistence() {
        final var config = DefaultSearchPersistenceConfig.of(ConfigFactory.empty());
        final MongoThingsSearchPersistence result = new MongoThingsSearchPersistence(mongoClient, actorSystem, config);
        // explicitly trigger CompletableFuture to make sure that indices are created before test runs
        result.initializeIndices().toCompletableFuture().join();

        return result;
    }

    private TestSearchUpdaterStream provideWritePersistence() {
        final var dittoExtensionsConfig = ScopedConfig.dittoExtension(actorSystem.settings().config());
        return TestSearchUpdaterStream.of(mongoClient.getDefaultDatabase(),
                SearchUpdateMapper.get(actorSystem, dittoExtensionsConfig));
    }

    private static DittoMongoClient provideClientWrapper() {
        return MongoClientWrapper.getBuilder()
                .connectionString(
                        "mongodb://" + MONGO_RESOURCE.getBindIp() + ":" + MONGO_RESOURCE.getPort() + "/testSearchDB")
                .connectionPoolMaxSize(100)
                .connectionPoolMaxWaitTime(Duration.ofSeconds(30))
                .build();
    }


    @After
    public void after() {
        if (mongoClient != null) {
            dropCollections(List.of(thingsCollection));
        }
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem);
            actorSystem = null;
            log = null;
            actorSystem = null;
        }
    }

    private void dropCollections(final List<MongoCollection<Document>> collections) {
        collections.stream()
                .filter(Objects::nonNull)
                .forEach(this::dropCollectionWithBackoff);
    }

    private void dropCollectionWithBackoff(final MongoCollection<Document> collection) {
        RuntimeException lastException = null;
        for (int i = 0; i < 20; ++i) {
            try {
                waitFor(Source.fromPublisher(collection.drop()));
                return;
            } catch (final RuntimeException e) {
                lastException = e;
                backoff();
            }
        }
        throw lastException;
    }

    @AfterClass
    public static void stopMongoResource() {
        try {
            if (mongoClient != null) {
                mongoClient.close();
            }
        } catch (final IllegalStateException e) {
            System.err.println("IllegalStateException during shutdown of MongoDB: " + e.getMessage());
        }
    }

    protected Long count(final Query query) {
        return count(query, KNOWN_SUBJECTS);
    }

    protected Long count(final Query query, @Nullable final List<String> subjectIds) {
        return readPersistence.count(query, subjectIds)
                .runWith(Sink.head(), actorSystem)
                .toCompletableFuture()
                .join();
    }

    protected ResultList<ThingId> findAll(final Query query) {
        return findAll(query, KNOWN_SUBJECTS);
    }

    protected ResultList<ThingId> findAll(final Query query, final List<String> subjectIds) {
        return readPersistence.findAll(query, subjectIds)
                .runWith(Sink.head(), actorSystem)
                .toCompletableFuture()
                .join();
    }

    protected <T> T runBlockingWithReturn(final Source<T, NotUsed> publisher) {
        final CompletionStage<T> done = publisher.runWith(Sink.last(), actorSystem);

        return done.toCompletableFuture().join();
    }

    protected final DittoMongoClient getClient() {
        return mongoClient;
    }

    final Materializer getMaterializer() {
        return SystemMaterializer.get(actorSystem).materializer();
    }

    protected <T> List<T> waitFor(final Source<T, ?> source) {
        return source.runWith(Sink.seq(), actorSystem)
                .toCompletableFuture()
                .join();
    }

    @SuppressWarnings("squid:S2925")
    private void backoff() {
        try {
            Thread.sleep(100);
        } catch (final Exception e) {
            // do nothing
        }
    }

}
