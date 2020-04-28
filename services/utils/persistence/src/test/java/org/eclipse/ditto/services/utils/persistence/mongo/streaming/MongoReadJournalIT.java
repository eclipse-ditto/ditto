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
package org.eclipse.ditto.services.utils.persistence.mongo.streaming;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import org.bson.Document;
import org.eclipse.ditto.services.utils.persistence.mongo.DittoMongoClient;
import org.eclipse.ditto.services.utils.persistence.mongo.MongoClientWrapper;
import org.eclipse.ditto.services.utils.test.mongo.MongoDbResource;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;

import akka.actor.ActorSystem;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.testkit.javadsl.TestKit;

/**
 * Tests {@link org.eclipse.ditto.services.utils.persistence.mongo.streaming.MongoReadJournal}.
 * CAUTION: Do not use Akka streams testkit; it does not work for Source.fromPublisher against reactive-streams client.
 */
public final class MongoReadJournalIT {

    private static final String MONGO_HOST = "localhost";
    private static final String MONGO_DB = "mongoReadJournalIT";

    private static MongoDbResource mongoResource;
    private static DittoMongoClient mongoClient;

    private ActorSystem actorSystem;
    private ActorMaterializer materializer;
    private MongoReadJournal readJournal;

    @BeforeClass
    public static void startMongoResource() {
        mongoResource = new MongoDbResource(MONGO_HOST);
        mongoResource.start();
        mongoClient = MongoClientWrapper.getBuilder()
                .hostnameAndPort(mongoResource.getBindIp(), mongoResource.getPort())
                .defaultDatabaseName(MONGO_DB)
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
        // set persistence plugin Mongo URI for JavaDslReadJournal test
        final String mongoUri = String.format("mongodb://%s:%d/%s", MONGO_HOST, mongoResource.getPort(), MONGO_DB);
        final Config config = ConfigFactory.load("mongo-read-journal-test")
                .withValue("akka.contrib.persistence.mongodb.mongo.mongouri", ConfigValueFactory.fromAnyRef(mongoUri));
        actorSystem = ActorSystem.create("AkkaTestSystem", config);
        materializer = ActorMaterializer.create(actorSystem);
        readJournal = MongoReadJournal.newInstance(config, mongoClient);
    }

    @After
    public void after() {
        if (null != mongoClient) {
            Source.fromPublisher(mongoClient.getDefaultDatabase().drop())
                    .runWith(Sink.ignore(), materializer)
                    .toCompletableFuture()
                    .join();
        }
        if (null != actorSystem) {
            TestKit.shutdownActorSystem(actorSystem);
        }
    }

    @Test
    public void streamLatestSnapshotsWithBatchCutOffWithinSamePid() {
        // GIVEN
        insert("test_snaps", new Document()
                .append("pid", "pid1")
                .append("sn", 1L)
                .append("s2", new Document().append("_modified", "2020-02-29T23:59:59.999Z"))
        );
        insert("test_snaps", new Document()
                .append("pid", "pid3")
                .append("sn", 1L)
                .append("s2", new Document().append("_modified", "1999-01-01:00:00:00.000Z"))
        );
        insert("test_snaps", new Document()
                .append("pid", "pid3")
                .append("sn", 2L)
                .append("s2", new Document().append("_modified", "2000-01-01T00:01:01.000Z"))
        );
        insert("test_snaps", new Document()
                .append("pid", "pid3")
                .append("sn", 3L)
                .append("s2", new Document().append("_modified", "2020-01-31T19:57:48.571Z"))
        );
        insert("test_snaps", new Document()
                .append("pid", "pid4")
                .append("sn", 4L)
                .append("s2", new Document().append("_modified", "1970-01-01T00:00:00.000Z"))
        );
        // latest snapshot of pid5 is deleted; it should not show up
        insert("test_snaps", new Document()
                .append("pid", "pid5")
                .append("sn", 4L)
                .append("s2", new Document().append("_modified", "1970-01-01T00:00:00.001Z"))
        );
        insert("test_snaps", new Document()
                .append("pid", "pid5")
                .append("sn", 5L)
                .append("s2", new Document()
                        .append("_modified", "1970-01-01T00:00:00.002Z")
                        .append("__lifecycle", "DELETED")
                )
        );

        // WHEN: latest snapshots requested with batch size that splits the snapshots of pid3 into 2 batches
        final List<Document> snapshots =
                readJournal.getNewestSnapshotsAbove("pid2", 2, materializer, "_modified")
                        .runWith(Sink.seq(), materializer)
                        .toCompletableFuture()
                        .join();

        // THEN: snapshots of the highest sequence number for each pid are returned
        assertThat(snapshots).containsExactly(
                new Document().append("_id", "pid3")
                        .append("__lifecycle", null)
                        .append("_modified", "2020-01-31T19:57:48.571Z"),
                new Document().append("_id", "pid4")
                        .append("__lifecycle", null)
                        .append("_modified", "1970-01-01T00:00:00.000Z")
        );
    }

    @Test
    public void streamLatestSnapshotsWithDeletedSnapshots() {
        // GIVEN
        // pid1: deleted
        insert("test_snaps", new Document()
                .append("pid", "pid1")
                .append("sn", 1L)
                .append("s2", new Document()
                        .append("_modified", "2020-02-29T23:59:59.999Z")
                        .append("__lifecycle", "DELETED")
                )
        );
        // pid2: deleted as final snapshot
        insert("test_snaps", new Document()
                .append("pid", "pid2")
                .append("sn", 1L)
                .append("s2", new Document().append("_modified", "1999-01-01:00:00:00.001Z"))
        );
        insert("test_snaps", new Document()
                .append("pid", "pid2")
                .append("sn", 2L)
                .append("s2", new Document()
                        .append("_modified", "1999-01-01:00:00:00.000Z")
                        .append("__lifecycle", "DELETED")
                )
        );
        // pid3: non-deleted as final snapshot
        insert("test_snaps", new Document()
                .append("pid", "pid3")
                .append("sn", 2L)
                .append("s2", new Document()
                        .append("_modified", "2000-01-01T00:01:01.000Z")
                        .append("__lifecycle", "DELETED")
                )
        );
        insert("test_snaps", new Document()
                .append("pid", "pid3")
                .append("sn", 3L)
                .append("s2", new Document().append("_modified", "2020-01-31T19:57:48.571Z"))
        );

        // WHEN: latest snapshots requested with batch size 2
        final List<Document> snapshots =
                readJournal.getNewestSnapshotsAbove("", 2, materializer, "_modified")
                        .runWith(Sink.seq(), materializer)
                        .toCompletableFuture()
                        .join();

        // THEN: pid3 should be returned.
        assertThat(snapshots).containsExactly(
                new Document().append("_id", "pid3")
                        .append("__lifecycle", null)
                        .append("_modified", "2020-01-31T19:57:48.571Z")
        );

    }

    @Test
    public void extractJournalPidsFromEventsAndNotSnapshots() {
        insert("test_journal", new Document().append("pid", "pid3").append("to", 2L));
        insert("test_journal", new Document().append("pid", "pid4").append("to", 2L));
        insert("test_journal", new Document().append("pid", "pid1").append("to", 1L));
        insert("test_journal", new Document().append("pid", "pid2").append("to", 1L));
        insert("test_snaps", new Document().append("pid", "pid5").append("sn", 3L));
        insert("test_snaps", new Document().append("pid", "pid6").append("sn", 4L));

        final List<String> pids =
                readJournal.getJournalPids(2, Duration.ZERO, materializer)
                        .runWith(Sink.seq(), materializer)
                        .toCompletableFuture().join();

        assertThat(pids).containsExactly("pid1", "pid2", "pid3", "pid4");
    }

    @Test
    public void extractJournalPidsAboveALowerBound() {
        insert("test_journal", new Document().append("pid", "pid1").append("to", 1L));
        insert("test_journal", new Document().append("pid", "pid2").append("to", 1L));
        insert("test_journal", new Document().append("pid", "pid3").append("to", 2L));
        insert("test_journal", new Document().append("pid", "pid4").append("to", 2L));

        final List<String> pids =
                readJournal.getJournalPidsAbove("pid2", 2, materializer)
                        .runWith(Sink.seq(), materializer)
                        .toCompletableFuture().join();

        assertThat(pids).containsExactly("pid3", "pid4");
    }

    private void insert(final String collection, final Document... documents) {
        Source.fromPublisher(mongoClient.getCollection(collection).insertMany(Arrays.asList(documents)))
                .runWith(Sink.ignore(), materializer)
                .toCompletableFuture()
                .join();
    }
}
