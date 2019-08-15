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
package org.eclipse.ditto.services.thingsearch.persistence;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;

import org.bson.Document;
import org.eclipse.ditto.model.query.Query;
import org.eclipse.ditto.model.query.QueryBuilderFactory;
import org.eclipse.ditto.model.query.criteria.CriteriaFactory;
import org.eclipse.ditto.model.query.criteria.CriteriaFactoryImpl;
import org.eclipse.ditto.model.query.expression.ThingsFieldExpressionFactory;
import org.eclipse.ditto.model.query.expression.ThingsFieldExpressionFactoryImpl;
import org.eclipse.ditto.services.base.config.limits.DefaultLimitsConfig;
import org.eclipse.ditto.services.thingsearch.common.model.ResultList;
import org.eclipse.ditto.services.thingsearch.persistence.read.MongoThingsSearchPersistence;
import org.eclipse.ditto.services.thingsearch.persistence.read.query.MongoQueryBuilderFactory;
import org.eclipse.ditto.services.thingsearch.persistence.write.streaming.TestSearchUpdaterStream;
import org.eclipse.ditto.services.utils.persistence.mongo.DittoMongoClient;
import org.eclipse.ditto.services.utils.persistence.mongo.MongoClientWrapper;
import org.eclipse.ditto.services.utils.test.mongo.MongoDbResource;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

import com.mongodb.reactivestreams.client.MongoCollection;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.event.LoggingAdapter;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.testkit.javadsl.TestKit;


/**
 * Abstract base class for search persistence tests.
 */
public abstract class AbstractThingSearchPersistenceITBase {

    protected static final List<String> KNOWN_SUBJECTS = Collections.singletonList("abc:mySid");

    protected static final CriteriaFactory cf = new CriteriaFactoryImpl();
    protected static final ThingsFieldExpressionFactory fef = new ThingsFieldExpressionFactoryImpl();

    protected static QueryBuilderFactory qbf;

    private static MongoDbResource mongoResource;
    private static DittoMongoClient mongoClient;

    private MongoThingsSearchPersistence readPersistence;
    private MongoCollection<Document> thingsCollection;
    private MongoCollection<Document> syncCollection;
    protected TestSearchUpdaterStream writePersistence;

    private ActorSystem actorSystem;
    private ActorMaterializer actorMaterializer;
    protected LoggingAdapter log;

    @BeforeClass
    public static void startMongoResource() {
        final Config rawTestConfig = ConfigFactory.load("test");
        final DefaultLimitsConfig limitsConfig = DefaultLimitsConfig.of(rawTestConfig.getConfig("ditto"));
        qbf = new MongoQueryBuilderFactory(limitsConfig);

        mongoResource = new MongoDbResource("localhost");
        mongoResource.start();
        mongoClient = provideClientWrapper();
    }

    @Before
    public void before() {
        final Config config = ConfigFactory.load("test");
        actorSystem = ActorSystem.create("AkkaTestSystem", config);
        log = actorSystem.log();
        actorMaterializer = ActorMaterializer.create(actorSystem);
        readPersistence = provideReadPersistence();
        writePersistence = provideWritePersistence();
        thingsCollection = mongoClient.getDefaultDatabase().getCollection(PersistenceConstants.THINGS_COLLECTION_NAME);
        syncCollection =
                mongoClient.getDefaultDatabase().getCollection(PersistenceConstants.THINGS_SYNC_STATE_COLLECTION_NAME);
    }

    private MongoThingsSearchPersistence provideReadPersistence() {
        final MongoThingsSearchPersistence result = new MongoThingsSearchPersistence(mongoClient, actorSystem);
        // explicitly trigger CompletableFuture to make sure that indices are created before test runs
        result.initializeIndices().toCompletableFuture().join();
        return result;
    }

    private TestSearchUpdaterStream provideWritePersistence() {
        return TestSearchUpdaterStream.of(mongoClient.getDefaultDatabase());
    }

    private static DittoMongoClient provideClientWrapper() {
        return MongoClientWrapper.getBuilder()
                .connectionString(
                        "mongodb://" + mongoResource.getBindIp() + ":" + mongoResource.getPort() + "/testSearchDB")
                .connectionPoolMaxSize(100)
                .connectionPoolMaxWaitQueueSize(500000)
                .connectionPoolMaxWaitTime(Duration.ofSeconds(30))
                .build();
    }


    @After
    public void after() {
        if (mongoClient != null) {
            dropCollections(Arrays.asList(thingsCollection, syncCollection));
        }
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem);
            actorSystem = null;
            log = null;
            actorMaterializer = null;
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
            if (mongoResource != null) {
                mongoResource.stop();
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
                .runWith(Sink.head(), actorMaterializer)
                .toCompletableFuture()
                .join();
    }

    protected ResultList<String> findAll(final Query query) {
        return findAll(query, KNOWN_SUBJECTS);
    }

    protected ResultList<String> findAll(final Query query, final List<String> subjectIds) {
        return readPersistence.findAll(query, subjectIds)
                .runWith(Sink.head(), actorMaterializer)
                .toCompletableFuture()
                .join();
    }

    protected <T> T runBlockingWithReturn(final Source<T, NotUsed> publisher) {
        final CompletionStage<T> done = publisher.runWith(Sink.last(), actorMaterializer);
        return done.toCompletableFuture().join();
    }

    protected final DittoMongoClient getClient() {
        return mongoClient;
    }

    final Materializer getMaterializer() {
        return actorMaterializer;
    }

    private <T> List<T> waitFor(final Source<T, ?> source) {
        return source.runWith(Sink.seq(), actorMaterializer)
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
