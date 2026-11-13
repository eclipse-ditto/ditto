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
package org.eclipse.ditto.internal.utils.persistence.postgres.search.reaper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.PostgresSchemaManager;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.testkit.PostgresDbResource;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.PostgresSearchSchema;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.reaper.PostgresDeleteAtReaper.TickResult;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.Result;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Integration test for {@link PostgresDeleteAtReaper} — the {@code delete_at} reaper — against a real PostgreSQL
 * (PG 16) via Testcontainers: multi-batch drain, the safety valve, cascade clearing of flat rows, {@code FOR UPDATE
 * SKIP LOCKED} row-level concurrency with an in-flight (simulated {@code ThingUpdater}) transaction, and the
 * advisory-lock single-runner guarantee against a genuinely concurrent second attempt.
 * <p>
 * Rows are inserted with plain SQL (not through the C2 write engine) — this IT is about the reaper's own SQL/lock
 * semantics, which do not depend on how a row got there.
 * </p>
 * <p>
 * Skipped offline / when no Docker daemon is reachable (the {@link #startContainer() Assume} guard turns an
 * unreachable Docker into a skip, not a failure).
 * </p>
 */
public final class PostgresPurgeReaperIT {

    private static final PostgresDbResource POSTGRES = new PostgresDbResource();
    private static ConnectionFactory connectionFactory;

    @BeforeClass
    public static void startContainer() {
        try {
            POSTGRES.start();
        } catch (final Throwable t) {
            Assume.assumeNoException("Docker/Testcontainers unavailable — skipping PostgresPurgeReaperIT", t);
        }
        connectionFactory = POSTGRES.newConnectionFactory();
    }

    @AfterClass
    public static void stopContainer() {
        POSTGRES.stop();
    }

    @Before
    public void bootstrapSchema() {
        runDdl("DROP TABLE IF EXISTS search_flat, search_things, search_sync, schema_version CASCADE");
        PostgresSchemaManager.of(connectionFactory, PostgresSearchSchema.descriptor()).bootstrap();
    }

    @After
    public void clear() {
        runDdl("TRUNCATE search_flat, search_things, search_sync");
    }

    // ============================================================================================================
    // multi-batch drain within one tick + survivors
    // ============================================================================================================

    @Test
    public void markedRowsAreReapedInMultipleBatchesWhileUnmarkedAndFutureRowsSurvive() {
        // 7 marked (mixed epoch-0 and past-now), 1 unmarked, 2 future -- 10 total.
        insertThing("ns", "epoch-1", epochZeroMarker());
        insertThing("ns", "epoch-2", epochZeroMarker());
        insertThing("ns", "epoch-3", epochZeroMarker());
        insertThing("ns", "past-1", pastMarker());
        insertThing("ns", "past-2", pastMarker());
        insertThing("ns", "past-3", pastMarker());
        insertThing("ns", "past-4", pastMarker());
        insertThing("ns", "unmarked", null);
        insertThing("ns", "future-1", futureMarker());
        insertThing("ns", "future-2", futureMarker());

        final TickResult result = tick(reaper(3, 50));

        assertThat(result.lockAcquired()).isTrue();
        assertThat(result.reapedCount()).isEqualTo(7L);
        assertThat(result.batchesRun()).isEqualTo(3); // ceil(7 / 3)
        assertThat(result.safetyValveHit()).isFalse();

        assertThat(countSearchThings()).isEqualTo(3L);
        assertThat(thingExists("ns", "unmarked")).isTrue();
        assertThat(thingExists("ns", "future-1")).isTrue();
        assertThat(thingExists("ns", "future-2")).isTrue();
        assertThat(thingExists("ns", "epoch-1")).isFalse();
        assertThat(thingExists("ns", "past-1")).isFalse();
    }

    // ============================================================================================================
    // cascade clears flat rows
    // ============================================================================================================

    @Test
    public void reapingAThingCascadesToDeleteItsFlatRows() {
        insertThing("ns", "reaped", epochZeroMarker());
        insertFlatRow("ns:reaped", "/a", "/a", 0, "abc");
        insertFlatRow("ns:reaped", "/b", "/b", 0, "def");
        insertThing("ns", "kept", null);
        insertFlatRow("ns:kept", "/a", "/a", 0, "xyz");

        final TickResult result = tick(reaper(100, 50));

        assertThat(result.reapedCount()).isEqualTo(1L);
        assertThat(thingExists("ns", "reaped")).isFalse();
        assertThat(countFlatRows("ns:reaped")).isEqualTo(0L);
        assertThat(thingExists("ns", "kept")).isTrue();
        assertThat(countFlatRows("ns:kept")).isEqualTo(1L);
    }

    // ============================================================================================================
    // safety valve: bounded batches per tick, backlog finished on the next tick
    // ============================================================================================================

    @Test
    public void safetyValveLimitsBatchesPerTickAndTheBacklogIsFinishedOnTheNextTick() {
        for (int i = 0; i < 10; i++) {
            insertThing("ns", "row-" + i, epochZeroMarker());
        }
        final PostgresDeleteAtReaper reaper = reaper(2, 3);

        final TickResult first = tick(reaper);
        assertThat(first.reapedCount()).isEqualTo(6L); // 3 batches * 2
        assertThat(first.batchesRun()).isEqualTo(3);
        assertThat(first.safetyValveHit()).isTrue();
        assertThat(countSearchThings()).isEqualTo(4L);

        final TickResult second = tick(reaper);
        assertThat(second.reapedCount()).isEqualTo(4L);
        assertThat(second.safetyValveHit()).isFalse();
        assertThat(countSearchThings()).isZero();
    }

    // ============================================================================================================
    // FOR UPDATE SKIP LOCKED: a row held by an in-flight (simulated ThingUpdater) transaction is skipped, then
    // reaped once released.
    // ============================================================================================================

    @Test
    public void aRowLockedByAConcurrentTransactionIsSkippedThenReapedOnceUnlocked() {
        insertThing("ns", "locked", epochZeroMarker());
        insertThing("ns", "free-1", epochZeroMarker());
        insertThing("ns", "free-2", epochZeroMarker());

        final Connection lockHolder = Mono.from(connectionFactory.create()).block();
        try {
            // Simulate an in-flight ThingUpdater transaction holding a row lock on "locked" -- genuinely holds it for
            // the duration of the tick below (no commit/rollback until we explicitly release it further down).
            Mono.from(lockHolder.beginTransaction()).block();
            Flux.from(lockHolder.createStatement(
                            "SELECT * FROM search_things WHERE thing_id = 'ns:locked' FOR UPDATE").execute())
                    .flatMap(result -> result.map((row, meta) -> row))
                    .blockLast();

            final PostgresDeleteAtReaper reaper = reaper(100, 50);
            final TickResult whileLocked = tick(reaper);

            assertThat(whileLocked.lockAcquired()).isTrue();
            assertThat(whileLocked.reapedCount()).as("SKIP LOCKED excludes the row held by the concurrent tx")
                    .isEqualTo(2L);
            assertThat(thingExists("ns", "locked"))
                    .as("the locked row survives this tick").isTrue();
            assertThat(thingExists("ns", "free-1")).isFalse();
            assertThat(thingExists("ns", "free-2")).isFalse();

            // release the row lock.
            Mono.from(lockHolder.commitTransaction()).block();

            final TickResult afterUnlock = tick(reaper);
            assertThat(afterUnlock.reapedCount()).as("the previously-locked row is reaped on the following tick")
                    .isEqualTo(1L);
            assertThat(thingExists("ns", "locked")).isFalse();
        } finally {
            Mono.from(lockHolder.close()).block();
        }
    }

    // ============================================================================================================
    // advisory-lock single-runner: a concurrent transaction genuinely holding the SAME advisory-lock key blocks a
    // tick from doing any work; releasing it lets the very next tick proceed.
    // ============================================================================================================

    @Test
    public void aConcurrentHolderOfTheAdvisoryLockPreventsATickFromReapingAnything() {
        insertThing("ns", "a", epochZeroMarker());
        insertThing("ns", "b", epochZeroMarker());

        final Connection lockHolder = Mono.from(connectionFactory.create()).block();
        try {
            // Genuinely hold the SAME advisory-lock key the reaper uses, in an open (uncommitted) transaction --
            // simulating another reaper instance mid-tick.
            Mono.from(lockHolder.beginTransaction()).block();
            Flux.from(lockHolder.createStatement("SELECT pg_advisory_xact_lock($1)")
                            .bind(0, PostgresDeleteAtReaper.ADVISORY_LOCK_KEY)
                            .execute())
                    .flatMap(result -> result.map((row, meta) -> row))
                    .blockLast();

            final PostgresDeleteAtReaper reaper = reaper(100, 50);
            final TickResult whileHeld = tick(reaper);

            assertThat(whileHeld.lockAcquired()).as("pg_try_advisory_xact_lock must fail non-blockingly").isFalse();
            assertThat(whileHeld.reapedCount()).isZero();
            assertThat(whileHeld.batchesRun()).isZero();
            assertThat(countSearchThings()).as("nothing was reaped while the lock was held elsewhere").isEqualTo(2L);

            // release the advisory lock.
            Mono.from(lockHolder.commitTransaction()).block();

            final TickResult afterRelease = tick(reaper);
            assertThat(afterRelease.lockAcquired()).isTrue();
            assertThat(afterRelease.reapedCount()).isEqualTo(2L);
            assertThat(countSearchThings()).isZero();
        } finally {
            Mono.from(lockHolder.close()).block();
        }
    }

    /**
     * A genuinely concurrent race between two reap attempts (as opposed to the deterministic construction above):
     * two threads call {@code tick()} against the same backlog at (as close as achievable to) the same instant. The
     * advisory lock guarantees that AT MOST one of them reaps -- summed across both, the whole backlog is reaped
     * exactly once (never double-counted, never lost).
     */
    @Test
    public void twoGenuinelyConcurrentTicksNeverBothReapTheSameBacklog() throws Exception {
        for (int i = 0; i < 5; i++) {
            insertThing("ns", "race-" + i, epochZeroMarker());
        }
        final PostgresDeleteAtReaper reaperA = reaper(100, 50);
        final PostgresDeleteAtReaper reaperB = reaper(100, 50);

        final AtomicReference<TickResult> resultA = new AtomicReference<>();
        final AtomicReference<TickResult> resultB = new AtomicReference<>();
        final Thread threadA = new Thread(() -> resultA.set(reaperA.tick().block(Duration.ofSeconds(30L))));
        final Thread threadB = new Thread(() -> resultB.set(reaperB.tick().block(Duration.ofSeconds(30L))));

        threadA.start();
        threadB.start();
        threadA.join(TimeUnit.SECONDS.toMillis(30L));
        threadB.join(TimeUnit.SECONDS.toMillis(30L));

        final TickResult a = resultA.get();
        final TickResult b = resultB.get();
        assertThat(a).as("thread A must have completed").isNotNull();
        assertThat(b).as("thread B must have completed").isNotNull();

        final long totalReaped = a.reapedCount() + b.reapedCount();
        final int lockAcquisitions = (a.lockAcquired() ? 1 : 0) + (b.lockAcquired() ? 1 : 0);

        assertThat(lockAcquisitions).as("the advisory lock allows at most one tick to actually acquire it here "
                + "(a genuine race may also serialise both onto different points in time, both acquiring in turn -- "
                + "the invariant that matters is the total below)").isBetween(1, 2);
        assertThat(totalReaped).as("the 5-row backlog is reaped exactly once in total, never double-counted")
                .isEqualTo(5L);
        assertThat(countSearchThings()).isZero();
    }

    // ============================================================================================================
    // helpers
    // ============================================================================================================

    private static PostgresDeleteAtReaper reaper(final int batchSize, final int maxBatchesPerTick) {
        final PostgresDeleteAtReaperConfig config = DefaultPostgresDeleteAtReaperConfig.of(ConfigFactory.parseString(
                "postgresql.search.reaper { batch-size = " + batchSize + ", max-batches-per-tick = "
                        + maxBatchesPerTick + " }"));
        return PostgresDeleteAtReaper.forConnectionFactory(connectionFactory, config);
    }

    private static TickResult tick(final PostgresDeleteAtReaper reaper) {
        final TickResult result = reaper.tick().block(Duration.ofSeconds(30L));
        assertThat(result).as("tick() must complete").isNotNull();
        return result;
    }

    private static String epochZeroMarker() {
        return "to_timestamp(0)";
    }

    private static String pastMarker() {
        return "now() - interval '1 hour'";
    }

    private static String futureMarker() {
        return "now() + interval '1 hour'";
    }

    /**
     * Inserts a minimal {@code search_things} row. {@code deleteAtSqlExpression} is a raw SQL expression (one of
     * {@link #epochZeroMarker()}/{@link #pastMarker()}/{@link #futureMarker()}, or {@code null} for unmarked) —
     * interpolated directly since it is always one of the fixed literals above, never test input.
     */
    private static void insertThing(final String namespace, final String name,
            final String deleteAtSqlExpression) {
        final String thingId = namespace + ":" + name;
        final String deleteAtValue = deleteAtSqlExpression == null ? "NULL" : deleteAtSqlExpression;
        runDdl("INSERT INTO search_things (thing_id, namespace, revision, thing, delete_at) VALUES ('"
                + thingId + "', '" + namespace + "', 1, '{}'::jsonb, " + deleteAtValue + ")");
    }

    private static void insertFlatRow(final String thingId, final String path, final String wpath, final int ord,
            final String valText) {
        runDdl("INSERT INTO search_flat (thing_id, path, wpath, ord, type_rank, val_text) VALUES ('"
                + thingId + "', '" + path + "', '" + wpath + "', " + ord + ", 1, '" + valText + "')");
    }

    private static boolean thingExists(final String namespace, final String name) {
        return "1".equals(scalar("SELECT count(*) FROM search_things WHERE thing_id = '"
                + namespace + ":" + name + "'"));
    }

    private static long countSearchThings() {
        return Long.parseLong(scalar("SELECT count(*) FROM search_things"));
    }

    private static long countFlatRows(final String thingId) {
        return Long.parseLong(scalar("SELECT count(*) FROM search_flat WHERE thing_id = '" + thingId + "'"));
    }

    private static String scalar(final String sql) {
        return Mono.usingWhen(Mono.from(connectionFactory.create()),
                        conn -> Flux.from(conn.createStatement(sql).execute())
                                .flatMap(result -> result.map((row, meta) -> String.valueOf(row.get(0))))
                                .next(),
                        Connection::close)
                .block();
    }

    private static void runDdl(final String sql) {
        Mono.usingWhen(Mono.from(connectionFactory.create()),
                        conn -> Flux.from(conn.createStatement(sql).execute())
                                .flatMap(Result::getRowsUpdated)
                                .then(),
                        Connection::close)
                .block();
    }

}
