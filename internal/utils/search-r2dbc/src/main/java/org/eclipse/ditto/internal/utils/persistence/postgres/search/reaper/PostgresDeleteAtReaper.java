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

import java.util.Objects;
import java.util.function.Function;

import javax.annotation.concurrent.ThreadSafe;

import org.eclipse.ditto.internal.utils.persistence.postgres.client.DittoPostgresClient;

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.Result;
import io.r2dbc.spi.Statement;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * The PostgreSQL {@code delete_at} reaper (plan §3.6) — the in-service replacement for Mongo's TTL index on
 * {@code deleteAt}. C3's namespace {@code purge} only MARKS rows ({@code delete_at = to_timestamp(0)}); reads never
 * filter {@code delete_at} (Mongo parity — only {@code sudoStreamMetadata} does), so marked rows accumulate until
 * something actually deletes them. This class is that something.
 * <p>
 * <strong>One tick, verbatim:</strong>
 * </p>
 * <ol>
 *     <li>open a connection, begin a transaction;</li>
 *     <li>{@code SELECT pg_try_advisory_xact_lock($1)} — non-blocking, transaction-scoped (auto-released on commit or
 *     rollback regardless of what happens to the pooled connection afterwards; see the single-runner note below). If
 *     not acquired, another reaper instance (this JVM or another replica) is mid-tick: skip, commit the (no-op)
 *     transaction, release the connection — {@link TickResult#lockAcquired()} is {@code false};</li>
 *     <li>otherwise, run {@link ReaperDrainLoop#run} with a batch-deleter bound to this connection executing the
 *     plan-mandated SQL ({@link #DELETE_BATCH_SQL}) until drained or the safety valve trips;</li>
 *     <li>commit (releasing the advisory lock) and close the connection.</li>
 * </ol>
 * <p>
 * <strong>Single-runner design (advisory lock vs. cluster singleton):</strong> a Pekko cluster singleton would also
 * guarantee exactly one active reaper cluster-wide, but needs cluster sharding/singleton infrastructure this module has
 * no reason to depend on. A Postgres advisory lock is simpler and dependency-free: it is enforced by the database
 * itself, works whether the search-updater service runs as one instance or many, and needs nothing beyond the
 * connection pool this module already has. <strong>Transaction-scoped, not session-scoped</strong> — deliberately
 * mirroring {@code PostgresSchemaManager}'s {@code ADVISORY_LOCK_KEY} rationale: a session-scoped
 * {@code pg_advisory_lock}/{@code pg_advisory_unlock} pair is unsafe here because {@code r2dbc-pool}'s
 * {@link Connection#close()} returns the physical connection to the pool rather than closing the TCP session — an
 * un-released (or exception-skipped) session lock would then persist on that pooled physical backend connection
 * indefinitely, silently jamming every future reaper tick that happens to borrow it. {@code pg_try_advisory_xact_lock}
 * auto-releases at commit/rollback no matter what, so it cannot leak across pooled-connection reuse.
 * </p>
 * <p>
 * The advisory-lock key ({@link #ADVISORY_LOCK_KEY}) is a dedicated constant, distinct from
 * {@code PostgresSchemaManager.ADVISORY_LOCK_KEY} (the shared first-boot bootstrap lock, {@code 0x0D17_70_5C_4E_4AL}) —
 * the two lock different things (schema bootstrap vs. ongoing reap ticks) and must never collide.
 * </p>
 *
 * @since 4.0.0
 */
@ThreadSafe
public final class PostgresDeleteAtReaper {

    /**
     * The dedicated advisory-lock key for reap ticks. Arbitrary but fixed; deliberately distinct from
     * {@code PostgresSchemaManager.ADVISORY_LOCK_KEY} (the first-boot bootstrap lock) so the two can never collide.
     */
    static final long ADVISORY_LOCK_KEY = 0x0D17_70_5E_47_C4L;

    /** Non-blocking, transaction-scoped advisory-lock attempt (see the class javadoc for why xact-scoped). */
    static final String TRY_LOCK_SQL = "SELECT pg_try_advisory_xact_lock($1)";

    /**
     * The plan-mandated batched-delete SQL shape (plan §3.6): PostgreSQL's {@code DELETE} has no {@code LIMIT} clause,
     * so the subselect form is required. {@code FOR UPDATE SKIP LOCKED} means a reap batch never stalls behind (or is
     * blocked by) an in-flight {@code ThingUpdater} transaction holding a row lock on a marked thing — that row is
     * simply skipped this batch and picked up once it is unlocked (this tick's later batch, or the next tick). The
     * {@code ON DELETE CASCADE} FK from {@code search_flat} clears that thing's flat rows automatically.
     */
    static final String DELETE_BATCH_SQL =
            "DELETE FROM search_things WHERE thing_id IN ("
                    + "SELECT thing_id FROM search_things WHERE delete_at < now() LIMIT $1 FOR UPDATE SKIP LOCKED)";

    private final ConnectionFactory connectionFactory;
    private final int batchSize;
    private final int maxBatchesPerTick;

    private PostgresDeleteAtReaper(final ConnectionFactory connectionFactory, final int batchSize,
            final int maxBatchesPerTick) {
        this.connectionFactory = connectionFactory;
        this.batchSize = batchSize;
        this.maxBatchesPerTick = maxBatchesPerTick;
    }

    /**
     * @param client the shared PostgreSQL client (one connection pool per service, via {@code PostgresClientExtension}).
     * @param config the reaper's interval/batch/max-batches configuration.
     * @return the reaper.
     */
    public static PostgresDeleteAtReaper of(final DittoPostgresClient client,
            final PostgresDeleteAtReaperConfig config) {
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(config, "config");
        return new PostgresDeleteAtReaper(client.getConnectionPool(), config.getBatchSize(),
                config.getMaxBatchesPerTick());
    }

    /**
     * @param connectionFactory a connection factory (a pool, or a plain testkit factory) whose connections back each
     * tick.
     * @param config the reaper's batch/max-batches configuration (the interval is read only by the actor).
     * @return the reaper.
     */
    static PostgresDeleteAtReaper forConnectionFactory(final ConnectionFactory connectionFactory,
            final PostgresDeleteAtReaperConfig config) {
        return new PostgresDeleteAtReaper(Objects.requireNonNull(connectionFactory, "connectionFactory"),
                config.getBatchSize(), config.getMaxBatchesPerTick());
    }

    /**
     * Runs one reap tick (see the class javadoc for the exact steps). Never throws synchronously; any failure (a
     * connection error, a statement error) surfaces as a {@link Mono} error for the caller ({@code
     * PostgresDeleteAtReaperActor}) to log and recover from — the whole tick's transaction is rolled back on any error,
     * so a failed tick never leaves a partial deletion committed and the same backlog is retried on the next tick.
     *
     * @return a {@link Mono} of the tick's outcome.
     */
    public Mono<TickResult> tick() {
        return Mono.usingWhen(
                Mono.from(connectionFactory.create()),
                connection -> Mono.from(connection.beginTransaction())
                        .then(tryAcquireLock(connection))
                        .flatMap(acquired -> {
                            if (!acquired) {
                                return Mono.just(TickResult.lockNotAcquired());
                            }
                            return ReaperDrainLoop.run(limit -> deleteBatch(connection, limit), batchSize,
                                            maxBatchesPerTick)
                                    .map(drain -> TickResult.reaped(drain.totalDeleted(), drain.batchesRun(),
                                            drain.safetyValveHit()));
                        }),
                connection -> Mono.from(connection.commitTransaction()).then(Mono.from(connection.close())),
                (connection, error) -> Mono.from(connection.rollbackTransaction())
                        .onErrorComplete()
                        .then(Mono.from(connection.close())),
                connection -> Mono.from(connection.rollbackTransaction())
                        .onErrorComplete()
                        .then(Mono.from(connection.close())));
    }

    private static Mono<Boolean> tryAcquireLock(final Connection connection) {
        final Statement statement = connection.createStatement(TRY_LOCK_SQL);
        statement.bind(0, ADVISORY_LOCK_KEY);
        return Flux.from(statement.execute())
                .flatMap(result -> result.map((row, meta) -> row.get(0, Boolean.class)))
                .next()
                .defaultIfEmpty(Boolean.FALSE);
    }

    private static Mono<Long> deleteBatch(final Connection connection, final int limit) {
        final Statement statement = connection.createStatement(DELETE_BATCH_SQL);
        statement.bind(0, limit);
        return Flux.from(statement.execute())
                .flatMap(Result::getRowsUpdated)
                .map(Number::longValue)
                .reduce(0L, Long::sum);
    }

    /**
     * The outcome of one {@link #tick()}.
     *
     * @param lockAcquired whether this instance acquired the advisory lock this tick (if {@code false}, no work was
     * attempted — another reaper instance is mid-tick).
     * @param reapedCount the total number of rows deleted (0 if the lock was not acquired, or if acquired but nothing
     * matched).
     * @param batchesRun how many batches ran (0 if the lock was not acquired).
     * @param safetyValveHit whether the {@code maxBatchesPerTick} safety valve stopped the drain before it fully
     * drained (backlog may remain for the next tick).
     */
    public record TickResult(boolean lockAcquired, long reapedCount, int batchesRun, boolean safetyValveHit) {

        private static final TickResult LOCK_NOT_ACQUIRED = new TickResult(false, 0L, 0, false);

        static TickResult lockNotAcquired() {
            return LOCK_NOT_ACQUIRED;
        }

        static TickResult reaped(final long reapedCount, final int batchesRun, final boolean safetyValveHit) {
            return new TickResult(true, reapedCount, batchesRun, safetyValveHit);
        }
    }

}
