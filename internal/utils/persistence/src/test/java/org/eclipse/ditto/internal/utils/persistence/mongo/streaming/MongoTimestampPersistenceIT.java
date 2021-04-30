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
package org.eclipse.ditto.internal.utils.persistence.mongo.streaming;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.bson.Document;
import org.eclipse.ditto.internal.utils.persistence.mongo.DittoMongoClient;
import org.eclipse.ditto.internal.utils.persistence.mongo.MongoClientWrapper;
import org.eclipse.ditto.internal.utils.test.mongo.MongoDbResource;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import com.mongodb.reactivestreams.client.MongoCollection;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorSystem;
import akka.stream.Materializer;
import akka.stream.SystemMaterializer;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.testkit.javadsl.TestKit;

/**
 * Tests {@link MongoTimestampPersistence}.
 */
public final class MongoTimestampPersistenceIT {

    @ClassRule
    public static final MongoDbResource MONGO_RESOURCE = new MongoDbResource();
    private static DittoMongoClient mongoClient;
    private static final String KNOWN_COLLECTION = "knownCollection";


    private ActorSystem actorSystem;
    private Materializer materializer;
    private MongoTimestampPersistence syncPersistence;

    @BeforeClass
    public static void startMongoResource() {
        mongoClient = MongoClientWrapper.getBuilder()
                .hostnameAndPort(MONGO_RESOURCE.getBindIp(), MONGO_RESOURCE.getPort())
                .defaultDatabaseName("mongoTimestampPersistenceIT")
                .connectionPoolMaxSize(100)
                .connectionPoolMaxWaitTime(Duration.ofSeconds(30))
                .build();
    }

    @AfterClass
    public static void stopMongoResource() {
        try {
            if (null != mongoClient) {
                mongoClient.close();
            }
        } catch (final IllegalStateException e) {
            System.err.println("IllegalStateException during shutdown of MongoDB: " + e.getMessage());
        }
    }

    @Before
    public void setUp() {
        final Config config = ConfigFactory.load("test");
        actorSystem = ActorSystem.create("AkkaTestSystem", config);
        materializer = SystemMaterializer.get(actorSystem).materializer();
        syncPersistence = MongoTimestampPersistence.initializedInstance(KNOWN_COLLECTION, mongoClient, materializer);
    }

    @After
    public void after() {
        if (null != mongoClient) {
            runBlocking(Source.fromPublisher(mongoClient.getCollection(KNOWN_COLLECTION).drop()));
        }
        if (null != actorSystem) {
            TestKit.shutdownActorSystem(actorSystem);
        }
    }

    /**
     * Checks that an empty {@link Optional} is returned when the timestamp has not yet been persisted.
     */
    @Test
    public void retrieveFallbackForLastSuccessfulSyncTimestamp() {
        final Optional<Instant> actualTs = getResult(syncPersistence.getTimestampAsync());

        assertThat(actualTs).isEmpty();
    }

    /**
     * Checks updating and retrieving the timestamp afterwards.
     */
    @Test
    public void updateAndRetrieveLastSuccessfulSyncTimestamp() {
        final Instant ts = Instant.now();

        runBlocking(syncPersistence.setTimestamp(ts));

        final Optional<Instant> persistedTs = getResult(syncPersistence.getTimestampAsync());
        assertThat(persistedTs).hasValue(ts.truncatedTo(ChronoUnit.MILLIS));
    }

    @Test
    public void ensureCollectionIsCapped() throws Exception {
        final MongoCollection<Document> collection =
                syncPersistence.getCollection().runWith(Sink.head(), materializer).toCompletableFuture().get();

        runBlocking(syncPersistence.setTimestamp(Instant.now()));
        runBlocking(syncPersistence.setTimestamp(Instant.now()));

        assertThat(runBlocking(Source.fromPublisher(collection.countDocuments()))).containsExactly(1L);
    }

    @Test
    public void createCollectionMultipleTimesWithoutError() {
        final MongoTimestampPersistence persistence1 =
                MongoTimestampPersistence.initializedInstance(KNOWN_COLLECTION, mongoClient, materializer);
        final MongoTimestampPersistence persistence2 =
                MongoTimestampPersistence.initializedInstance(KNOWN_COLLECTION, mongoClient, materializer);

        runBlocking(syncPersistence.getCollection()
                .flatMapConcat(d -> persistence1.getCollection())
                .flatMapConcat(d -> persistence2.getCollection()));
    }

    private <T> T getResult(final Source<T, ?> source) {
        final CompletableFuture<T> future = source.runWith(Sink.head(), materializer).toCompletableFuture();
        finishCompletableFuture(future);
        return future.join();
    }

    private <T> List<T> runBlocking(final Source<T, ?> publisher) {
        return finishCompletableFuture(publisher.runWith(Sink.seq(), materializer).toCompletableFuture());
    }

    private <T> T finishCompletableFuture(final CompletableFuture<T> future) {
        try {
            return future.get();
        } catch (final Exception e) {
            throw mapAsRuntimeException(e);
        }
    }

    private static RuntimeException mapAsRuntimeException(final Throwable t) {
        // shortcut: RTEs can be returned as-is
        if (t instanceof RuntimeException) {
            return (RuntimeException) t;
        }

        // for ExecutionExceptions, extract the cause
        if (t instanceof ExecutionException && t.getCause() != null) {
            return mapAsRuntimeException(t.getCause());
        }

        // wrap non-RTEs as IllegalStateException
        return new IllegalStateException(t);
    }

}
