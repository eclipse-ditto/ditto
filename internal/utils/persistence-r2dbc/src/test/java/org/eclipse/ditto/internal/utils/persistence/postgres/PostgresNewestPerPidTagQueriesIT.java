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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import org.eclipse.ditto.internal.utils.persistence.postgres.ops.PostgresPersistenceOperations;
import org.eclipse.ditto.internal.utils.persistence.postgres.readjournal.PostgresReadJournal;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.SystemMaterializer;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.SinkQueueWithCancel;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.testkit.javadsl.TestKit;

/**
 * Real-PostgreSQL tests of the two newest-event-per-pid tag queries, on the {@code connections} table set where
 * production runs them (the connectivity ping streams pids in priority order; connection-id retrieval asks for pids
 * whose newest event carries a tag).
 * <p>
 * The two queries differ in <em>when</em> the tag filter applies, and these tests pin exactly that:
 * </p>
 * <ul>
 *     <li>{@code getJournalPidsWithTagOrderedByPriorityTag} filters <em>before</em> the newest pick: a pid is
 *     included if any of its events carries the tag, and its priority comes from its newest <em>tagged</em> event
 *     (Mongo parity: {@code $match} on the tag, then {@code $group} with {@code $last} tags). Equal priorities come
 *     in pid order.</li>
 *     <li>{@code getJournalPidsWithTag(…, considerOnlyLatest = true)} filters <em>after</em> the newest pick: a pid is
 *     included only if its newest event carries the tag.</li>
 * </ul>
 * <p>
 * A bulk fixture with many more pids than one window covers the windowed ID order and the priority order at scale.
 * Paused consumers show that no server transaction is left while the consumer waits. An EXPLAIN check (sequential scans and explicit
 * sorts disabled) shows that the newest-row step of each query is a backward primary-key scan with no sort on
 * {@code (pid, sn DESC)}.
 * </p>
 */
public final class PostgresNewestPerPidTagQueriesIT {

    private static final String ENTITY = "connections";
    private static final String JOURNAL = ENTITY + "_journal";
    private static final Duration STREAM_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration PAUSE = Duration.ofSeconds(2);
    private static final Duration NO_BACKEND_DEADLINE = Duration.ofSeconds(10);

    private static final String BULK_PREFIX = "connection:bulk:";
    private static final String BULK_TAG = "always-alive";
    private static final int BULK_PIDS = 5_000;
    private static final int BULK_EVENTS_PER_PID = 4;
    /*
     * Every bulk pid i carries the tag and "priority-(i % 10)" on events 1..3. For every third pid the newest event
     * (sn 4) drops the tag and carries a different priority, so the two filter placements give different answers at
     * scale too.
     */
    private static final String BULK_TAGS = "CASE WHEN s = " + BULK_EVENTS_PER_PID + " AND i % 3 = 0 "
            + "THEN ARRAY['priority-' || ((i + 5) % 10)] "
            + "ELSE ARRAY['" + BULK_TAG + "', 'priority-' || (i % 10)] END";

    private static NewestPerPidFixture fixture;
    private static ActorSystem system;
    private static Materializer mat;
    private static PostgresPersistenceOperations operations;
    private static PostgresReadJournal readJournal;

    @BeforeClass
    public static void startContainer() {
        try {
            fixture = NewestPerPidFixture.start();
        } catch (final Throwable t) {
            Assume.assumeNoException("Docker/Testcontainers unavailable — skipping PostgresNewestPerPidTagQueriesIT",
                    t);
        }
        system = ActorSystem.create("PostgresNewestPerPidTagQueriesIT");
        mat = SystemMaterializer.get(system).materializer();
        operations = fixture.operations(ENTITY);
        readJournal = fixture.readJournal(ENTITY);

        fixture.seedJournal(ENTITY, BULK_PREFIX, BULK_PIDS, BULK_EVENTS_PER_PID, BULK_TAGS);
        fixture.analyze(JOURNAL);
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

    // =================================================================================================
    // priority query: the tag filter applies BEFORE the newest pick
    // =================================================================================================

    @Test
    public void priorityQueryTakesThePriorityFromTheNewestEventCarryingTheTag() {
        final String tag = "prio-pick";
        final String newestTaggedWins = "connection:prio-pick:a";
        final String olderTaggedOnly = "connection:prio-pick:b";
        final String single = "connection:prio-pick:c";
        final String neverTagged = "connection:prio-pick:d";
        // two tagged events: the newer one (priority 3) decides, not the older one (priority 7).
        event(newestTaggedWins, 1L, tag, "priority-7");
        event(newestTaggedWins, 2L, tag, "priority-3");
        // the NEWEST event lacks the tag (and carries priority 1): the older tagged event (priority 9) decides.
        event(olderTaggedOnly, 1L, tag, "priority-9");
        event(olderTaggedOnly, 2L, "priority-1");
        event(single, 1L, tag, "priority-5");
        // a priority tag without the membership tag never qualifies.
        event(neverTagged, 1L, "priority-8");

        assertThat(priorityPids(tag))
                .as("included: any tagged event; priority: newest TAGGED event (9 > 5 > 3); never-tagged excluded")
                .containsExactly(olderTaggedOnly, single, newestTaggedWins);
    }

    @Test
    public void priorityQueryOrdersByDescendingNumericPriorityAndOrdersTiesByPid() {
        final String tag = "prio-order";
        final String p10 = "connection:prio-order:a";
        final String p9 = "connection:prio-order:b";
        final String p6FromTwoPriorityTags = "connection:prio-order:c";
        final String p4First = "connection:prio-order:d";
        final String p4Second = "connection:prio-order:e";
        final String noPriority = "connection:prio-order:f";
        final String malformedPriority = "connection:prio-order:g";
        event(noPriority, 1L, tag);
        event(p4Second, 1L, tag, "priority-4");
        event(malformedPriority, 1L, tag, "priority-x");
        event(p9, 1L, tag, "priority-9");
        event(p4First, 1L, tag, "priority-4");
        event(p6FromTwoPriorityTags, 1L, tag, "priority-2", "priority-6");
        event(p10, 1L, tag, "priority-10");

        final List<String> ordered = priorityPids(tag);

        assertThat(ordered).hasSize(7);
        // numeric, not lexicographic ("priority-9" > "priority-10" as text); several priority tags -> the highest.
        assertThat(ordered.subList(0, 3)).containsExactly(p10, p9, p6FromTwoPriorityTags);
        // equal priorities come in pid order ("…:d" < "…:e"), although "…:e" was written first.
        assertThat(ordered.subList(3, 5)).containsExactly(p4First, p4Second);
        // no priority tag and a malformed one both rank as priority 0, in pid order.
        assertThat(ordered.subList(5, 7)).containsExactly(noPriority, malformedPriority);
    }

    @Test
    public void priorityQueryWithEmptyTagSelectsEveryPidWithItsNewestEventsPriority() {
        final String prefix = "connection:prio-all:";
        final String newestEventHasNoPriority = prefix + "a";
        final String priority2 = prefix + "b";
        final String noTags = prefix + "c";
        final String newestEventPriority6 = prefix + "d";
        event(newestEventHasNoPriority, 1L, "priority-9");
        event(newestEventHasNoPriority, 2L);
        event(priority2, 1L, "priority-2");
        event(noTags, 1L);
        event(newestEventPriority6, 1L, "always-alive", "priority-4");
        event(newestEventPriority6, 2L, "priority-6");

        // empty tag = every event qualifies, so the overall newest event of each pid supplies the priority.
        final List<String> ordered = priorityPids("").stream().filter(pid -> pid.startsWith(prefix)).toList();

        assertThat(ordered).hasSize(4);
        assertThat(ordered.subList(0, 2)).containsExactly(newestEventPriority6, priority2);
        assertThat(ordered.subList(2, 4)).containsExactly(newestEventHasNoPriority, noTags);
    }

    @Test
    public void priorityQueryReturnsEveryTaggedPidInPriorityOrderAtScale() {
        final List<String> ordered = priorityPids(BULK_TAG).stream()
                .filter(pid -> pid.startsWith(BULK_PREFIX))
                .toList();

        // every bulk pid has tagged events; its priority is i % 10 (from the newest TAGGED event, never sn 4's).
        assertThat(ordered).hasSize(BULK_PIDS).doesNotHaveDuplicates();
        final List<Integer> priorities = ordered.stream().map(pid -> bulkIndex(pid) % 10).toList();
        assertThat(priorities).isSortedAccordingTo((a, b) -> Integer.compare(b, a));
        assertThat(ordered).as("equal priorities in pid order")
                .isSortedAccordingTo(Comparator.comparing((String pid) -> bulkIndex(pid) % 10).reversed()
                        .thenComparing(Comparator.naturalOrder()));
    }

    // =================================================================================================
    // getJournalPidsWithTag(considerOnlyLatest = true): the tag filter applies AFTER the newest pick
    // =================================================================================================

    @Test
    public void pidsWithTagConsideringOnlyLatestChecksTheNewestEventOnly() {
        final String tag = "latest-only";
        final String tagOnNewest = "connection:latest-only:d";
        final String tagOnOlderOnly = "connection:latest-only:b";
        final String tagOnBoth = "connection:latest-only:a";
        final String neverTagged = "connection:latest-only:c";
        final String tagOnOlderNewestUntagged = "connection:latest-only:e";
        event(tagOnNewest, 1L);
        event(tagOnNewest, 2L, tag);
        event(tagOnOlderOnly, 1L, tag);
        event(tagOnOlderOnly, 2L, "other");
        event(tagOnBoth, 1L, tag);
        event(tagOnBoth, 2L, tag, "other");
        event(neverTagged, 1L, "other");
        event(tagOnOlderNewestUntagged, 1L, tag);
        event(tagOnOlderNewestUntagged, 2L);

        assertThat(latestTaggedPids(tag))
                .as("only pids whose NEWEST event carries the tag, in pid order")
                .containsExactly(tagOnBoth, tagOnNewest);
        // contrast: without considerOnlyLatest any tagged event qualifies.
        assertThat(anyTaggedPids(tag))
                .containsExactly(tagOnBoth, tagOnOlderOnly, tagOnNewest, tagOnOlderNewestUntagged);
    }

    @Test
    public void pidsWithTagConsideringOnlyLatestWithEmptyTagSelectsEveryPidInBytewisePidOrder() {
        final String prefix = "connection:latest-all:";
        event(prefix + "a", 1L, "x");
        event(prefix + "C", 1L);
        event(prefix + "B", 1L, "x");
        event(prefix + "B", 2L);

        final List<String> pids = latestTaggedPids("").stream().filter(pid -> pid.startsWith(prefix)).toList();

        // pid is COLLATE "C": byte order puts upper case before lower case.
        assertThat(pids).containsExactly(prefix + "B", prefix + "C", prefix + "a");
    }

    @Test
    public void pidsWithTagConsideringOnlyLatestStreamsMoreRowsThanOnePortalFetch() {
        final List<String> pids = latestTaggedPids(BULK_TAG).stream()
                .filter(pid -> pid.startsWith(BULK_PREFIX))
                .toList();

        // every third bulk pid's newest event dropped the tag -> excluded; the rest in pid order.
        final List<String> expected = IntStream.rangeClosed(1, BULK_PIDS)
                .filter(i -> i % 3 != 0)
                .mapToObj(i -> NewestPerPidFixture.pid(BULK_PREFIX, i))
                .toList();
        assertThat(pids).containsExactlyElementsOf(expected);
    }

    // =================================================================================================
    // paused consumers: between statements no statement, transaction or snapshot is left on the server
    // =================================================================================================

    @Test
    public void pausedConsumerOfThePriorityStreamHoldsNoServerTransaction() throws Exception {
        final String tag = "prio-pause";
        final int pids = 600;
        fixture.seedJournal(ENTITY, "connection:prio-pause:", pids, 1,
                "ARRAY['" + tag + "', 'priority-' || (i % 10)]");
        assertNoServerTransactionWhilePausedAndComplete(
                readJournal.getJournalPidsWithTagOrderedByPriorityTag(tag, STREAM_TIMEOUT), pids);
    }

    @Test
    public void pausedConsumerOfTheIdOrderPidStreamHoldsNoServerTransaction() throws Exception {
        // the policies ping streams getJournalPidsWithTag(…, considerOnlyLatest = false) in ID order, throttled (by
        // default to one pid per second).
        assertNoServerTransactionWhilePausedAndComplete(
                readJournal.getJournalPidsWithTag(BULK_TAG, 100, STREAM_TIMEOUT, mat, false)
                        .filter(pid -> pid.startsWith(BULK_PREFIX)), BULK_PIDS);
    }

    private static void assertNoServerTransactionWhilePausedAndComplete(final Source<String, ?> stream,
            final int expectedPids) throws Exception {
        final SinkQueueWithCancel<String> consumer = stream.runWith(Sink.queue(), mat);
        try {
            for (int i = 0; i < 10; i++) {
                assertThat(pull(consumer)).isPresent();
            }

            // The consumer pauses, as the throttled persistence ping does. Every statement is short and is
            // read completely, so its transaction has ended: no snapshot is held and statement_timeout cannot cut
            // the stream off.
            awaitNoBackend("(state <> 'idle' OR backend_xmin IS NOT NULL OR xact_start IS NOT NULL) "
                    + "AND query LIKE '%" + JOURNAL + "%'", List.of());
            TimeUnit.MILLISECONDS.sleep(PAUSE.toMillis());
            assertThat(backends("(state <> 'idle' OR backend_xmin IS NOT NULL OR xact_start IS NOT NULL) "
                    + "AND query LIKE '%" + JOURNAL + "%'", List.of()))
                    .as("backends in a statement, a transaction or holding a snapshot during the pause")
                    .isEmpty();

            int received = 10;
            while (pull(consumer).isPresent()) {
                received++;
            }
            assertThat(received).isEqualTo(expectedPids);
        } finally {
            consumer.cancel();
        }
    }

    // =================================================================================================
    // plan shape: the newest-row step is a backward PK scan, never a sort on (pid, sn DESC)
    // =================================================================================================

    @Test
    public void priorityQueryNewestRowStepIsABackwardPrimaryKeyScanWithoutSort() {
        final String sql = productionSql(rj -> rj.getJournalPidsWithTagOrderedByPriorityTag("", STREAM_TIMEOUT));
        // first window: ($1 cursor, $2 tag, $3 window size)
        assertNewestRowStepIsABackwardPrimaryKeyScanWithoutSort(sql, List.of("", "", 100), "");
        assertNewestRowStepIsABackwardPrimaryKeyScanWithoutSort(sql, List.of("", BULK_TAG, 100), BULK_TAG);
    }

    @Test
    public void pidsWithTagConsideringOnlyLatestNewestRowStepIsABackwardPrimaryKeyScanWithoutSort() {
        final String sql = productionSql(rj -> rj.getJournalPidsWithTag("", 100, STREAM_TIMEOUT, mat, true));
        // first window: ($1 cursor, $2 tag, $3 window size)
        assertNewestRowStepIsABackwardPrimaryKeyScanWithoutSort(sql, List.of("", "", 100), "");
        assertNewestRowStepIsABackwardPrimaryKeyScanWithoutSort(sql, List.of("", BULK_TAG, 100), BULK_TAG);
    }

    // =================================================================================================
    // helpers
    // =================================================================================================

    private static void assertNewestRowStepIsABackwardPrimaryKeyScanWithoutSort(final String sql,
            final List<Object> binds, final String tag) {
        // Index ELIGIBILITY on a test-sized table: without seq scans and explicit sorts the planner must still find a
        // sort-free plan. The former DISTINCT ON (pid) … ORDER BY pid, sn DESC shape has none: even with sorting
        // disabled it keeps an (Incremental) Sort on (pid, sn DESC) over the whole journal. The per-pid top-1 is an
        // index-only scan when the query needs no tags from that row (empty tag, newest-event variant).
        final String plan = fixture.explain(sql, binds, "enable_seqscan = off", "enable_sort = off");
        assertThat(plan)
                .as("tag '%s', plan:%n%s", tag, plan)
                .doesNotContainPattern(Pattern.compile("Sort Key: [^\\n]*\\bsn DESC"))
                .containsPattern(Pattern.compile(
                        "->  Limit\\n\\s+->  Index (Only )?Scan Backward using " + JOURNAL + "_pkey on " + JOURNAL));
    }

    /** The SQL the production code issues for a read-journal call, captured offline with a recording stub. */
    private static String productionSql(final Function<PostgresReadJournal, Source<String, ?>> call) {
        return PidStreamTestSupport.productionSql(ENTITY, call, mat);
    }

    private static void event(final String pid, final long sn, final String... tags) {
        try {
            operations.insertEvents(List.of(new PostgresPersistenceOperations.JournalInsert(
                            pid, sn, "manifest", List.of(tags), "{}")))
                    .toFuture()
                    .get(STREAM_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        } catch (final Exception e) {
            throw new AssertionError("insert of " + pid + "/" + sn + " failed", e);
        }
    }

    private static List<String> priorityPids(final String tag) {
        return run(readJournal.getJournalPidsWithTagOrderedByPriorityTag(tag, STREAM_TIMEOUT));
    }

    private static List<String> latestTaggedPids(final String tag) {
        return run(readJournal.getJournalPidsWithTag(tag, 100, STREAM_TIMEOUT, mat, true));
    }

    private static List<String> anyTaggedPids(final String tag) {
        return run(readJournal.getJournalPidsWithTag(tag, 100, STREAM_TIMEOUT, mat, false));
    }

    private static Optional<String> pull(final SinkQueueWithCancel<String> consumer) throws Exception {
        return consumer.pull().toCompletableFuture().get(STREAM_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
    }

    /** Polls {@code pg_stat_activity} until no other backend matches the condition. */
    private static void awaitNoBackend(final String condition, final List<Object> binds) throws InterruptedException {
        final long deadline = System.nanoTime() + NO_BACKEND_DEADLINE.toNanos();
        List<Map<String, String>> rows = backends(condition, binds);
        while (!rows.isEmpty() && System.nanoTime() < deadline) {
            TimeUnit.MILLISECONDS.sleep(50);
            rows = backends(condition, binds);
        }
        assertThat(rows).as("backends still matching %s after %s", condition, NO_BACKEND_DEADLINE).isEmpty();
    }

    private static List<Map<String, String>> backends(final String condition, final List<Object> binds) {
        return fixture.queryAsText("SELECT pid::text AS pid, state, backend_xmin::text AS backend_xmin, "
                + "xact_start::text AS xact_start, "
                + "extract(epoch FROM clock_timestamp() - xact_start)::text AS xact_age_s "
                + "FROM pg_stat_activity WHERE datname = current_database() AND pid <> pg_backend_pid() AND "
                + condition, binds);
    }

    private static int bulkIndex(final String bulkPid) {
        return Integer.parseInt(bulkPid.substring(BULK_PREFIX.length()));
    }

    private static <T> List<T> run(final Source<T, ?> source) {
        try {
            return source.runWith(Sink.seq(), mat).toCompletableFuture()
                    .get(STREAM_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        } catch (final Exception e) {
            throw new AssertionError("stream failed", e);
        }
    }

}
