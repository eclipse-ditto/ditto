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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

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
import org.apache.pekko.testkit.javadsl.TestKit;

/**
 * The ID-order pid streams when the tagged pids are a small minority clustered in one part of the key space, as
 * always-alive policies of one namespace among many are: 500,000 pids with two events each, of which only the last
 * 1 % (pids 495,001 to 500,000) carry the tag, and the journal rows stored in random order (not pid order) so that
 * every row lookup is a random heap access.
 * <p>
 * Every statement of a walk must take a bounded time regardless of where the tagged pids lie: a statement that
 * looked for the next tagged pids would read all 495,000 untagged pids before the cluster in one go, and under
 * {@code statement_timeout} that is the statement that fails, on every retry, every walk. Bounded statements read a
 * window of the next pids in key order and keep the tagged ones, so a window may deliver nothing and the walk moves
 * on.
 * </p>
 * <p>
 * The priority order walks the same windows (its order is made in memory once the last window is read), so its
 * statements are bounded the same way; it no longer reads all tagged rows in one statement.
 * </p>
 */
public final class PostgresClusteredTagLayoutIT {

    private static final String ENTITY = "policies";
    private static final String JOURNAL = ENTITY + "_journal";
    private static final String TAG = "always-alive";
    private static final String PREFIX = "policy:layout:";
    private static final int PIDS = 500_000;
    private static final int FIRST_TAGGED = 495_001;
    private static final int BATCH_SIZE = 500;
    private static final Duration MAX_IDLE_TIME = Duration.ofMinutes(1);
    private static final Duration WALK_TIMEOUT = Duration.ofMinutes(3);
    /** No statement of a walk may take longer; a statement over the untagged block takes seconds. */
    private static final double STATEMENT_BUDGET_MS = 250.0;
    /** Shared buffers one statement may touch: a window of 500 pids with two events each is about 2,500. */
    private static final long STATEMENT_BUFFER_BUDGET = 6_000L;
    private static final Pattern TOP_NODE_BUFFERS = Pattern.compile("Buffers: shared hit=(\\d+)(?: read=(\\d+))?");

    private static NewestPerPidFixture fixture;
    private static ActorSystem system;
    private static Materializer mat;

    @BeforeClass
    public static void startContainer() {
        try {
            fixture = NewestPerPidFixture.start();
        } catch (final Throwable t) {
            Assume.assumeNoException("Docker/Testcontainers unavailable — skipping PostgresClusteredTagLayoutIT", t);
        }
        system = ActorSystem.create("PostgresClusteredTagLayoutIT");
        mat = SystemMaterializer.get(system).materializer();
        // rows in random heap order; the tag only on the last 1 % of the key space.
        fixture.execute("INSERT INTO " + JOURNAL + " (pid, sn, manifest, tags, event) "
                + "SELECT '" + PREFIX + "' || lpad(i::text, 6, '0'), s, 'manifest', "
                + "CASE WHEN i >= " + FIRST_TAGGED + " THEN ARRAY['" + TAG + "'] ELSE ARRAY['other'] END, "
                + "jsonb_build_object('i', i, 's', s) "
                + "FROM generate_series(1, " + PIDS + ") AS i, generate_series(1, 2) AS s ORDER BY random()",
                List.of());
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
    public void anyTaggedEventWalkTakesBoundedTimePerStatementAndDeliversTheCluster() {
        assertBoundedWalk(false);
    }

    @Test
    public void newestEventWalkTakesBoundedTimePerStatementAndDeliversTheCluster() {
        assertBoundedWalk(true);
    }

    @Test
    public void priorityWalkTakesBoundedTimePerStatementAndDeliversTheClusterInPidOrder() {
        final PidStreamTestSupport.StatementRecorder recorder =
                new PidStreamTestSupport.StatementRecorder("FROM " + JOURNAL + " WHERE pid > $1");
        final DittoPostgresClient client = fixture.newClient(recorder::decorate);
        try {
            final PostgresReadJournal readJournal =
                    PostgresReadJournal.of(PostgresPersistenceOperations.of(client, ENTITY));
            final long start = System.nanoTime();
            final List<String> pids = PidStreamTestSupport.run(
                    readJournal.getJournalPidsWithTagOrderedByPriorityTag(TAG, MAX_IDLE_TIME), mat, WALK_TIMEOUT);
            final long totalMillis = (System.nanoTime() - start) / 1_000_000L;
            System.out.printf("[layout] priority order: %d pids in %d statements, total %d ms; per statement "
                            + "p50 %.2f ms, p95 %.2f ms, slowest %.2f ms%n", pids.size(), recorder.nanos().size(),
                    totalMillis, recorder.percentileMillis(50), recorder.percentileMillis(95),
                    recorder.slowestMillis());

            // no priority tags in this layout: every pid has priority 0, so the order is the pid order
            assertThat(pids).containsExactlyElementsOf(IntStream.rangeClosed(FIRST_TAGGED, PIDS)
                    .mapToObj(i -> NewestPerPidFixture.pid(PREFIX, i))
                    .toList());
            assertThat(recorder.nanos())
                    .as("one statement per window of %s pids, plus the final empty window", BATCH_SIZE)
                    .hasSize(PIDS / BATCH_SIZE + 1);
            assertThat(recorder.slowestMillis()).as("slowest statement of the priority walk")
                    .isLessThanOrEqualTo(STATEMENT_BUDGET_MS);
            assertThat(recorder.rows()).allSatisfy(rows -> assertThat(rows).isLessThanOrEqualTo(BATCH_SIZE));
        } finally {
            client.close();
        }
    }

    @Test
    public void statementAtTheStartOfTheUntaggedBlockReadsABoundedNumberOfBuffers() {
        final Map<String, String> statements = new LinkedHashMap<>();
        for (final boolean considerOnlyLatest : List.of(false, true)) {
            statements.put("ID order, considerOnlyLatest=" + considerOnlyLatest, PidStreamTestSupport.productionSql(
                    ENTITY, readJournal -> readJournal.getJournalPidsWithTag(TAG, BATCH_SIZE, MAX_IDLE_TIME, mat,
                            considerOnlyLatest), mat));
        }
        statements.put("priority order", PidStreamTestSupport.productionSql(ENTITY,
                readJournal -> readJournal.getJournalPidsWithTagOrderedByPriorityTag(TAG, MAX_IDLE_TIME), mat));
        statements.forEach((walk, sql) -> {
            final String plan = fixture.explain("ANALYZE, BUFFERS, COSTS OFF, TIMING OFF", sql,
                    List.of("", TAG, BATCH_SIZE));
            System.out.printf("[layout] %s, first statement:%n%s%n", walk, plan);

            assertThat(topNodeBuffers(plan))
                    .as("buffers of the first statement (%s), plan:%n%s", walk, plan)
                    .isLessThanOrEqualTo(STATEMENT_BUFFER_BUDGET);
            assertThat(plan).as("the tag index is not part of the plan:%n%s", plan).doesNotContain("tags_idx");
        });
    }

    private static void assertBoundedWalk(final boolean considerOnlyLatest) {
        final PidStreamTestSupport.StatementRecorder recorder =
                new PidStreamTestSupport.StatementRecorder("FROM " + JOURNAL + " WHERE pid > $1");
        final DittoPostgresClient client = fixture.newClient(recorder::decorate);
        try {
            final PostgresReadJournal readJournal =
                    PostgresReadJournal.of(PostgresPersistenceOperations.of(client, ENTITY));
            final long start = System.nanoTime();
            final List<String> pids = PidStreamTestSupport.run(
                    readJournal.getJournalPidsWithTag(TAG, BATCH_SIZE, MAX_IDLE_TIME, mat, considerOnlyLatest), mat,
                    WALK_TIMEOUT);
            final long totalMillis = (System.nanoTime() - start) / 1_000_000L;
            System.out.printf("[layout] considerOnlyLatest=%s: %d pids in %d statements, total %d ms; per statement "
                            + "p50 %.2f ms, p95 %.2f ms, slowest %.2f ms%n", considerOnlyLatest, pids.size(),
                    recorder.nanos().size(), totalMillis, recorder.percentileMillis(50),
                    recorder.percentileMillis(95), recorder.slowestMillis());

            assertThat(pids).containsExactlyElementsOf(IntStream.rangeClosed(FIRST_TAGGED, PIDS)
                    .mapToObj(i -> NewestPerPidFixture.pid(PREFIX, i))
                    .toList());
            assertThat(recorder.slowestMillis())
                    .as("slowest statement of the walk (considerOnlyLatest=%s)", considerOnlyLatest)
                    .isLessThanOrEqualTo(STATEMENT_BUDGET_MS);
            assertThat(recorder.rows()).allSatisfy(rows -> assertThat(rows).isLessThanOrEqualTo(BATCH_SIZE));
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
