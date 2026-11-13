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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.eclipse.ditto.internal.utils.persistence.postgres.ops.PostgresPersistenceOperations;
import org.eclipse.ditto.internal.utils.persistence.postgres.readjournal.PostgresReadJournal;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.stream.Attributes;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.SystemMaterializer;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.SinkQueueWithCancel;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.testkit.javadsl.TestKit;

/**
 * Real-PostgreSQL tests of the tag pid streams (the persistence pings and connection-id retrieval):
 * <ul>
 *     <li>ID order, read in windows of {@code batchSize} pids: completeness, bytewise pid order and no duplicates
 *     across many windows, for both variants;</li>
 *     <li>priority order, the same windows sorted in memory: the same pids and order as the single statement of the
 *     former implementation, ties in pid order;</li>
 *     <li>what a walk sees of changes made while it runs;</li>
 *     <li>the plan of each statement.</li>
 * </ul>
 */
public final class PostgresKeysetPidStreamsIT {

    private static final Duration TIMEOUT = Duration.ofSeconds(60);
    private static final int SMALL_BATCH = 7;

    /**
     * The single priority statement of the former implementation, as a reference (plus the priority as a column, and
     * the pid order of the new tie rule).
     */
    private static final String REFERENCE_PRIORITY_SQL = "SELECT p.pid AS pid, COALESCE(pr.prio, 0)::text AS prio "
            + "FROM ("
            + "SELECT DISTINCT pid FROM connections_journal WHERE ($1 = '' OR tags @> ARRAY[$1]::text[])"
            + ") p CROSS JOIN LATERAL ("
            + "SELECT tags FROM connections_journal WHERE pid = p.pid AND ($1 = '' OR tags @> ARRAY[$1]::text[])"
            + " ORDER BY sn DESC LIMIT 1"
            + ") n LEFT JOIN LATERAL ("
            + "SELECT max((substring(tag from '^priority-([0-9]+)$'))::int) AS prio "
            + "FROM unnest(n.tags) AS tag WHERE tag ~ '^priority-[0-9]+$'"
            + ") pr ON true "
            + "ORDER BY COALESCE(pr.prio, 0) DESC, p.pid";

    private static NewestPerPidFixture fixture;
    private static ActorSystem system;
    private static Materializer mat;

    @BeforeClass
    public static void startContainer() {
        try {
            fixture = NewestPerPidFixture.start();
        } catch (final Throwable t) {
            Assume.assumeNoException("Docker/Testcontainers unavailable — skipping PostgresKeysetPidStreamsIT", t);
        }
        system = ActorSystem.create("PostgresKeysetPidStreamsIT");
        mat = SystemMaterializer.get(system).materializer();
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
    // ID order: completeness, order, no duplicates
    // =================================================================================================

    @Test
    public void idOrderStreamsDeliverEveryQualifyingPidOnceInBytewisePidOrderAcrossManyWindows() {
        final String entity = "things";
        final String prefix = "thing:complete:";
        final String tag = "complete";
        final int seeded = 500;
        // three events per pid: the oldest carries the tag for even i, the newest for i divisible by 3.
        fixture.seedJournal(entity, prefix, seeded, 3, "CASE "
                + "WHEN s = 3 THEN (CASE WHEN i % 3 = 0 THEN ARRAY['" + tag + "'] ELSE ARRAY['other'] END) "
                + "WHEN s = 1 AND i % 2 = 0 THEN ARRAY['" + tag + "'] "
                + "ELSE ARRAY[]::text[] END");
        // pids whose bytewise (COLLATE "C") order differs from a case-insensitive or locale order.
        final List<String> extra = List.of(prefix + "Zulu", prefix + "alpha", prefix + "Alpha", prefix + "ärger",
                prefix + "zulu");
        extra.forEach(pid -> event(entity, pid, 1L, tag));

        final List<String> anyTagged = Stream.concat(IntStream.rangeClosed(1, seeded)
                                .filter(i -> i % 2 == 0 || i % 3 == 0)
                                .mapToObj(i -> NewestPerPidFixture.pid(prefix, i)),
                        extra.stream())
                .sorted()
                .toList();
        final List<String> newestTagged = Stream.concat(IntStream.rangeClosed(1, seeded)
                                .filter(i -> i % 3 == 0)
                                .mapToObj(i -> NewestPerPidFixture.pid(prefix, i)),
                        extra.stream())
                .sorted()
                .toList();
        final PostgresReadJournal readJournal = fixture.readJournal(entity);

        final List<String> anyTaggedStreamed = run(readJournal.getJournalPidsWithTag(tag, SMALL_BATCH, TIMEOUT, mat,
                false)).stream().filter(pid -> pid.startsWith(prefix)).toList();
        final List<String> newestTaggedStreamed = run(readJournal.getJournalPidsWithTag(tag, SMALL_BATCH, TIMEOUT,
                mat, true)).stream().filter(pid -> pid.startsWith(prefix)).toList();

        assertThat((seeded + extra.size()) / SMALL_BATCH).as("many windows").isGreaterThan(3);
        assertThat(anyTaggedStreamed).as("any event tagged").containsExactlyElementsOf(anyTagged);
        assertThat(newestTaggedStreamed).as("newest event tagged").containsExactlyElementsOf(newestTagged);
        assertThat(anyTaggedStreamed.subList(anyTaggedStreamed.size() - 5, anyTaggedStreamed.size()))
                .as("bytewise: upper case before lower case, 'ä' (0xC3 0xA4) after 'z'")
                .containsExactly(prefix + "Alpha", prefix + "Zulu", prefix + "alpha", prefix + "zulu",
                        prefix + "ärger");
    }

    // =================================================================================================
    // priority order: windows, then a sort in memory
    // =================================================================================================

    @Test
    public void priorityWalkReturnsWhatTheFormerSingleStatementReturnedWithTiesInPidOrder() {
        final String prefix = "connection:prio-char:";
        final String tag = "prio-char";
        final int seeded = 300;
        // newest event (s = 3): untagged with priority 99 for i % 5 = 0 (so s = 2 decides), malformed for i % 11 = 0,
        // two priority tags for i % 13 = 0, else priority i % 7; s = 2 carries (i + 3) % 7; s = 1 carries 50.
        fixture.seedJournal("connections", prefix, seeded, 3, "CASE "
                + "WHEN s = 3 AND i % 5 = 0 THEN ARRAY['priority-99'] "
                + "WHEN s = 3 AND i % 11 = 0 THEN ARRAY['" + tag + "', 'priority-x'] "
                + "WHEN s = 3 AND i % 13 = 0 THEN ARRAY['" + tag + "', 'priority-2', 'priority-12'] "
                + "WHEN s = 3 THEN ARRAY['" + tag + "', 'priority-' || (i % 7)] "
                + "WHEN s = 2 THEN ARRAY['" + tag + "', 'priority-' || ((i + 3) % 7)] "
                + "ELSE ARRAY['" + tag + "', 'priority-50'] END");
        fixture.analyze("connections_journal");

        // the empty tag selects every pid of connections_journal (also other tests' pids): compared all the same,
        // since the reference and the walks read the same table back to back
        for (final String walkTag : List.of(tag, "")) {
            final List<Map<String, String>> reference = fixture.queryAsText(REFERENCE_PRIORITY_SQL, List.of(walkTag));
            final List<String> referencePids = reference.stream().map(row -> row.get("pid")).toList();
            for (final int window : List.of(SMALL_BATCH, PostgresReadJournal.PRIORITY_WALK_WINDOW)) {
                final List<String> streamed = run(fixture.readJournal("connections")
                        .getJournalPidsWithTagOrderedByPriorityTag(walkTag, window, TIMEOUT));
                assertThat(streamed)
                        .as("tag '%s', window %s: the former statement's pids in its priority order, ties in pid "
                                + "order", walkTag, window)
                        .containsExactlyElementsOf(referencePids);
            }
            if (!walkTag.isEmpty()) {
                final Map<String, Integer> priorities = new HashMap<>();
                reference.forEach(row -> priorities.put(row.get("pid"), Integer.parseInt(row.get("prio"))));
                assertThat(referencePids).hasSize(seeded);
                assertThat(priorities.values()).as("the fixture covers the edge cases")
                        .contains(0, 12)
                        .doesNotContain(50, 99);
            }
        }
    }

    // =================================================================================================
    // changes made while a walk runs
    // =================================================================================================

    @Test
    public void idOrderWalkSeesChangesAheadOfItsCursorButNotBehindIt() throws Exception {
        final String entity = "things";
        final String prefix = "thing:live:";
        final String tag = "live";
        fixture.seedJournal(entity, prefix, 100, 1,
                "CASE WHEN i = 80 THEN ARRAY[]::text[] ELSE ARRAY['" + tag + "'] END");
        final PostgresReadJournal readJournal = fixture.readJournal(entity);
        final String insertedBehind = NewestPerPidFixture.pid(prefix, 5) + "a";
        final String insertedAhead = NewestPerPidFixture.pid(prefix, 65) + "a";
        final String deletedAhead = NewestPerPidFixture.pid(prefix, 70);
        final String taggedAhead = NewestPerPidFixture.pid(prefix, 80);

        final SinkQueueWithCancel<String> walk = readJournal.getJournalPidsWithTag(tag, 10, TIMEOUT, mat, false)
                .runWith(Sink.<String>queue().withAttributes(Attributes.inputBuffer(1, 1)), mat);
        final List<String> delivered = new ArrayList<>();
        try {
            for (int i = 0; i < 10; i++) {
                delivered.add(PidStreamTestSupport.pull(walk, TIMEOUT).orElseThrow());
            }
            // the cursor is at pid 10; at most a few windows beyond it can have been read ahead.
            event(entity, insertedBehind, 1L, tag);
            event(entity, insertedAhead, 1L, tag);
            fixture.execute("DELETE FROM things_journal WHERE pid = $1", List.of(deletedAhead));
            event(entity, taggedAhead, 2L, tag);
            PidStreamTestSupport.drain(walk, delivered, TIMEOUT);
        } finally {
            walk.cancel();
        }

        assertThat(delivered).as("strictly increasing: no pid twice").isSorted().doesNotHaveDuplicates();
        assertThat(delivered).contains(insertedAhead, taggedAhead).doesNotContain(insertedBehind, deletedAhead);
        assertThat(delivered).hasSize(100 - 1 /* untagged 80 */ - 1 /* deleted */ + 1 /* inserted */
                + 1 /* tagged later */);

        assertThat(run(readJournal.getJournalPidsWithTag(tag, 10, TIMEOUT, mat, false)))
                .as("the next walk sees the pid inserted behind the cursor")
                .contains(insertedBehind, insertedAhead, taggedAhead)
                .doesNotContain(deletedAhead);
    }

    @Test
    public void priorityWalkDeliversThePidsAsOfTheWindowsThatReadThem() throws Exception {
        final String prefix = "connection:prio-live:";
        final String tag = "prio-live";
        fixture.seedJournal("connections", prefix, 60, 1, "ARRAY['" + tag + "', 'priority-' || (i % 6)]");
        final PostgresReadJournal readJournal = fixture.readJournal("connections");
        final String insertedDuringTheWalk = prefix + "new-top";
        final String deletedDuringTheWalk = NewestPerPidFixture.pid(prefix, 7); // priority 1, not reached yet

        final SinkQueueWithCancel<String> walk = readJournal.getJournalPidsWithTagOrderedByPriorityTag(tag, TIMEOUT)
                .runWith(Sink.<String>queue().withAttributes(Attributes.inputBuffer(1, 1)), mat);
        final List<String> delivered = new ArrayList<>();
        final String moved;
        try {
            for (int i = 0; i < 5; i++) {
                delivered.add(PidStreamTestSupport.pull(walk, TIMEOUT).orElseThrow());
            }
            moved = delivered.get(0);
            event("connections", insertedDuringTheWalk, 1L, tag, "priority-9");
            event("connections", moved, 2L, tag, "priority-0");
            fixture.execute("DELETE FROM connections_journal WHERE pid = $1", List.of(deletedDuringTheWalk));
            PidStreamTestSupport.drain(walk, delivered, TIMEOUT);
        } finally {
            walk.cancel();
        }

        assertThat(delivered)
                .as("the walk is what its windows read: every pid of the time they ran, once, nothing added after "
                        + "the last window")
                .hasSize(60)
                .doesNotHaveDuplicates()
                .contains(deletedDuringTheWalk)
                .doesNotContain(insertedDuringTheWalk)
                .containsOnlyOnce(moved);

        assertThat(run(readJournal.getJournalPidsWithTagOrderedByPriorityTag(tag, TIMEOUT)))
                .as("the next walk starts with the pid inserted during the previous one")
                .startsWith(insertedDuringTheWalk)
                .doesNotContain(deletedDuringTheWalk);
    }

    // =================================================================================================
    // plans
    // =================================================================================================

    @Test
    public void idOrderWindowIsAnIndexRangeScanOnThePrimaryKeyWithoutSortOrTagIndex() {
        seedPlanFixture();
        final String anyTagged = PidStreamTestSupport.productionSql(PLAN_ENTITY,
                readJournal -> readJournal.getJournalPidsWithTag("", 500, TIMEOUT, mat, false), mat);
        final String newestTagged = PidStreamTestSupport.productionSql(PLAN_ENTITY,
                readJournal -> readJournal.getJournalPidsWithTag("", 500, TIMEOUT, mat, true), mat);
        final String midCursor = NewestPerPidFixture.pid(PLAN_PREFIX, PLAN_PIDS / 2);

        for (final String tag : List.of(PLAN_TAG, "")) {
            final String anyPlan = planWithoutSeqScanAndSort(anyTagged, List.of(midCursor, tag, 500));
            final String newestPlan = planWithoutSeqScanAndSort(newestTagged, List.of(midCursor, tag, 500));
            final String anyDefault = fixture.explain(anyTagged, List.of(midCursor, tag, 500));
            final String newestDefault = fixture.explain(newestTagged, List.of(midCursor, tag, 500));
            System.out.printf("[plan] any tagged, tag '%s':%n%s%n[plan] newest tagged, tag '%s':%n%s%n", tag, anyPlan,
                    tag, newestPlan);
            for (final String plan : List.of(anyPlan, newestPlan, anyDefault, newestDefault)) {
                // the window: a primary-key range scan from the cursor, stopped by the LIMIT after batchSize pids.
                assertThat(plan)
                        .as("tag '%s', plan:%n%s", tag, plan)
                        .doesNotContain("Sort")
                        .doesNotContain("tags_idx")
                        .containsPattern("Limit\\n(\\s+->  Result\\n)?\\s+->  Unique\\n\\s+->  Index (Only )?Scan using "
                                + PLAN_JOURNAL + "_pkey on " + PLAN_JOURNAL + "\\n\\s+Index Cond: \\(pid > ");
            }
            assertThat(newestPlan)
                    .as("newest-row step, plan:%n%s", newestPlan)
                    .containsPattern("->  Limit\\n\\s+->  Index (Only )?Scan Backward using " + PLAN_JOURNAL + "_pkey");
            if (!tag.isEmpty()) {
                assertThat(anyPlan)
                        .as("per-pid tag probe on the pid's own primary-key range, plan:%n%s", anyPlan)
                        .containsPattern("SubPlan[^\\n]*\\n\\s+->  Index Scan using " + PLAN_JOURNAL + "_pkey on "
                                + PLAN_JOURNAL + " t\\n\\s+Index Cond: \\(pid = ");
            }
        }
    }

    @Test
    public void priorityWindowIsAnIndexRangeScanOnThePrimaryKeyWithoutSortOrTagIndex() {
        seedPlanFixture();
        final String priorityWindow = PidStreamTestSupport.productionSql(PLAN_ENTITY,
                readJournal -> readJournal.getJournalPidsWithTagOrderedByPriorityTag("", TIMEOUT), mat);
        final String midCursor = NewestPerPidFixture.pid(PLAN_PREFIX, PLAN_PIDS / 2);

        for (final String tag : List.of(PLAN_TAG, "")) {
            final String forced = planWithoutSeqScanAndSort(priorityWindow, List.of(midCursor, tag, 500));
            final String plain = fixture.explain(priorityWindow, List.of(midCursor, tag, 500));
            System.out.printf("[plan] priority window, tag '%s':%n%s%n", tag, forced);
            for (final String plan : List.of(forced, plain)) {
                // the priority is computed per pid and the order made in memory: no sort, no full scan of tagged rows
                assertThat(plan)
                        .as("tag '%s', plan:%n%s", tag, plan)
                        .doesNotContain("Sort")
                        .doesNotContain("tags_idx")
                        .containsPattern("Limit\\n(\\s+->  Result\\n)?\\s+->  Unique\\n\\s+->  Index (Only )?Scan using "
                                + PLAN_JOURNAL + "_pkey on " + PLAN_JOURNAL + "\\n\\s+Index Cond: \\(pid > ")
                        .containsPattern("->  Limit\\n\\s+->  Index (Only )?Scan Backward using " + PLAN_JOURNAL
                                + "_pkey");
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------

    private static final String PLAN_ENTITY = "policies";
    private static final String PLAN_JOURNAL = PLAN_ENTITY + "_journal";
    private static final String PLAN_PREFIX = "policy:plan:";
    private static final String PLAN_TAG = "always-alive";
    private static final int PLAN_PIDS = 20_000;
    private static boolean planFixtureSeeded = false;

    private static synchronized void seedPlanFixture() {
        if (!planFixtureSeeded) {
            fixture.seedJournal(PLAN_ENTITY, PLAN_PREFIX, PLAN_PIDS, 2,
                    "CASE WHEN i % 11 = 0 THEN ARRAY[]::text[] "
                            + "ELSE ARRAY['" + PLAN_TAG + "', 'priority-' || (i % 10)] END");
            fixture.execute("VACUUM ANALYZE " + PLAN_JOURNAL, List.of());
            planFixtureSeeded = true;
        }
    }

    /**
     * The plan as on a table too large for this small fixture's shortcuts: no sequential scan, no sort, and no hashed
     * alternative of an {@code EXISTS} sub-plan (which needs its whole result in {@code work_mem}). The last setting is
     * needed from PostgreSQL 18 on: {@code enable_seqscan = off} no longer makes the planner reject the hashed
     * alternative, whose sequential scan is then only marked "Disabled: true".
     */
    private static String planWithoutSeqScanAndSort(final String sql, final List<Object> binds) {
        return fixture.explain(sql, binds, "enable_seqscan = off", "enable_sort = off", "work_mem = '64kB'");
    }

    private static void event(final String entity, final String pid, final long sn, final String... tags) {
        try {
            fixture.operations(entity)
                    .insertEvents(List.of(new PostgresPersistenceOperations.JournalInsert(
                            pid, sn, "manifest", List.of(tags), "{}")))
                    .toFuture()
                    .get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        } catch (final Exception e) {
            throw new AssertionError("insert of " + pid + "/" + sn + " failed", e);
        }
    }

    private static <T> List<T> run(final Source<T, ?> source) {
        return PidStreamTestSupport.run(source, mat, TIMEOUT);
    }

}
