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
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.ditto.internal.utils.persistence.postgres.client.DittoPostgresClient;
import org.eclipse.ditto.internal.utils.persistence.postgres.ops.PostgresPersistenceOperations;
import org.eclipse.ditto.internal.utils.persistence.postgres.readjournal.PostgresReadJournal;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.SystemMaterializer;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.testkit.javadsl.TestKit;

/**
 * The ID-order pid streams at scale: 110,000 pids, of which 100,000 carry the {@code always-alive} tag (every 11th
 * does not), two events each. The full walk runs without a throttle and an r2dbc-proxy listener records every
 * statement: its duration and its row count.
 * <p>
 * Keyset windows make the cost of a statement independent of its position: the last windows take about as long as
 * the first and read about as many buffers (an {@code OFFSET} walk would read every skipped row again, statement after
 * statement). Each statement returns at most {@code batchSize} rows, which bounds the client memory per statement.
 * {@link PostgresClusteredTagLayoutIT} covers a layout in which the tagged pids are a clustered minority.
 * </p>
 */
public final class PostgresPidStreamScaleIT {

    private static final String ENTITY = "policies";
    private static final String JOURNAL = ENTITY + "_journal";
    private static final String TAG = "always-alive";
    private static final String PREFIX = "policy:scale:";
    private static final int SEEDED_PIDS = 110_000;
    private static final int TAGGED_PIDS = 100_000;
    private static final int EVENTS_PER_PID = 2;
    private static final int BATCH_SIZE = 500;
    private static final int EDGE_WINDOWS = 20;
    private static final Duration MAX_IDLE_TIME = Duration.ofMinutes(1);
    private static final Duration WALK_TIMEOUT = Duration.ofMinutes(2);
    private static final Pattern TOP_NODE_BUFFERS = Pattern.compile("Buffers: shared hit=(\\d+)(?: read=(\\d+))?");

    private static NewestPerPidFixture fixture;
    private static ActorSystem system;
    private static Materializer mat;

    @BeforeClass
    public static void startContainer() {
        try {
            fixture = NewestPerPidFixture.start();
        } catch (final Throwable t) {
            Assume.assumeNoException("Docker/Testcontainers unavailable — skipping PostgresPidStreamScaleIT", t);
        }
        system = ActorSystem.create("PostgresPidStreamScaleIT");
        mat = SystemMaterializer.get(system).materializer();
        fixture.seedJournal(ENTITY, PREFIX, SEEDED_PIDS, EVENTS_PER_PID,
                "CASE WHEN i % 11 = 0 THEN ARRAY[]::text[] ELSE ARRAY['" + TAG + "'] END");
        fixture.execute("VACUUM ANALYZE " + JOURNAL, List.of());
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
    public void anyTaggedEventWalkOfOneHundredThousandPidsHasFlatStatementCost() {
        assertFlatWalk("ID order", readJournal ->
                readJournal.getJournalPidsWithTag(TAG, BATCH_SIZE, MAX_IDLE_TIME, mat, false));
    }

    @Test
    public void newestEventWalkOfOneHundredThousandPidsHasFlatStatementCost() {
        assertFlatWalk("ID order, newest event", readJournal ->
                readJournal.getJournalPidsWithTag(TAG, BATCH_SIZE, MAX_IDLE_TIME, mat, true));
    }

    @Test
    public void windowReadsAsManyBuffersAtTheEndOfTheWalkAsAtItsStart() {
        final String sql = PidStreamTestSupport.productionSql(ENTITY, readJournal ->
                readJournal.getJournalPidsWithTag(TAG, BATCH_SIZE, MAX_IDLE_TIME, mat, false), mat);
        // a cursor at the end of the walk: about 600 pids follow it, so its window is still a full one.
        final String lastCursor = NewestPerPidFixture.pid(PREFIX, SEEDED_PIDS - 600);

        final String firstPlan = fixture.explain("ANALYZE, BUFFERS, COSTS OFF, TIMING OFF", sql,
                List.of("", TAG, BATCH_SIZE));
        final String lastPlan = fixture.explain("ANALYZE, BUFFERS, COSTS OFF, TIMING OFF", sql,
                List.of(lastCursor, TAG, BATCH_SIZE));
        System.out.printf("[scale] first window plan:%n%s%n[scale] last window plan:%n%s%n", firstPlan, lastPlan);

        for (final String plan : List.of(firstPlan, lastPlan)) {
            assertThat(plan)
                    .as("default planner settings, plan:%n%s", plan)
                    // PostgreSQL 18 prints row counts with two decimals ("actual rows=500.00").
                    .containsPattern("Limit \\(actual rows=" + BATCH_SIZE + "(\\.00)? loops=1\\)")
                    .contains("->  Unique")
                    .containsPattern("Index (Only )?Scan using " + JOURNAL + "_pkey on " + JOURNAL)
                    .containsPattern("Index Cond: \\(pid > ")
                    .doesNotContain("Sort")
                    .doesNotContain("tags_idx");
        }
        final long firstBuffers = topNodeBuffers(firstPlan);
        final long lastBuffers = topNodeBuffers(lastPlan);
        assertThat(lastBuffers)
                .as("buffers of the last window (%s) vs the first window (%s): keyset, not OFFSET", lastBuffers,
                        firstBuffers)
                .isLessThanOrEqualTo(2 * firstBuffers + 16);
    }

    private static void assertFlatWalk(final String label,
            final Function<PostgresReadJournal, Source<String, ?>> walk) {
        final PidStreamTestSupport.StatementRecorder recorder =
                new PidStreamTestSupport.StatementRecorder("FROM " + JOURNAL + " WHERE pid > $1");
        final DittoPostgresClient client = fixture.newClient(recorder::decorate);
        try {
            final PostgresReadJournal readJournal =
                    PostgresReadJournal.of(PostgresPersistenceOperations.of(client, ENTITY));
            final long start = System.nanoTime();
            final List<String> pids = PidStreamTestSupport.run(walk.apply(readJournal), mat, WALK_TIMEOUT);
            final Duration total = Duration.ofNanos(System.nanoTime() - start);

            assertThat(pids).hasSize(TAGGED_PIDS);
            assertThat(pids).as("strictly increasing: complete, ordered, no pid twice")
                    .isSortedAccordingTo(Comparator.naturalOrder())
                    .doesNotHaveDuplicates();

            final int fullWindows = SEEDED_PIDS / BATCH_SIZE;
            final List<Integer> rows = recorder.rows();
            assertThat(rows).as("%s full windows and one empty one", fullWindows).hasSize(fullWindows + 1);
            assertThat(rows).as("client memory per statement is bounded by batchSize rows")
                    .allSatisfy(count -> assertThat(count).isLessThanOrEqualTo(BATCH_SIZE));
            assertThat(rows.subList(0, fullWindows)).allSatisfy(count -> assertThat(count).isEqualTo(BATCH_SIZE));

            final List<Long> nanos = recorder.nanos().subList(0, fullWindows);
            final long firstMedian = PidStreamTestSupport.median(nanos.subList(0, EDGE_WINDOWS));
            final long lastMedian = PidStreamTestSupport.median(nanos.subList(fullWindows - EDGE_WINDOWS, fullWindows));
            System.out.printf("[scale] %s: %d pids in %d windows, total %d ms; per statement p50 %.2f ms, p95 %.2f ms, "
                            + "slowest %.2f ms; median of the first %d windows %.2f ms, of the last %d windows "
                            + "%.2f ms%n", label, pids.size(), fullWindows, total.toMillis(),
                    recorder.percentileMillis(50), recorder.percentileMillis(95), recorder.slowestMillis(),
                    EDGE_WINDOWS, firstMedian / 1e6, EDGE_WINDOWS, lastMedian / 1e6);

            assertThat(lastMedian)
                    .as("median statement time of the last %s windows (%s ms) vs the first %s windows (%s ms)",
                            EDGE_WINDOWS, lastMedian / 1e6, EDGE_WINDOWS, firstMedian / 1e6)
                    .isLessThanOrEqualTo(Math.max(4 * firstMedian, firstMedian + TimeUnit.MILLISECONDS.toNanos(20)));
        } finally {
            client.close();
        }
    }

    private static long topNodeBuffers(final String plan) {
        final Matcher matcher = TOP_NODE_BUFFERS.matcher(plan);
        assertThat(matcher.find()).as("buffers in plan:%n%s", plan).isTrue();
        final long hit = Long.parseLong(matcher.group(1));
        final long read = matcher.group(2) == null ? 0L : Long.parseLong(matcher.group(2));
        return hit + read;
    }

}
