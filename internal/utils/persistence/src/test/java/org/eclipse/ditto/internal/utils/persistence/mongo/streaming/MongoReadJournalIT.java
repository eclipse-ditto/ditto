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
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonInt64;
import org.bson.BsonString;
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

import com.mongodb.client.result.DeleteResult;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;

import akka.actor.ActorSystem;
import akka.stream.Materializer;
import akka.stream.SystemMaterializer;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.testkit.javadsl.TestKit;

/**
 * Tests {@link MongoReadJournal}.
 * CAUTION: Do not use Akka streams testkit; it does not work for Source.fromPublisher against reactive-streams client.
 */
public final class MongoReadJournalIT {

    private static final String MONGO_DB = "mongoReadJournalIT";

    @ClassRule
    public static final MongoDbResource MONGO_RESOURCE = new MongoDbResource();
    private static DittoMongoClient mongoClient;

    private ActorSystem actorSystem;
    private Materializer materializer;
    private MongoReadJournal readJournal;

    @BeforeClass
    public static void startMongoResource() {
        mongoClient = MongoClientWrapper.getBuilder()
                .hostnameAndPort(MONGO_RESOURCE.getBindIp(), MONGO_RESOURCE.getPort())
                .defaultDatabaseName(MONGO_DB)
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
        // set persistence plugin Mongo URI for JavaDslReadJournal test
        final String mongoUri =
                String.format("mongodb://%s:%d/%s", MONGO_RESOURCE.getBindIp(), MONGO_RESOURCE.getPort(), MONGO_DB);
        final Config config = ConfigFactory.load("mongo-read-journal-test")
                .withValue("akka.contrib.persistence.mongodb.mongo.mongouri", ConfigValueFactory.fromAnyRef(mongoUri));
        actorSystem = ActorSystem.create("AkkaTestSystem", config);
        materializer = SystemMaterializer.get(actorSystem).materializer();
        readJournal = MongoReadJournal.newInstance(config, mongoClient, actorSystem);
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
                        .append("_modified", "2020-01-31T19:57:48.571Z")
                        .append("sn", 3L),
                new Document().append("_id", "pid4")
                        .append("__lifecycle", null)
                        .append("_modified", "1970-01-01T00:00:00.000Z")
                        .append("sn", 4L)
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
                        .append("sn", 3L)
        );

    }

    private static Document snapshot(final String pid, final long sn, final boolean deleted) {
        return new Document().append("pid", pid)
                            .append("sn", sn)
                            .append("s2", new Document().append("_modified", Instant.ofEpochSecond(sn).toString())
                                    .append("__lifecycle", deleted  ? "DELETED"  :"ACTIVE"));
    }

    @Test
    public void streamLatestSnapshotsWithPidFilter() {
        // GIVEN
        insert("test_snaps", snapshot("snip:pid1", 1L, false));
        insert("test_snaps", snapshot("snap:pid1", 1L, false));
        insert("test_snaps", snapshot("snap:pid1", 2L, false));
        insert("test_snaps", snapshot("snap:pid1", 3L, false));
        insert("test_snaps", snapshot("snip:pid3", 1L, false));
        insert("test_snaps", snapshot("snap:pid4", 4L, false));

        // latest snapshot of pid5 is deleted; it should not show up
        insert("test_snaps", snapshot("snap:pid5", 1L, false));
        insert("test_snaps", snapshot("snap:pid5", 2L, true));

        insert("test_snaps", snapshot("snap:pid6", 6L, false));


        // WHEN: latest snapshots requested with batch size that splits the snapshots of pid3 into 2 batches
        final List<Document> snapshots =
                readJournal.getNewestSnapshotsAbove(SnapshotFilter.of("", "^snap:.*"), 2, materializer, "_modified")
                        .runWith(Sink.seq(), materializer)
                        .toCompletableFuture()
                        .join();

        // THEN: snapshots of the highest sequence number for each pid are returned
        assertThat(snapshots).containsExactly(
                new Document().append("_id", "snap:pid1")
                        .append("__lifecycle", "ACTIVE")
                        .append("_modified", "1970-01-01T00:00:03Z")
                        .append("sn", 3L),
                new Document().append("_id", "snap:pid4")
                        .append("__lifecycle", "ACTIVE")
                        .append("_modified", "1970-01-01T00:00:04Z")
                        .append("sn", 4L),
                new Document().append("_id", "snap:pid6")
                        .append("__lifecycle", "ACTIVE")
                        .append("_modified", "1970-01-01T00:00:06Z")
                        .append("sn", 6L)
        );
    }

    @Test
    public void streamLatestSnapshotsWithLowerBoundAndPidFilter() {
        // GIVEN
        insert("test_snaps", snapshot("snip:pid1", 1L, false));
        insert("test_snaps", snapshot("snap:pid1", 1L, false));
        insert("test_snaps", snapshot("snap:pid1", 2L, false));
        insert("test_snaps", snapshot("snap:pid1", 3L, false));
        insert("test_snaps", snapshot("snip:pid3", 1L, false));
        insert("test_snaps", snapshot("snap:pid4", 4L, false));

        // latest snapshot of pid5 is deleted; it should not show up
        insert("test_snaps", snapshot("snap:pid5", 1L, false));
        insert("test_snaps", snapshot("snap:pid5", 2L, true));

        insert("test_snaps", snapshot("snap:pid6", 6L, false));

        // WHEN: latest snapshots requested lower bound and pid filter
        final List<Document> snapshots =
                readJournal.getNewestSnapshotsAbove(SnapshotFilter.of("snap:pid2", "^snap:.*"), 2, materializer, "_modified")
                        .runWith(Sink.seq(), materializer)
                        .toCompletableFuture()
                        .join();

        // THEN: snapshots of the highest sequence number for each pid are returned
        assertThat(snapshots).containsExactly(
                new Document().append("_id", "snap:pid4")
                        .append("__lifecycle", "ACTIVE")
                        .append("_modified", "1970-01-01T00:00:04Z")
                        .append("sn", 4L),
                new Document().append("_id", "snap:pid6")
                        .append("__lifecycle", "ACTIVE")
                        .append("_modified", "1970-01-01T00:00:06Z")
                        .append("sn", 6L)
        );
    }

    @Test
    public void extractJournalPidsFromEventsAndNotSnapshots() {
        insert("test_journal", new JournalEntry("pid3").withSn(2L).getDocument());
        insert("test_journal", new JournalEntry("pid4").withSn(2L).getDocument());
        insert("test_journal", new JournalEntry("pid1").withSn(1L).getDocument());
        insert("test_journal", new JournalEntry("pid2").withSn(1L).getDocument());
        insert("test_snaps", new Document().append("pid", "pid5").append("sn", 3L));
        insert("test_snaps", new Document().append("pid", "pid6").append("sn", 4L));

        final List<String> pids =
                readJournal.getJournalPids(2, Duration.ZERO, materializer)
                        .runWith(Sink.seq(), materializer)
                        .toCompletableFuture().join();

        assertThat(pids).containsExactly("pid1", "pid2", "pid3", "pid4");
    }

    @Test
    public void extractJournalPidsFromEventsOfEntitiesWhichAreNotDeleted() {
        /*
         * The insertion order of pid3 is not natural but should ensure that the ordering by the sequence number happens
         * correctly. Without this ordering there would be issues in the batching mechanism.
         */
        insert("test_journal", new JournalEntry("pid3").withSn(1L).withManifest("createdEvent").getDocument());
        insert("test_journal", new JournalEntry("pid3").withSn(2L).withManifest("updatedEvent").getDocument());
        insert("test_journal", new JournalEntry("pid3").withSn(3L).withManifest("deletedEvent").getDocument());

        insert("test_journal", new JournalEntry("pid4").withSn(2L).withManifest("updatedEvent").getDocument());
        insert("test_journal", new JournalEntry("pid1").withSn(1L).withManifest("createdEvent").getDocument());
        insert("test_journal", new JournalEntry("pid2").withSn(1L).withManifest("createdEvent").getDocument());

        final List<String> pids =
                readJournal.getLatestJournalEntries(2, Duration.ZERO, materializer)
                        .filter(document -> Optional.ofNullable(
                                        document.getString(MongoReadJournal.J_EVENT_MANIFEST))
                                .map(manifest -> !"deletedEvent".equals(manifest))
                                .orElse(false))
                        .map(document -> document.getString(MongoReadJournal.J_EVENT_PID))
                        .runWith(Sink.seq(), materializer)
                        .toCompletableFuture().join();

        assertThat(pids).containsExactly("pid1", "pid2", "pid4");
    }

    @Test
    public void extractJournalPidsWithSpecificTag() {
        final Set<String> tagged = Set.of("always-live", "awesome");
        final Set<String> alwaysLiveTagged = Set.of("always-live");
        insert("test_journal", new JournalEntry("pid1").withSn(1L).withTags(tagged).getDocument());
        insert("test_journal", new JournalEntry("pid2").withSn(1L).getDocument());
        insert("test_journal", new JournalEntry("pid3").withSn(2L).withTags(alwaysLiveTagged).getDocument());
        insert("test_journal", new JournalEntry("pid4").withSn(2L).withTags(tagged).getDocument());

        final List<String> pids =
                readJournal.getJournalPidsWithTag("always-live", 2, Duration.ZERO, materializer, true)
                        .runWith(Sink.seq(), materializer)
                        .toCompletableFuture().join();

        assertThat(pids).containsExactly("pid1", "pid3", "pid4");
    }

    @Test
    public void extractJournalPidsInOrderOfTags() {
        insert("test_journal",
                new JournalEntry("pid1").withSn(1L).withTags(Set.of("always-alive", "priority-10")).getDocument());
        insert("test_journal",
                new JournalEntry("pid2").withSn(1L).withTags(Set.of("always-alive", "priority-2")).getDocument());
        insert("test_journal",
                new JournalEntry("pid3").withSn(2L).withTags(Set.of("always-alive", "priority-3")).getDocument());
        insert("test_journal",
                new JournalEntry("pid4").withSn(2L).withTags(Set.of("always-alive", "priority-4")).getDocument());

        final List<String> pids =
                readJournal.getJournalPidsWithTagOrderedByPriorityTag("always-alive", Duration.ZERO)
                        .runWith(Sink.seq(), materializer)
                        .toCompletableFuture().join();

        assertThat(pids).containsExactly("pid1", "pid4", "pid3", "pid2");
    }

    @Test
    public void extractJournalPidsInOrderOfTagsOfNewestEvent() {
        insert("test_journal",
                new JournalEntry("pid1").withSn(1L).withTags(Set.of("always-alive", "priority-99")).getDocument());
        insert("test_journal",
                new JournalEntry("pid1").withSn(2L).withTags(Set.of("always-alive", "priority-1")).getDocument());
        insert("test_journal",
                new JournalEntry("pid2").withSn(1L).withTags(Set.of("always-alive", "priority-2")).getDocument());
        insert("test_journal",
                new JournalEntry("pid3").withSn(2L).withTags(Set.of("always-alive", "priority-3")).getDocument());
        insert("test_journal",
                new JournalEntry("pid4").withSn(2L).withTags(Set.of("always-alive", "priority-4")).getDocument());

        final List<String> pids =
                readJournal.getJournalPidsWithTagOrderedByPriorityTag("always-alive", Duration.ZERO)
                        .runWith(Sink.seq(), materializer)
                        .toCompletableFuture().join();

        assertThat(pids).containsExactly("pid4", "pid3", "pid2", "pid1");
    }

    @Test
    public void extractJournalPidsInOrderOfTagsOfNewestEventWhenNewestDoesNotContainTagAnymoreConsideringOnlyLatest() {
        final List<String> pids = findPidsWithTestTagWithDifferentConsiderOnlyLatest(true);
        assertThat(pids).containsExactly("pid2", "pid3");
    }

    @Test
    public void extractJournalPidsInOrderOfTagsOfNewestEventWhenNewestDoesNotContainTagAnymore() {
        final List<String> pids = findPidsWithTestTagWithDifferentConsiderOnlyLatest(false);
        assertThat(pids).containsExactly("pid1", "pid2", "pid3");
    }

    private List<String> findPidsWithTestTagWithDifferentConsiderOnlyLatest(final boolean considerOnlyLatest) {
        insert("test_journal",
                new JournalEntry("pid1").withSn(1L).withTags(Set.of("test")).getDocument());
        insert("test_journal",
                new JournalEntry("pid1").withSn(2L).withTags(Set.of()).getDocument());
        insert("test_journal",
                new JournalEntry("pid2").withSn(1L).withTags(Set.of("test")).getDocument());
        insert("test_journal",
                new JournalEntry("pid3").withSn(2L).withTags(Set.of("test")).getDocument());
        insert("test_journal",
                new JournalEntry("pid4").withSn(2L).withTags(Set.of()).getDocument());

        return readJournal.getJournalPidsWithTag("test", 5, Duration.ZERO, materializer, considerOnlyLatest)
                        .runWith(Sink.seq(), materializer)
                        .toCompletableFuture().join();
    }

    @Test
    public void extractJournalPidsInOrderOfTagsOfNewestEventWhenNewestDoesNotContainTagAnymorAndBatchCanReturnFullyEmptyList() {
        insert("test_journal",
                new JournalEntry("pid1").withSn(1L).withTags(Set.of("test")).getDocument());
        insert("test_journal",
                new JournalEntry("pid1").withSn(2L).withTags(Set.of()).getDocument());
        insert("test_journal",
                new JournalEntry("pid2").withSn(1L).withTags(Set.of("test")).getDocument());
        insert("test_journal",
                new JournalEntry("pid3").withSn(2L).withTags(Set.of("test")).getDocument());
        insert("test_journal",
                new JournalEntry("pid4").withSn(2L).withTags(Set.of()).getDocument());

        final List<String> pids =
                readJournal.getJournalPidsWithTag("test", 2, Duration.ZERO, materializer, true)
                        .runWith(Sink.seq(), materializer)
                        .toCompletableFuture().join();

        assertThat(pids).containsExactly("pid2", "pid3");
    }

    @Test
    public void extractJournalPidsInOrderOfTagsIgnoresOtherTags() {
        insert("test_journal", new JournalEntry("pid1")
                .withSn(1L)
                .withTags(Set.of("always-alive", "priority-99"))
                .getDocument());
        insert("test_journal", new JournalEntry("pid2")
                .withSn(1L)
                .withTags(Set.of("always-alive", "priority-2"))
                .getDocument());
        insert("test_journal", new JournalEntry("pid3")
                .withSn(2L)
                .withTags(Set.of("always-alive", "z-tag", "priority-3"))
                .getDocument());
        insert("test_journal", new JournalEntry("pid4")
                .withSn(2L)
                .withTags(Set.of("always-alive", "priority-4"))
                .getDocument());

        final List<String> pids = readJournal.getJournalPidsWithTagOrderedByPriorityTag("always-alive", Duration.ZERO)
                .runWith(Sink.seq(), materializer)
                .toCompletableFuture().join();

        assertThat(pids).containsExactly("pid1", "pid4", "pid3", "pid2");
    }

    @Test
    public void extractJournalPidsWithTagOrderedByPriorityTagWhenPriorityTagMissing() {
        insert("test_journal", new JournalEntry("pid1").withSn(1L).withTags(Set.of("always-alive")).getDocument());
        insert("test_journal", new JournalEntry("pid2").withSn(1L).withTags(Set.of("always-alive")).getDocument());
        insert("test_journal", new JournalEntry("pid3").withSn(1L).withTags(Set.of("always-alive")).getDocument());
        insert("test_journal", new JournalEntry("pid4").withSn(1L).withTags(Set.of("always-alive")).getDocument());

        final List<String> pids =
                readJournal.getJournalPidsWithTagOrderedByPriorityTag("always-alive", Duration.ZERO)
                        .runWith(Sink.seq(), materializer)
                        .toCompletableFuture().join();

        assertThat(pids).containsExactlyInAnyOrder("pid1", "pid2", "pid3", "pid4");
    }

    @Test
    public void extractJournalPidsAboveALowerBound() {
        insert("test_journal", new JournalEntry("pid1").withSn(1L).getDocument());
        insert("test_journal", new JournalEntry("pid2").withSn(1L).getDocument());
        insert("test_journal", new JournalEntry("pid3").withSn(2L).getDocument());
        insert("test_journal", new JournalEntry("pid4").withSn(2L).getDocument());

        final List<String> pids =
                readJournal.getJournalPidsAbove("pid2", 2, materializer)
                        .runWith(Sink.seq(), materializer)
                        .toCompletableFuture().join();

        assertThat(pids).containsExactly("pid3", "pid4");
    }

    @Test
    public void findLowestEventSeqNr() {
        insert("test_journal", new JournalEntry("pid1").withSn(1L).getDocument());
        insert("test_journal", new JournalEntry("pid2").withSn(1L).getDocument());
        insert("test_journal", new JournalEntry("pid2").withSn(2L).getDocument());

        final List<Optional<Long>> lowestSeqNrs = Stream.of(readJournal.getSmallestEventSeqNo("pid1"),
                        readJournal.getSmallestEventSeqNo("pid2"),
                        readJournal.getSmallestEventSeqNo("pid3"))
                .flatMap(source -> source.runWith(Sink.seq(), materializer).toCompletableFuture().join().stream())
                .toList();

        assertThat(lowestSeqNrs).containsExactly(Optional.of(1L), Optional.of(1L), Optional.empty());
    }

    @Test
    public void deleteEventsUpTo() {
        insert("test_journal", new JournalEntry("pid1").withSn(1L).getDocument());
        insert("test_journal", new JournalEntry("pid2").withSn(1L).getDocument());
        insert("test_journal", new JournalEntry("pid2").withSn(2L).getDocument());
        insert("test_journal", new JournalEntry("pid3").withSn(2L).getDocument());

        final List<Long> lowestSeqNrs = Stream.of(readJournal.deleteEvents("pid1", 0, 1),
                        readJournal.deleteEvents("pid2", 0, 1),
                        readJournal.deleteEvents("pid3", 0, 1))
                .flatMap(source -> source.runWith(Sink.seq(), materializer).toCompletableFuture().join().stream())
                .map(DeleteResult::getDeletedCount)
                .toList();

        assertThat(lowestSeqNrs).containsExactly(1L, 1L, 0L);
    }

    @Test
    public void findLowestSnapshotSeqNr() {
        insert("test_snaps", new Document()
                .append("pid", "pid1")
                .append("sn", 1L)
                .append("s2", new Document().append("_modified", "2020-02-29T23:59:59.999Z"))
        );
        insert("test_snaps", new Document()
                .append("pid", "pid2")
                .append("sn", 1L)
                .append("s2", new Document().append("_modified", "1999-01-01:00:00:00.000Z"))
        );
        insert("test_snaps", new Document()
                .append("pid", "pid2")
                .append("sn", 2L)
                .append("s2", new Document().append("_modified", "2000-01-01T00:01:01.000Z"))
        );

        final List<Optional<Long>> lowestSeqNrs = Stream.of(readJournal.getSmallestSnapshotSeqNo("pid1"),
                        readJournal.getSmallestSnapshotSeqNo("pid2"),
                        readJournal.getSmallestSnapshotSeqNo("pid3"))
                .flatMap(source -> source.runWith(Sink.seq(), materializer).toCompletableFuture().join().stream())
                .toList();

        assertThat(lowestSeqNrs).containsExactly(Optional.of(1L), Optional.of(1L), Optional.empty());
    }

    @Test
    public void deleteSnapshotsUpTo() {
        insert("test_snaps", new Document()
                .append("pid", "pid1")
                .append("sn", 1L)
                .append("s2", new Document().append("_modified", "2020-02-29T23:59:59.999Z"))
        );
        insert("test_snaps", new Document()
                .append("pid", "pid2")
                .append("sn", 1L)
                .append("s2", new Document().append("_modified", "1999-01-01:00:00:00.000Z"))
        );
        insert("test_snaps", new Document()
                .append("pid", "pid2")
                .append("sn", 2L)
                .append("s2", new Document().append("_modified", "2000-01-01T00:01:01.000Z"))
        );
        insert("test_snaps", new Document()
                .append("pid", "pid3")
                .append("sn", 2L)
                .append("s2", new Document().append("_modified", "2000-01-01T00:01:01.000Z"))
        );

        final List<Long> lowestSeqNrs = Stream.of(readJournal.deleteSnapshots("pid1", 0, 1),
                        readJournal.deleteSnapshots("pid2", 0, 1),
                        readJournal.deleteSnapshots("pid3", 0, 1))
                .flatMap(source -> source.runWith(Sink.seq(), materializer).toCompletableFuture().join().stream())
                .map(DeleteResult::getDeletedCount)
                .toList();

        assertThat(lowestSeqNrs).containsExactly(1L, 1L, 0L);
    }

    @Test
    public void extractJournalPidsAboveALowerBoundWithSpecificTag() {
        final Set<String> tagged = Set.of("always-live");
        insert("test_journal", new JournalEntry("pid1").withSn(1L).withTags(tagged).getDocument());
        insert("test_journal", new JournalEntry("pid2").withSn(1L).getDocument());
        insert("test_journal", new JournalEntry("pid3").withSn(2L).withTags(tagged).getDocument());
        insert("test_journal", new JournalEntry("pid4").withSn(2L).withTags(tagged).getDocument());
        insert("test_journal", new JournalEntry("pid5").withSn(3L).getDocument());
        insert("test_journal", new JournalEntry("pid6").withSn(3L).withTags(tagged).getDocument());

        final List<String> pids =
                readJournal.getJournalPidsAboveWithTag("pid2", "always-live", 2, materializer)
                        .runWith(Sink.seq(), materializer)
                        .toCompletableFuture().join();

        assertThat(pids).containsExactly("pid3", "pid4", "pid6");
    }

    private void insert(final CharSequence collection, final Document... documents) {
        Source.fromPublisher(mongoClient.getCollection(collection).insertMany(Arrays.asList(documents)))
                .runWith(Sink.ignore(), materializer)
                .toCompletableFuture()
                .join();
    }

    private static final class JournalEntry {

        private final Document document;

        private JournalEntry(final String pid) {
            document = new Document().append("pid", pid)
                    .append("events", new BsonArray(List.of(BsonDocument.parse(new Document()
                            .append("pid", pid)
                            .toJson())
                    )));
        }

        private JournalEntry withSn(final Long sn) {
            document.append("to", sn);
            final BsonDocument event = (BsonDocument) document.get("events", List.class).get(0);
            event.append("sn", new BsonInt64(sn));
            return this;
        }

        private JournalEntry withManifest(final String manifest) {
            final BsonDocument event = (BsonDocument) document.get("events", List.class).get(0);
            event.append("manifest", new BsonString(manifest));
            return this;
        }

        private JournalEntry withTags(final Collection<String> tags) {
            final BsonArray bsonTags = new BsonArray(tags.stream().map(BsonString::new).toList());
            document.append("_tg", bsonTags);
            final BsonDocument event = (BsonDocument) document.get("events", List.class).get(0);
            event.append("_tg", bsonTags);
            return this;
        }

        private Document getDocument() {
            return document;
        }

    }

}
