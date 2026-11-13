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
package org.eclipse.ditto.internal.utils.persistence.postgres;

import org.eclipse.ditto.internal.utils.persistence.postgres.schema.PostgresSchema;

import org.eclipse.ditto.internal.utils.persistence.postgres.client.DittoPostgresClient;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.testkit.PostgresDbResource;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.internal.utils.persistence.api.SnapshotEntry;
import org.eclipse.ditto.internal.utils.persistence.api.SnapshotFilter;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.config.DefaultPostgresConfig;
import org.eclipse.ditto.internal.utils.persistence.postgres.ops.PostgresPersistenceOperations;
import org.eclipse.ditto.internal.utils.persistence.postgres.readjournal.PostgresReadJournal;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.PostgresSchemaManager;
import org.eclipse.ditto.internal.utils.persistence.postgres.snapshot.PostgresSnapshotStoreOps;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.spi.ConnectionFactory;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.persistence.SnapshotMetadata;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.SystemMaterializer;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.testkit.javadsl.TestKit;

/**
 * Integration test proving {@link PostgresReadJournal#getJournalPids} / {@code getJournalPidsAbove} stream ALL
 * matching pids across multiple pages instead of truncating to a single {@code LIMIT batchSize} page — the exact
 * contract of {@code MongoReadJournal#unfoldBatchedSource}. Runs under failsafe ({@code *IT}); skipped offline /
 * when no Docker daemon is reachable.
 */
public final class PostgresReadJournalPaginationIT {

    private static final PostgresDbResource POSTGRES = new PostgresDbResource();

    private static ActorSystem system;
    private static Materializer mat;
    private static DittoPostgresClient client;
    private static PostgresPersistenceOperations operations;
    private static PostgresSnapshotStoreOps snapshots;
    private static PostgresReadJournal readJournal;

    @BeforeClass
    public static void startContainer() {
        try {
            POSTGRES.start();
        } catch (final Throwable t) {
            Assume.assumeNoException("Docker/Testcontainers unavailable — skipping PostgresReadJournalPaginationIT",
                    t);
        }
        final ConnectionFactory factory = POSTGRES.newConnectionFactory();
        PostgresSchemaManager.of(factory, PostgresSchema.descriptor()).bootstrap();

        system = ActorSystem.create("PostgresReadJournalPaginationIT");
        mat = SystemMaterializer.get(system).materializer();
        final ConnectionPool pool = new ConnectionPool(ConnectionPoolConfiguration.builder(factory)
                .name("pagination-it-pool").maxSize(8).build());
        client = DittoPostgresClient.forConnectionPool(pool, DefaultPostgresConfig.of(ConfigFactory.empty()));
        operations = PostgresPersistenceOperations.of(client, "things");
        snapshots = PostgresSnapshotStoreOps.of(operations);
        readJournal = PostgresReadJournal.of(operations);
    }

    @AfterClass
    public static void stop() {
        if (client != null) {
            client.close();
        }
        if (system != null) {
            TestKit.shutdownActorSystem(system);
        }
        POSTGRES.stop();
    }

    private static <T> T await(final CompletionStage<T> stage) throws Exception {
        return stage.toCompletableFuture().get(20, TimeUnit.SECONDS);
    }

    @Test
    public void getJournalPidsStreamsAllPidsAcrossPages() throws Exception {
        // 7 pids, batch size 3 -> 3 pages (3+3+1). Pre-fix: only the first 3 pids arrive.
        for (int i = 1; i <= 7; i++) {
            await(operations.insertEvents(List.of(new PostgresPersistenceOperations.JournalInsert(
                    "thing:page:p" + i, 1L, "manifest", List.of(), "{\"n\":" + i + "}"))).toFuture());
        }
        final List<String> pids = readJournal.getJournalPids(3, Duration.ofSeconds(10), mat)
                .filter(pid -> pid.startsWith("thing:page:"))
                .runWith(Sink.seq(), mat)
                .toCompletableFuture().get(20, TimeUnit.SECONDS);
        assertThat(pids).containsExactly("thing:page:p1", "thing:page:p2", "thing:page:p3",
                "thing:page:p4", "thing:page:p5", "thing:page:p6", "thing:page:p7");
    }

    @Test
    public void getJournalPidsAboveResumesFromLowerBound() throws Exception {
        // reuses the 7 pids from the test above if ordering is not guaranteed, insert independently:
        for (int i = 1; i <= 5; i++) {
            await(operations.insertEvents(List.of(new PostgresPersistenceOperations.JournalInsert(
                    "thing:above:a" + i, 1L, "manifest", List.of(), "{}"))).toFuture());
        }
        final List<String> pids = readJournal.getJournalPidsAbove("thing:above:a2", 2, mat)
                .filter(pid -> pid.startsWith("thing:above:"))
                .runWith(Sink.seq(), mat)
                .toCompletableFuture().get(20, TimeUnit.SECONDS);
        assertThat(pids).containsExactly("thing:above:a3", "thing:above:a4", "thing:above:a5");
    }

    @Test
    public void newestSnapshotsPaginateAcrossPages() throws Exception {
        for (int i = 1; i <= 7; i++) {
            // timestamp 1000L = epoch past, safely below now() for the age filter
            await(snapshots.saveAsync(new SnapshotMetadata("thing:spage:s" + i, 1L, 1000L), "{\"n\":" + i + "}"));
        }
        final List<String> pids = readJournal.getNewestSnapshotsAbove("", 3, false, Duration.ZERO, mat)
                .filter(e -> e.getPid().filter(p -> p.startsWith("thing:spage:")).isPresent())
                .map(e -> e.getPid().orElseThrow())
                .runWith(Sink.seq(), mat)
                .toCompletableFuture().get(20, TimeUnit.SECONDS);
        assertThat(pids).hasSize(7);
    }

    @Test
    public void deletedNewestSnapshotExcludesPidEntirely() throws Exception {
        // Older LIVE snapshot + newer DELETED snapshot: Mongo semantics = the pid must NOT appear at all.
        await(snapshots.saveAsync(new SnapshotMetadata("thing:del:d1", 50L, 1000L), "{\"__lifecycle\":\"ACTIVE\"}"));
        await(snapshots.saveAsync(new SnapshotMetadata("thing:del:d1", 60L, 2000L), "{\"__lifecycle\":\"DELETED\"}"));
        final List<String> pids = readJournal.getNewestSnapshotsAbove("", 100, false, Duration.ZERO, mat)
                .filter(e -> e.getPid().filter(p -> p.equals("thing:del:d1")).isPresent())
                .map(e -> e.getPid().orElseThrow())
                .runWith(Sink.seq(), mat)
                .toCompletableFuture().get(20, TimeUnit.SECONDS);
        assertThat(pids).as("deleted-newest pid must not be resurrected via its older live snapshot").isEmpty();
    }

    @Test
    public void minAgeZeroIncludesFutureWrittenAt() throws Exception {
        // written_at bound from app clock: simulate app-ahead-of-DB skew with a future timestamp.
        final long future = System.currentTimeMillis() + 60_000L;
        await(snapshots.saveAsync(new SnapshotMetadata("thing:skew:f1", 1L, future), "{\"v\":1}"));
        final List<String> pids = readJournal.getNewestSnapshotsAbove("", 100, false, Duration.ZERO, mat)
                .filter(e -> e.getPid().filter(p -> p.equals("thing:skew:f1")).isPresent())
                .map(e -> e.getPid().orElseThrow())
                .runWith(Sink.seq(), mat)
                .toCompletableFuture().get(20, TimeUnit.SECONDS);
        assertThat(pids).as("Duration.ZERO must bypass the age filter (Mongo isZero() parity)").hasSize(1);
    }

    @Test
    public void emptyTagMatchesAllPids() throws Exception {
        await(operations.insertEvents(List.of(new PostgresPersistenceOperations.JournalInsert(
                "thing:tag:none", 1L, "manifest", List.of(), "{}"))).toFuture());
        await(operations.insertEvents(List.of(new PostgresPersistenceOperations.JournalInsert(
                "thing:tag:tagged", 1L, "manifest", List.of("always-alive", "priority-3"), "{}"))).toFuture());

        // Empty tag -> ALL pids (Mongo parity: PersistencePingActor default journal-tag is "").
        final List<String> all = readJournal.getJournalPidsWithTagOrderedByPriorityTag("", Duration.ofSeconds(10))
                .filter(p -> p.startsWith("thing:tag:"))
                .runWith(Sink.seq(), mat)
                .toCompletableFuture().get(20, TimeUnit.SECONDS);
        assertThat(all).containsExactlyInAnyOrder("thing:tag:none", "thing:tag:tagged");

        // Non-empty tag still filters.
        final List<String> tagged = readJournal.getJournalPidsWithTag("always-alive", 100,
                        Duration.ofSeconds(10), mat, false)
                .filter(p -> p.startsWith("thing:tag:"))
                .runWith(Sink.seq(), mat)
                .toCompletableFuture().get(20, TimeUnit.SECONDS);
        assertThat(tagged).containsExactly("thing:tag:tagged");
    }

    @Test
    public void snapshotFilterPidRegexIsApplied() throws Exception {
        await(snapshots.saveAsync(new SnapshotMetadata("thing:org.acme:in1", 1L, 1000L), "{}"));
        await(snapshots.saveAsync(new SnapshotMetadata("thing:org.other:out1", 1L, 1000L), "{}"));
        final SnapshotFilter filter = SnapshotFilter.of("", "^thing:org\\.acme:.*");
        final List<String> pids = readJournal.getNewestSnapshotsAbove(filter, 100, mat)
                .map(e -> e.getPid().orElseThrow())
                .filter(p -> p.startsWith("thing:org."))
                .runWith(Sink.seq(), mat)
                .toCompletableFuture().get(20, TimeUnit.SECONDS);
        assertThat(pids).containsExactly("thing:org.acme:in1");
    }

    @Test
    public void deleteSnapshotWithZeroTimestampDeletesBySn() throws Exception {
        await(snapshots.saveAsync(new SnapshotMetadata("thing:zdel:z1", 4L, 1234L), "{}"));
        await(snapshots.deleteAsync(new SnapshotMetadata("thing:zdel:z1", 4L, 0L))); // Pekko deleteSnapshot(seqNr)
        final List<String> pids = readJournal.getNewestSnapshotsAbove("", 100, true, Duration.ZERO, mat)
                .filter(e -> e.getPid().filter(p -> p.equals("thing:zdel:z1")).isPresent())
                .map(e -> e.getPid().orElseThrow())
                .runWith(Sink.seq(), mat).toCompletableFuture().get(20, TimeUnit.SECONDS);
        assertThat(pids).as("timestamp-0 delete must fall back to (pid, sn)").isEmpty();
    }

    @Test
    public void deleteSnapshotWithNonZeroTimestampDeletesExactRowOnly() throws Exception {
        // Two written_at rows at the same sn: an exact (ts>0) delete must remove only the targeted row.
        await(snapshots.saveAsync(new SnapshotMetadata("thing:zdel:z2", 4L, 1000L), "{\"v\":1}"));
        await(snapshots.saveAsync(new SnapshotMetadata("thing:zdel:z2", 4L, 2000L), "{\"v\":2}"));
        await(snapshots.deleteAsync(new SnapshotMetadata("thing:zdel:z2", 4L, 1000L)));
        final List<String> pids = readJournal.getNewestSnapshotsAbove("", 100, true, Duration.ZERO, mat)
                .filter(e -> e.getPid().filter(p -> p.equals("thing:zdel:z2")).isPresent())
                .map(e -> e.getPid().orElseThrow())
                .runWith(Sink.seq(), mat).toCompletableFuture().get(20, TimeUnit.SECONDS);
        assertThat(pids).as("the other written_at row must survive an exact (pid, sn, written_at) delete")
                .containsExactly("thing:zdel:z2");
    }

    @Test
    public void mostRecentTagsReturnsAllTagsOfTheNewestEvent() throws Exception {
        await(operations.insertEvents(List.of(new PostgresPersistenceOperations.JournalInsert(
                "thing:mtags:m1", 1L, "m", List.of("old-tag"), "{}"))).toFuture());
        await(operations.insertEvents(List.of(new PostgresPersistenceOperations.JournalInsert(
                "thing:mtags:m1", 2L, "m", List.of("always-alive", "priority-7"), "{}"))).toFuture());
        final List<String> tags = readJournal.getMostRecentJournalTagsForPid("thing:mtags:m1")
                .runWith(Sink.seq(), mat).toCompletableFuture().get(20, TimeUnit.SECONDS);
        assertThat(tags).containsExactlyInAnyOrder("always-alive", "priority-7");
    }

    @Test
    public void mostRecentTagsOfEmptyNewestEventDoesNotLeakOlderTags() throws Exception {
        // Newest event (sn=2) carries NO tags; an older event (sn=1) does. LIMIT-after-unnest used to leak the
        // older event's tag when the newest event's tags array was empty.
        await(operations.insertEvents(List.of(new PostgresPersistenceOperations.JournalInsert(
                "thing:mtags:m2", 1L, "m", List.of("old-tag"), "{}"))).toFuture());
        await(operations.insertEvents(List.of(new PostgresPersistenceOperations.JournalInsert(
                "thing:mtags:m2", 2L, "m", List.of(), "{}"))).toFuture());
        final List<String> tags = readJournal.getMostRecentJournalTagsForPid("thing:mtags:m2")
                .runWith(Sink.seq(), mat).toCompletableFuture().get(20, TimeUnit.SECONDS);
        assertThat(tags).as("newest event has no tags -> no leak from the older event").isEmpty();
    }

    @Test
    public void activeMinAgeUsesNewestQualifyingRowAndSkipsFreshOnlyPids() throws Exception {
        // Mongo-parity STAGE ORDER: the min-age predicate filters ROWS before the newest-per-pid
        // grouping. Two consequences this test pins, with batchSize=1 so slot accounting is visible:
        //  (a) a pid with an old sn=1 and a fresh sn=2 surfaces sn=1 — the newest QUALIFYING row;
        //  (b) a pid whose rows are ALL fresh consumes no page slot and must not end pagination early
        //      ("aa" sorts before "zz", so a phase-2-only filter would emit an empty first page and
        //      truncate the stream before reaching "zz").
        await(snapshots.saveAsync(new SnapshotMetadata("thing:agefilter:aa-freshonly", 1L,
                System.currentTimeMillis()), "{\"v\":\"fresh\"}"));
        await(snapshots.saveAsync(new SnapshotMetadata("thing:agefilter:zz-mixed", 1L, 1000L),
                "{\"v\":\"old\"}"));
        await(snapshots.saveAsync(new SnapshotMetadata("thing:agefilter:zz-mixed", 2L,
                System.currentTimeMillis()), "{\"v\":\"fresh\"}"));

        final List<SnapshotEntry> entries =
                readJournal.getNewestSnapshotsAbove("", 1, true, Duration.ofHours(1), mat)
                        .filter(e -> e.getPid().filter(p -> p.startsWith("thing:agefilter:")).isPresent())
                        .runWith(Sink.seq(), mat)
                        .toCompletableFuture().get(20, TimeUnit.SECONDS);

        assertThat(entries).as("fresh-only pid must not appear; mixed pid must appear once").hasSize(1);
        assertThat(entries.get(0).getPid()).contains("thing:agefilter:zz-mixed");
        assertThat(entries.get(0).getSequenceNumber().getAsLong())
                .as("the newest QUALIFYING (old-enough) row wins, not the overall-newest fresh row")
                .isEqualTo(1L);
    }
}
