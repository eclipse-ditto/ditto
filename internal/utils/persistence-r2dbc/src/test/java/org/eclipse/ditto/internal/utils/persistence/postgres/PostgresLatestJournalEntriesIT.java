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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.eclipse.ditto.internal.utils.persistence.postgres.ops.JournalRow;
import org.eclipse.ditto.internal.utils.persistence.postgres.ops.PostgresPersistenceOperations;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.SystemMaterializer;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.testkit.javadsl.TestKit;

/**
 * Real-PostgreSQL tests of {@link PostgresPersistenceOperations#getLatestJournalEntries()}, the newest-event-per-pid
 * stream (production caller: the connection-id retrieval, hence the {@code connections} table set). The query selects
 * the distinct pids and fetches the newest event of each through a lateral {@code ORDER BY sn DESC LIMIT 1}, in pid
 * order, over a server-side portal of 256 rows.
 */
public final class PostgresLatestJournalEntriesIT {

    private static final String ENTITY = "connections";
    private static final String JOURNAL = ENTITY + "_journal";
    private static final String JOURNAL_SEQ = ENTITY + "_journal_seq";
    private static final Duration STREAM_TIMEOUT = Duration.ofSeconds(60);
    /** Mirrors the portal size the streaming queries bind (256), exceeded by the scale test. */
    private static final int PORTAL_FETCH_SIZE = 256;

    /** Every event carries its own pid index and sequence number as its only tag, e.g. {@code t3-2}. */
    private static final String TAGS = "ARRAY['t' || i || '-' || s]";

    private static NewestPerPidFixture fixture;
    private static ActorSystem system;
    private static Materializer mat;
    private static PostgresPersistenceOperations operations;

    @BeforeClass
    public static void startContainer() {
        try {
            fixture = NewestPerPidFixture.start();
        } catch (final Throwable t) {
            Assume.assumeNoException("Docker/Testcontainers unavailable - skipping PostgresLatestJournalEntriesIT",
                    t);
        }
        system = ActorSystem.create("PostgresLatestJournalEntriesIT");
        mat = SystemMaterializer.get(system).materializer();
        operations = fixture.operations(ENTITY);
    }

    @AfterClass
    public static void stop() {
        if (system != null) {
            TestKit.shutdownActorSystem(system);
        }
        if (fixture != null) {
            fixture.close();
        }
    }

    @Before
    @After
    public void emptyTables() {
        if (fixture != null) {
            fixture.execute("TRUNCATE " + JOURNAL + ", " + JOURNAL_SEQ, List.of());
        }
    }

    @Test
    public void returnsExactlyTheNewestEventOfEveryPidInPidOrder() {
        // inserted out of pid order on purpose; every pid has several events.
        fixture.seedJournal(ENTITY, "connection:c:", 2, 4, TAGS);
        fixture.seedJournal(ENTITY, "connection:a:", 3, 5, TAGS);
        fixture.seedJournal(ENTITY, "connection:b:", 1, 2, TAGS);

        final List<JournalRow> rows = latestEntries();

        assertThat(rows).extracting(JournalRow::pid).containsExactly(
                NewestPerPidFixture.pid("connection:a:", 1), NewestPerPidFixture.pid("connection:a:", 2),
                NewestPerPidFixture.pid("connection:a:", 3), NewestPerPidFixture.pid("connection:b:", 1),
                NewestPerPidFixture.pid("connection:c:", 1), NewestPerPidFixture.pid("connection:c:", 2));
        assertThat(rows).extracting(JournalRow::sequenceNr).containsExactly(5L, 5L, 5L, 2L, 4L, 4L);
        assertThat(rows).allSatisfy(row -> assertThat(row.manifest()).isEqualTo("manifest"));
        // tags and event payload belong to the newest event: each carries its own index and sequence number.
        assertThat(rows).extracting(JournalRow::tags).containsExactly(
                List.of("t1-5"), List.of("t2-5"), List.of("t3-5"), List.of("t1-2"), List.of("t1-4"),
                List.of("t2-4"));
        assertThat(rows).extracting(JournalRow::eventJson).containsExactly(
                "{\"i\": 1, \"s\": 5}", "{\"i\": 2, \"s\": 5}", "{\"i\": 3, \"s\": 5}", "{\"i\": 1, \"s\": 2}",
                "{\"i\": 1, \"s\": 4}", "{\"i\": 2, \"s\": 4}");
        // the identity column of the returned row is the one of the newest row, not of an older one.
        final List<String> expectedSeq = fixture.queryAsText(
                "SELECT seq::text AS seq FROM " + JOURNAL + " j WHERE sn = (SELECT max(sn) FROM " + JOURNAL
                        + " WHERE pid = j.pid) ORDER BY pid", List.of())
                .stream().map(row -> row.get("seq")).toList();
        assertThat(rows).extracting(row -> String.valueOf(row.seq())).containsExactlyElementsOf(expectedSeq);
    }

    @Test
    public void aPidWhoseOlderEventsWereDeletedStillReturnsItsNewestSurvivingEvent() {
        fixture.seedJournal(ENTITY, "connection:del:", 2, 5, TAGS);
        final String trimmed = NewestPerPidFixture.pid("connection:del:", 1);
        final String untouched = NewestPerPidFixture.pid("connection:del:", 2);

        // advances deleted_to to 3; events 4 and 5 survive.
        await(operations.deleteMessagesTo(trimmed, 3L).toFuture());
        assertThat(fixture.queryAsText("SELECT deleted_to::text AS deleted_to FROM " + JOURNAL_SEQ
                + " WHERE pid = $1", List.of(trimmed))).singleElement()
                .satisfies(row -> assertThat(row.get("deleted_to")).isEqualTo("3"));

        final List<JournalRow> rows = latestEntries();

        assertThat(rows).extracting(JournalRow::pid).containsExactly(trimmed, untouched);
        assertThat(rows).extracting(JournalRow::sequenceNr).containsExactly(5L, 5L);
        assertThat(rows.get(0).tags()).containsExactly("t1-5");
    }

    @Test
    public void aPidWhoseOlderEventsWereDeletedUpToButExcludingTheLastOneReturnsThatEvent() {
        fixture.seedJournal(ENTITY, "connection:one:", 1, 4, TAGS);
        final String pid = NewestPerPidFixture.pid("connection:one:", 1);

        await(operations.deleteMessagesTo(pid, 3L).toFuture());

        assertThat(latestEntries()).singleElement().satisfies(row -> {
            assertThat(row.pid()).isEqualTo(pid);
            assertThat(row.sequenceNr()).isEqualTo(4L);
        });
    }

    @Test
    public void aPidWhoseJournalRowsAreAllDeletedIsAbsent() {
        fixture.seedJournal(ENTITY, "connection:gone:", 3, 3, TAGS);
        final String deleted = NewestPerPidFixture.pid("connection:gone:", 2);

        await(operations.deleteMessagesTo(deleted, 3L).toFuture());
        // the journal_seq row survives the delete (highest_sn is kept); the pid must still be absent.
        assertThat(fixture.queryAsText("SELECT pid FROM " + JOURNAL_SEQ + " WHERE pid = $1", List.of(deleted)))
                .hasSize(1);

        assertThat(latestEntries()).extracting(JournalRow::pid).containsExactly(
                NewestPerPidFixture.pid("connection:gone:", 1), NewestPerPidFixture.pid("connection:gone:", 3));
    }

    @Test
    public void completesWithEveryPidWhenThereAreMoreThanOnePortalFetchOfPids() {
        final int pidCount = 3 * PORTAL_FETCH_SIZE + 17;
        final int eventsPerPid = 3;
        fixture.seedJournal(ENTITY, "connection:bulk:", pidCount, eventsPerPid, TAGS);

        final List<JournalRow> rows = latestEntries();

        assertThat(rows).hasSize(pidCount);
        assertThat(rows).extracting(JournalRow::pid).containsExactlyElementsOf(
                IntStream.rangeClosed(1, pidCount).mapToObj(i -> NewestPerPidFixture.pid("connection:bulk:", i))
                        .toList());
        assertThat(rows).allSatisfy(row -> assertThat(row.sequenceNr()).isEqualTo(eventsPerPid));
    }

    @Test
    public void anEmptyJournalCompletesWithoutRows() {
        assertThat(latestEntries()).isEmpty();
    }

    private static List<JournalRow> latestEntries() {
        return operations.getLatestJournalEntries().runWith(Sink.seq(), mat)
                .toCompletableFuture().orTimeout(STREAM_TIMEOUT.toSeconds(), TimeUnit.SECONDS).join();
    }

    private static void await(final java.util.concurrent.Future<?> future) {
        try {
            future.get(STREAM_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        } catch (final Exception e) {
            throw new AssertionError("operation failed", e);
        }
    }

}
