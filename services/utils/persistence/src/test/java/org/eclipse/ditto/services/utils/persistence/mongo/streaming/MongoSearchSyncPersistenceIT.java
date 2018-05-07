package org.eclipse.ditto.services.utils.persistence.mongo.streaming;
/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import org.eclipse.ditto.services.utils.persistence.mongo.MongoClientWrapper;
import org.eclipse.ditto.services.utils.test.mongo.MongoDbResource;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.mongodb.reactivestreams.client.MongoClient;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorSystem;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.testkit.javadsl.TestKit;

/**
 * Tests {@link MongoSearchSyncPersistence}.
 */
public final class MongoSearchSyncPersistenceIT {

    private static MongoDbResource mongoResource;
    private static MongoClientWrapper mongoClient;
    private static final String KNOWN_COLLECTION = "knownCollection";

    private ActorSystem actorSystem;
    private ActorMaterializer materializer;
    private MongoSearchSyncPersistence syncPersistence;

    @BeforeClass
    public static void startMongoResource() {
        mongoResource = new MongoDbResource("localhost");
        mongoResource.start();
        mongoClient = MongoClientWrapper.newInstance(mongoResource.getBindIp(), mongoResource.getPort(), "testSearchDB",
                100, 500000, 30);
    }

    @AfterClass
    public static void stopMongoResource() {
        try {
            Optional.ofNullable(mongoClient)
                    .map(MongoClientWrapper::getMongoClient)
                    .ifPresent(MongoClient::close);
            Optional.ofNullable(mongoResource)
                    .ifPresent(MongoDbResource::stop);
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
        syncPersistence = MongoSearchSyncPersistence.initializedInstance(KNOWN_COLLECTION, mongoClient, materializer);
    }


    /** */
    @After
    public void after() {
        if (null != mongoClient) {
            runBlocking(Source.fromPublisher(mongoClient.getDatabase().getCollection(KNOWN_COLLECTION).drop()));
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
        final Optional<Instant> actualTs = syncPersistence.retrieveLastSuccessfulStreamEnd();

        assertThat(actualTs).isEmpty();
    }

    /**
     * Checks updating and retrieving the timestamp afterwards.
     */
    @Test
    public void updateAndRetrieveLastSuccessfulSyncTimestamp() {
        final Instant ts = Instant.now();

        runBlocking(syncPersistence.updateLastSuccessfulStreamEnd(ts));

        final Optional<Instant> persistedTs = syncPersistence.retrieveLastSuccessfulStreamEnd();
        assertThat(persistedTs).hasValue(ts);
    }

    private void runBlocking(final Source<?, ?>... publishers) {
        Stream.of(publishers)
                .map(p -> p.runWith(Sink.ignore(), materializer))
                .map(CompletionStage::toCompletableFuture)
                .forEach(this::finishCompletableFuture);
    }

    private void finishCompletableFuture(final CompletableFuture future) {
        try {
            future.get();
        } catch (final InterruptedException | ExecutionException e) {
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

