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
package org.eclipse.ditto.internal.utils.persistence.postgres.readjournal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.internal.utils.persistence.postgres.client.DittoPostgresClient;
import org.eclipse.ditto.internal.utils.persistence.postgres.ops.PostgresPersistenceOperations;
import org.eclipse.ditto.internal.utils.persistence.postgres.ops.RecordingConnectionFactory;
import org.eclipse.ditto.internal.utils.persistence.postgres.ops.StubClientSupport;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.persistence.query.Offset;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.SystemMaterializer;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;

/**
 * Regression coverage for the PostgreSQL read journal's fetch size and replay query shape.
 * <p>
 * (1) with the connection-pool-wide {@code fetch-size=0} default, the r2dbc-postgresql driver runs a statement as one
 * fetch-all {@code Execute}; it reads the rows demand-driven, so the server blocks on the unread socket under a slow
 * consumer. The genuinely unbounded streaming read-journal queries ({@code currentPersistenceIds()},
 * {@code eventsByTag()}) bind a positive per-statement {@code Statement.fetchSize(n)} instead, so a server-side portal
 * produces the rows fetch by fetch on demand. The tag pid streams (both {@code getJournalPidsWithTag} variants and the
 * priority order) are the deliberate exception: their consumer, the throttled persistence ping, would keep a portal's
 * transaction open past {@code statement_timeout}, so they read short windows without a portal instead (the priority
 * order sorts the qualifying pids of its windows in memory).
 * </p>
 * <p>
 * (2) {@code getLatestJournalEntries} must NOT be an N+1 (one inner replay query per pid); a single
 * loose-index-scan query (distinct-pid phase + per-pid top-1 {@code CROSS JOIN LATERAL}) returns the newest
 * event per pid. The two newest-event-per-pid tag queries use the same loose-index-scan shape instead of
 * {@code DISTINCT ON (pid) … ORDER BY pid, sn DESC}; their filter semantics are pinned against a real PostgreSQL in
 * {@code PostgresNewestPerPidTagQueriesIT}.
 * </p>
 */
public final class PostgresReadJournalBackpressureTest {

    private static ActorSystem system;
    private static Materializer mat;

    @BeforeClass
    public static void beforeClass() {
        system = ActorSystem.create("PostgresReadJournalBackpressureTest");
        mat = SystemMaterializer.get(system).materializer();
    }

    @AfterClass
    public static void afterClass() {
        if (system != null) {
            system.terminate();
        }
    }

    @Test
    public void currentPersistenceIdsBindsPositiveFetchSize() {
        final RecordingConnectionFactory factory = new RecordingConnectionFactory();
        final PostgresReadJournal journal = readJournal(factory);

        drain(journal.currentPersistenceIds());

        assertThat(factory.fetchSizeForSqlContaining("SELECT DISTINCT pid FROM"))
                .as("currentPersistenceIds() must open a server-side portal via a positive fetchSize")
                .isPositive();
    }

    @Test
    public void eventsByTagBindsPositiveFetchSize() {
        final RecordingConnectionFactory factory = new RecordingConnectionFactory();
        final PostgresReadJournal journal = readJournal(factory);

        drain(journal.currentEventsByTag("always-alive", Offset.sequence(0L)));

        assertThat(factory.fetchSizeForSqlContaining("seq > $2 ORDER BY seq ASC"))
                .as("eventsByTag() is unbounded and must bind a positive fetchSize")
                .isPositive();
    }

    @Test
    public void getPidsWithTagIsReadInWindowsWithoutAPortal() {
        final RecordingConnectionFactory factory = new RecordingConnectionFactory();
        final PostgresReadJournal journal = readJournal(factory);

        drain(journal.getJournalPidsWithTag("always-alive", 10, Duration.ofSeconds(1), mat, false));

        assertThat(factory.executedSql()).singleElement().satisfies(sql -> assertThat(sql)
                .as("one window: the next batchSize pids after the cursor, the tag checked per pid without the GIN "
                        + "index")
                .contains("SELECT DISTINCT pid FROM things_journal WHERE pid > $1 ORDER BY pid LIMIT $3",
                        "EXISTS (", "$2 = ANY(t.tags)")
                .doesNotContain("tags @>"));
        assertThat(factory.fetchSizeForSqlContaining("SELECT DISTINCT pid FROM"))
                .as("the throttled ID-order ping must not hold a portal (open transaction, statement_timeout)")
                .isZero();
    }

    @Test
    public void priorityOrderIsReadInWindowsWithoutAPortal() {
        final RecordingConnectionFactory factory = new RecordingConnectionFactory();
        final PostgresReadJournal journal = readJournal(factory);

        drain(journal.getJournalPidsWithTagOrderedByPriorityTag("always-alive", Duration.ofSeconds(1)));

        assertThat(factory.executedContaining("priority-")).as("the priority window ran").isTrue();
        assertThat(factory.executedSql()).singleElement().satisfies(sql -> assertThat(sql)
                .as("one window: the next pids after the cursor with whether they carry the tag and their priority, "
                        + "the tag checked per pid without the GIN index; the order is made in memory")
                .contains("SELECT DISTINCT pid FROM things_journal WHERE pid > $1 ORDER BY pid LIMIT $3",
                        "$2 = ANY(tags)", "priority-")
                .doesNotContain("tags @>", "ORDER BY COALESCE"));
        assertThat(factory.fetchSizeForSqlContaining("priority-"))
                .as("the throttled priority ping must not hold a portal (open transaction, statement_timeout)")
                .isZero();
    }

    @Test
    public void priorityOrderUsesLooseIndexScanNotDistinctOn() {
        final RecordingConnectionFactory factory = new RecordingConnectionFactory();
        final PostgresReadJournal journal = readJournal(factory);

        drain(journal.getJournalPidsWithTagOrderedByPriorityTag("always-alive", Duration.ofSeconds(1)));

        assertThat(factory.executedSql()).singleElement().satisfies(sql -> assertThat(sql)
                .as("window step + per-pid top-1 backward scan that keeps non-qualifying pids (LEFT JOIN), "
                        + "not DISTINCT ON (pid) … ORDER BY pid, sn DESC")
                .contains("SELECT DISTINCT pid FROM", "LEFT JOIN LATERAL", "ORDER BY sn DESC LIMIT 1")
                .doesNotContain("DISTINCT ON"));
    }

    @Test
    public void getPidsWithTagConsideringOnlyLatestUsesLooseIndexScanNotDistinctOn() {
        final RecordingConnectionFactory factory = new RecordingConnectionFactory();
        final PostgresReadJournal journal = readJournal(factory);

        drain(journal.getJournalPidsWithTag("always-alive", 10, Duration.ofSeconds(1), mat, true));

        assertThat(factory.executedSql()).singleElement().satisfies(sql -> assertThat(sql)
                .as("distinct-pid step + per-pid top-1 backward scan, not DISTINCT ON (pid) … ORDER BY pid, sn DESC")
                .contains("SELECT DISTINCT pid FROM", "CROSS JOIN LATERAL", "ORDER BY sn DESC LIMIT 1",
                        "WHERE pid > $1", "ORDER BY pid LIMIT $3", "$2 = ANY(n.tags)")
                .doesNotContain("DISTINCT ON"));
        assertThat(factory.fetchSizeForSqlContaining("CROSS JOIN LATERAL"))
                .as("read in windows, without a portal")
                .isZero();
    }

    @Test
    public void getLatestJournalEntriesUsesSingleLooseScanQueryNotPerPidNPlus1() {
        final RecordingConnectionFactory factory = new RecordingConnectionFactory();
        // Three pids exist; an N+1 implementation would run one inner replay per pid.
        factory.onSql("CROSS JOIN LATERAL", List.of(
                journalRow("thing:ns:a", 7L, "manifest-a"),
                journalRow("thing:ns:b", 3L, "manifest-b"),
                journalRow("thing:ns:c", 9L, "manifest-c")));
        final PostgresReadJournal journal = readJournal(factory);

        drain(journal.getLatestJournalEntries(10, Duration.ofSeconds(1), mat));

        assertThat(factory.executedContaining("CROSS JOIN LATERAL"))
                .as("getLatestJournalEntries must use a single loose-index-scan (CROSS JOIN LATERAL) query")
                .isTrue();
        // N+1 would issue a per-pid bounded-replay (... WHERE pid = $1 AND sn >= ... ORDER BY sn ASC) per pid.
        assertThat(factory.executedCountContaining("sn >= $2 AND sn <= $3 ORDER BY sn ASC"))
                .as("getLatestJournalEntries must NOT issue a per-pid inner replay query (N+1)")
                .isZero();
    }

    private static PostgresReadJournal readJournal(final RecordingConnectionFactory factory) {
        final DittoPostgresClient client = StubClientSupport.clientFor(factory);
        return PostgresReadJournal.of(PostgresPersistenceOperations.of(client, "things"));
    }

    private static Map<String, Object> journalRow(final String pid, final long sn, final String manifest) {
        final Map<String, Object> row = new LinkedHashMap<>();
        row.put("pid", pid);
        row.put("sn", sn);
        row.put("seq", sn);
        row.put("manifest", manifest);
        row.put("tags", new String[0]);
        row.put("event", "{\"foo\":\"bar\"}");
        return row;
    }

    private <T> void drain(final Source<T, ?> source) {
        try {
            source.runWith(Sink.ignore(), system).toCompletableFuture().get(10, TimeUnit.SECONDS);
        } catch (final Exception e) {
            throw new AssertionError("stream failed", e);
        }
    }

}
