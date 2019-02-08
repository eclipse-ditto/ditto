package org.eclipse.ditto.services.utils.persistence.mongo.streaming;
/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.bson.Document;
import org.eclipse.ditto.services.utils.persistence.mongo.DittoMongoClient;
import org.eclipse.ditto.services.utils.persistence.mongo.MongoClientWrapper;
import org.eclipse.ditto.services.utils.test.mongo.MongoDbResource;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.mongodb.reactivestreams.client.MongoCollection;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorSystem;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.testkit.javadsl.TestKit;

/**
 * Tests {@link MongoTimestampPersistence}.
 */
public final class MongoTimestampPersistenceIT {

    private static MongoDbResource mongoResource;
    private static DittoMongoClient mongoClient;
    private static final String KNOWN_COLLECTION = "knownCollection";

    private ActorSystem actorSystem;
    private ActorMaterializer materializer;
    private MongoTimestampPersistence syncPersistence;

    @BeforeClass
    public static void startMongoResource() {
        mongoResource = new MongoDbResource("localhost");
        mongoResource.start();
        mongoClient = MongoClientWrapper.getBuilder()
                .hostnameAndPort(mongoResource.getBindIp(), mongoResource.getPort())
                .defaultDatabaseName("testSearchDB")
                .connectionPoolMaxSize(100)
                .connectionPoolMaxWaitQueueSize(500_000)
                .connectionPoolMaxWaitTime(Duration.ofSeconds(30))
                .build();
    }

    @AfterClass
    public static void stopMongoResource() {
        try {
            if (null != mongoClient) {
                mongoClient.close();
            }
            if (null != mongoResource) {
                mongoResource.stop();
            }
        } catch (final IllegalStateException e) {
            System.err.println("IllegalStateException during shutdown of MongoDB: " + e.getMessage());
        }
    }

    @Before
    public void setUp() {
        final Config config = ConfigFactory.load("test");
        actorSystem = ActorSystem.create("AkkaTestSystem", config);
        actorSystem = ActorSystem.create("actors");
        materializer = ActorMaterializer.create(actorSystem);
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
        assertThat(persistedTs).hasValue(ts);
    }

    @Test
    public void ensureCollectionIsCapped() throws Exception {
        final MongoCollection<Document> collection =
                syncPersistence.getCollection().runWith(Sink.head(), materializer).toCompletableFuture().get();

        runBlocking(syncPersistence.setTimestamp(Instant.now()));
        runBlocking(syncPersistence.setTimestamp(Instant.now()));

        assertThat(runBlocking(Source.fromPublisher(collection.count()))).containsExactly(1L);
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
