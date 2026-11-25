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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.eclipse.ditto.internal.utils.persistence.api.SnapshotEntry;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.DittoPostgresClient;
import org.eclipse.ditto.internal.utils.persistence.postgres.ops.PostgresPersistenceOperations;
import org.eclipse.ditto.internal.utils.persistence.postgres.ops.RecordingConnectionFactory;
import org.eclipse.ditto.internal.utils.persistence.postgres.ops.StubClientSupport;
import org.eclipse.ditto.internal.utils.persistence.postgres.readjournal.PostgresReadJournal;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.SystemMaterializer;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.testkit.javadsl.TestKit;

/**
 * Real-PostgreSQL checks of the cleanup pid-stream page, the page {@code Cleanup} issues
 * ({@link PostgresPersistenceOperations#getNewestSnapshotHeadsAbove}), in the shape production issues: {@code Cleanup}
 * passes {@code history-retention-duration} (things default 3d) as the minimum age, so the age predicate is active in
 * both steps of the loose index scan, and no pid regex.
 * <p>
 * The fixture holds {@value #PIDS} things pids with 1 to 4 snapshots each (sn 500, 1000, ...; a 2.5 KB payload,
 * stored out of line like real snapshots): 30 % are fresh-only (every snapshot younger than the retention), some are
 * mixed (old snapshots plus a fresh newest one) and the rest are old. It is {@code VACUUM ANALYZE}d, so the planner
 * sees real statistics and a set visibility map, as autovacuum keeps them in production.
 * </p>
 * <ul>
 *     <li>Plan: step 1 is an index-only scan of the primary key that applies the age predicate in the index scan,
 *     step 2 a backward primary-key top-1 that applies it again; no Sort, no temp spill. Checked with the default
 *     planner settings ({@code EXPLAIN (ANALYZE, BUFFERS)}) and, independently of the fixture's statistics, with
 *     sequential scans and sorts disabled (index eligibility).</li>
 *     <li>Result: paging through all pids returns, in pid order, the newest old-enough snapshot of every pid that
 *     has one, and no fresh-only pid.</li>
 *     <li>Payload: the statement selects no snapshot column ({@code EXPLAIN VERBOSE} outputs none); a stream that
 *     requests fields issues the same statement plus the two payload columns.</li>
 * </ul>
 */
public final class PostgresCleanupSnapshotPageIT {

    private static final String ENTITY = "things";
    private static final String SNAPS = ENTITY + "_snaps";
    private static final String PREFIX = "thing:cleanup:";
    private static final int PIDS = 10_000;
    /** Cleanup's reads-per-query default. */
    private static final int PAGE = 100;
    /** things.conf history-retention-duration default. */
    private static final Duration RETENTION = Duration.ofDays(3);
    /** The age predicate as EXPLAIN prints it; a text bind of the interval shows as {@code ('…'::cstring)::interval}. */
    private static final String AGE_PREDICATE =
            "\\(written_at < \\(now\\(\\) - \\(?'259200 seconds'(::cstring\\))?::interval\\)\\)";
    private static final Duration STREAM_TIMEOUT = Duration.ofSeconds(60);

    /*
     * Pid i has 1 + i % 4 snapshots. i % 10 < 3: fresh-only (written a day ago); i % 10 = 9 with at least two
     * snapshots: mixed, only the newest one is fresh; all other rows are 300 days old.
     */
    private static final String SEED_SNAPSHOTS = "INSERT INTO " + SNAPS
            + " (pid, sn, snapshot, lifecycle, written_at) "
            + "SELECT $1 || lpad(i::text, 6, '0'), s * 500, "
            + "jsonb_build_object('thingId', i, '_revision', s * 500, 'attributes', jsonb_build_object('pad', "
            + "(SELECT string_agg(md5(i || ':' || s || ':' || g), '') FROM generate_series(1, 80) AS g))), "
            + "NULL, "
            + "CASE WHEN i % 10 < 3 THEN now() - interval '1 day' + s * interval '1 minute' "
            + "WHEN i % 10 = 9 AND i % 4 > 0 AND s = 1 + i % 4 THEN now() - interval '1 hour' "
            + "ELSE now() - interval '300 days' + s * interval '1 second' END "
            + "FROM generate_series(1, $2) AS i, generate_series(1, 1 + i % 4) AS s";

    private static NewestPerPidFixture fixture;
    private static ActorSystem system;
    private static Materializer mat;
    private static RecordingConnectionFactory.Execution productionPage;

    @BeforeClass
    public static void startAndSeed() {
        try {
            fixture = NewestPerPidFixture.start();
        } catch (final Throwable t) {
            Assume.assumeNoException("Docker/Testcontainers unavailable - skipping PostgresCleanupSnapshotPageIT", t);
        }
        system = ActorSystem.create("PostgresCleanupSnapshotPageIT");
        mat = SystemMaterializer.get(system).materializer();
        fixture.execute(SEED_SNAPSHOTS, List.of(PREFIX, PIDS));
        fixture.execute("VACUUM ANALYZE " + SNAPS, List.of());
        productionPage = firstStatementOfTheCleanupPidStream();
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

    @Test
    public void cleanupBindsTheActiveAgeFilterForBothStepsAndNoPidRegex() {
        // $1 lower bound, $2 pid regex ('' = none), $3 "no age filter", $4 min age, $5 page size
        assertThat(productionPage.binds())
                .isEqualTo(Map.of(0, "", 1, "", 2, false, 3, "259200 seconds", 4, PAGE));
        assertThat(Pattern.compile(Pattern.quote("($3 OR written_at < now() - $4::interval)"))
                .matcher(productionPage.sql()).results().count())
                .as("the age predicate in step 1 and in step 2: %s", productionPage.sql())
                .isEqualTo(2);
    }

    @Test
    public void withDefaultPlannerSettingsThePageIsAnIndexOnlyPidScanAndABackwardTopOneWithoutSortOrSpill() {
        for (final String lowerBound : List.of("", NewestPerPidFixture.pid(PREFIX, PIDS / 2))) {
            final String plan = fixture.explain("ANALYZE, BUFFERS", productionPage.sql(), binds(lowerBound));

            assertProductionShape(plan, "default planner, lower bound '" + lowerBound + "'");
            assertThat(plan)
                    .as("no temp spill, lower bound '%s', plan:%n%s", lowerBound, plan)
                    .doesNotContainPattern("temp (read|written)")
                    .doesNotContain("Disk:")
                    .doesNotContain("external");
            // a full page although every third pid has no old-enough snapshot: step 1 reads on past them
            // (PostgreSQL 18 prints actual rows with two decimals)
            assertThat(plan.lines().findFirst().orElseThrow())
                    .as("full page, lower bound '%s', plan:%n%s", lowerBound, plan)
                    .containsPattern("actual .*rows=" + PAGE + "(\\.00)? loops=1\\)");
        }
    }

    @Test
    public void withSequentialScansAndSortsDisabledThePrimaryKeyPathIsStillChosen() {
        // index ELIGIBILITY, independent of the fixture's statistics: with enable_sort = off a Sort would still
        // appear if no sort-free plan existed
        for (final String lowerBound : List.of("", NewestPerPidFixture.pid(PREFIX, PIDS / 2))) {
            final String plan = fixture.explain(productionPage.sql(), binds(lowerBound),
                    "enable_seqscan = off", "enable_sort = off");
            assertProductionShape(plan, "seqscan and sort disabled, lower bound '" + lowerBound + "'");
        }
    }

    @Test
    public void theCleanupPageSelectsNoSnapshotPayload() {
        assertThat(productionPage.sql())
                .as("the statement Cleanup's pid stream issues")
                .doesNotContain("snapshot")
                .contains("SELECT p.pid, s.sn, s.lifecycle, s.written_at FROM (")
                .contains("SELECT sn, lifecycle, written_at FROM " + SNAPS + " WHERE pid = p.pid");
        for (final String lowerBound : List.of("", NewestPerPidFixture.pid(PREFIX, PIDS / 2))) {
            final String plan = fixture.explain("ANALYZE, BUFFERS, VERBOSE", productionPage.sql(), binds(lowerBound));
            final List<String> outputs = plan.lines().map(String::trim).filter(l -> l.startsWith("Output:")).toList();
            assertThat(outputs)
                    .as("the plan's Output: lines, lower bound '%s', plan:%n%s", lowerBound, plan)
                    .isNotEmpty()
                    .noneMatch(line -> line.contains("snapshot"))
                    .anyMatch(line -> line.contains(".lifecycle"));
        }
    }

    @Test
    public void aStreamThatRequestsFieldsStillSelectsTheSnapshotPayload() {
        final RecordingConnectionFactory.Execution withFields = firstStatementOf(journal ->
                journal.getNewestSnapshotsAbove("", PAGE, true, RETENTION, mat, "_revision"));
        assertThat(withFields.sql()).contains("s.snapshot, ").contains("snapshot::text AS snapshot, ");
        assertThat(withFields.sql().replace("s.snapshot, ", "").replace("snapshot::text AS snapshot, ", ""))
                .as("the same statement as the cleanup page plus exactly the two payload columns")
                .isEqualTo(productionPage.sql());
        assertThat(withFields.binds()).isEqualTo(productionPage.binds());
    }

    @Test
    public void pagingWithTheProductionRetentionReturnsTheNewestOldEnoughSnapshotOfEveryPidThatHasOne() {
        final List<String> expectedWithRetention = new ArrayList<>();
        final List<String> expectedWithoutAgeFilter = new ArrayList<>();
        for (int i = 1; i <= PIDS; i++) {
            final int snapshots = 1 + i % 4;
            final String pid = NewestPerPidFixture.pid(PREFIX, i);
            expectedWithoutAgeFilter.add(pid + "@" + snapshots * 500);
            if (i % 10 >= 3) {
                final boolean mixed = i % 10 == 9 && snapshots > 1;
                expectedWithRetention.add(pid + "@" + (mixed ? snapshots - 1 : snapshots) * 500);
            }
        }

        assertThat(newestSnapshots(RETENTION)).containsExactlyElementsOf(expectedWithRetention);
        assertThat(newestSnapshots(Duration.ZERO)).containsExactlyElementsOf(expectedWithoutAgeFilter);
    }

    // =================================================================================================
    // helpers
    // =================================================================================================

    /**
     * Step 1: {@code Limit -> Group -> Index Only Scan} of the primary key, the age predicate evaluated in that scan;
     * step 2: {@code Limit -> Index Scan Backward} of the primary key per pid with the age predicate; the outer
     * {@code ORDER BY p.pid} is served by step 1's order. Nothing else: no Sort, no sequential or bitmap scan.
     */
    private static void assertProductionShape(final String plan, final String label) {
        final List<String> nodes = nodes(plan);
        assertThat(nodes).as("plan nodes (%s), plan:%n%s", label, plan).hasSize(6);
        assertThat(nodes.get(0)).as(label).isEqualTo("Nested Loop");
        assertThat(nodes.get(1)).as(label).isEqualTo("Limit");
        assertThat(nodes.get(2)).as(label).isEqualTo("Group");
        assertThat(nodes.get(3)).as(label).isEqualTo("Index Only Scan using " + SNAPS + "_pkey on " + SNAPS);
        assertThat(nodes.get(4)).as(label).isEqualTo("Limit");
        assertThat(nodes.get(5)).as(label).matches("Index Scan Backward using " + SNAPS + "_pkey on " + SNAPS + " \\w+");

        // the age predicate is an index condition in both steps: rows too young are skipped inside the index scan
        assertThat(details(plan, "Index Only Scan using " + SNAPS + "_pkey"))
                .as("step 1 applies the age predicate in the index-only scan (%s), plan:%n%s", label, plan)
                .anyMatch(line -> line.matches("Index Cond: \\(\\(pid > '[^']*'::text\\) AND " + AGE_PREDICATE + "\\)"));
        assertThat(details(plan, "Index Scan Backward using " + SNAPS + "_pkey"))
                .as("step 2 applies the age predicate per pid (%s), plan:%n%s", label, plan)
                .anyMatch(line -> line.matches(
                        "Index Cond: \\(\\(pid = " + SNAPS + "\\.pid\\) AND " + AGE_PREDICATE + "\\)"));
        assertThat(plan).as("no sort, no sequential or bitmap scan (%s), plan:%n%s", label, plan)
                .doesNotContainPattern("\\bSort\\b")
                .doesNotContain("Seq Scan")
                .doesNotContain("Bitmap");
    }

    /** The plan's node names in plan order, without costs, actuals and the arrow. */
    private static List<String> nodes(final String plan) {
        final List<String> nodes = new ArrayList<>();
        final List<String> lines = plan.lines().toList();
        for (int i = 0; i < lines.size(); i++) {
            final String trimmed = lines.get(i).trim();
            if (i == 0 || trimmed.startsWith("->")) {
                final String node = trimmed.startsWith("->") ? trimmed.substring(2).trim() : trimmed;
                final int open = node.indexOf("  (");
                nodes.add(open < 0 ? node : node.substring(0, open));
            }
        }
        return nodes;
    }

    /** The trimmed detail lines (conditions, buffers, ...) of the first node whose header contains the marker. */
    private static List<String> details(final String plan, final String nodeMarker) {
        final List<String> lines = plan.lines().toList();
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains(nodeMarker)) {
                final List<String> details = new ArrayList<>();
                for (int j = i + 1; j < lines.size() && !lines.get(j).trim().startsWith("->")
                        && lines.get(j).startsWith(" "); j++) {
                    details.add(lines.get(j).trim());
                }
                return details;
            }
        }
        return List.of();
    }

    /** The production binds with another lower bound. */
    private static List<Object> binds(final String lowerBound) {
        final Map<Integer, Object> binds = new HashMap<>(productionPage.binds());
        binds.put(0, lowerBound);
        final Object[] ordered = new Object[binds.size()];
        binds.forEach((index, value) -> ordered[index] = value);
        return Arrays.asList(ordered);
    }

    /** What Cleanup's pid stream issues first, captured offline with a recording stub. */
    private static RecordingConnectionFactory.Execution firstStatementOfTheCleanupPidStream() {
        return firstStatementOf(journal -> journal.getNewestSnapshotsAbove("", PAGE, true, RETENTION, mat));
    }

    /** The first statement a read-journal stream issues, captured offline with a recording stub. */
    private static RecordingConnectionFactory.Execution firstStatementOf(
            final Function<PostgresReadJournal, Source<SnapshotEntry, NotUsed>> stream) {
        final RecordingConnectionFactory recorder = new RecordingConnectionFactory();
        final DittoPostgresClient recordingClient = StubClientSupport.clientFor(recorder);
        try {
            PidStreamTestSupport.run(stream.apply(PostgresReadJournal.of(
                    PostgresPersistenceOperations.of(recordingClient, ENTITY))), mat, Duration.ofSeconds(30));
        } finally {
            recordingClient.close();
        }
        assertThat(recorder.executions()).hasSize(1);
        return recorder.executions().get(0);
    }

    /** The whole cleanup pid stream as "pid@sn", in stream order. */
    private static List<String> newestSnapshots(final Duration minAge) {
        return PidStreamTestSupport.run(fixture.readJournal(ENTITY)
                                .getNewestSnapshotsAbove("", PAGE, true, minAge, mat), mat, STREAM_TIMEOUT)
                .stream()
                .map((SnapshotEntry e) -> e.getPid().orElseThrow() + "@" + e.getSequenceNumber().orElseThrow())
                .toList();
    }

}
