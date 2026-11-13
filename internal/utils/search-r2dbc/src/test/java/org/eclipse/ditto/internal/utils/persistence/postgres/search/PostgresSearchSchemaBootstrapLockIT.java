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
package org.eclipse.ditto.internal.utils.persistence.postgres.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.PostgresSchemaManager;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.testkit.PostgresDbResource;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.R2dbcException;
import io.r2dbc.spi.Result;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * The search-schema bootstrap next to a running per-path index build, against a real PostgreSQL (Testcontainers).
 * {@code LOCK TABLE search_flat IN SHARE UPDATE EXCLUSIVE MODE} in a side session stands in for a running
 * {@code CREATE INDEX CONCURRENTLY} / {@code DROP INDEX CONCURRENTLY} (it is the lock those hold for their whole
 * duration):
 * <ul>
 *     <li>a same-version boot completes, and a writer to {@code search_things} and {@code search_flat} is not
 *     blocked;</li>
 *     <li>a boot that has to re-create a dropped index gives up after the 500 ms {@code lock_timeout}, changes
 *     nothing, and the next boot (lock released) re-creates the index.</li>
 * </ul>
 * Runs under failsafe ({@code *IT}); skipped when no Docker daemon is reachable.
 */
public final class PostgresSearchSchemaBootstrapLockIT {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final PostgresDbResource POSTGRES = new PostgresDbResource();
    private static ConnectionFactory connectionFactory;

    @BeforeClass
    public static void startContainer() {
        try {
            POSTGRES.start();
        } catch (final Throwable t) {
            Assume.assumeNoException("Docker/Testcontainers unavailable — skipping PostgresSearchSchemaBootstrapLockIT",
                    t);
        }
        connectionFactory = POSTGRES.newConnectionFactory();
    }

    @AfterClass
    public static void stopContainer() {
        POSTGRES.stop();
    }

    @Before
    public void reset() {
        runDdl("DROP TABLE IF EXISTS search_flat, search_things, search_sync, schema_version CASCADE");
    }

    @Test
    public void aSameVersionBootCompletesWhileABuildHoldsSearchFlatAndWritersAreNotBlocked() throws Exception {
        bootstrap();
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        try (HeldLock build = HeldLock.on("search_flat", "SHARE UPDATE EXCLUSIVE")) {
            final Future<Duration> boot = executor.submit(() -> timed(PostgresSearchSchemaBootstrapLockIT::bootstrap));
            // Old code: the boot now holds SHARE on search_things and waits for SHARE on search_flat. New code: done.
            awaitDoneOrWaitingForALock(boot);

            final Duration write = timed(() -> writeThing("it:writer"));

            assertThat(boot.get(15, TimeUnit.SECONDS)).as("same-version boot").isLessThan(Duration.ofSeconds(5));
            assertThat(write).as("writer to search_things + search_flat").isLessThan(Duration.ofSeconds(2));
        } finally {
            executor.shutdownNow();
        }
        assertThat(scalar("SELECT count(*) FROM search_flat WHERE thing_id = 'it:writer'")).isEqualTo("1");
    }

    @Test
    public void aBootThatMustRecreateAnIndexGivesUpAfterTheLockTimeoutAndTheNextBootRecreatesIt() {
        bootstrap();
        runDdl("DROP INDEX sf_num");
        final String row = storedRow();

        try (HeldLock build = HeldLock.on("search_flat", "SHARE UPDATE EXCLUSIVE")) {
            final long start = System.nanoTime();
            final Throwable error = catchThrowable(PostgresSearchSchemaBootstrapLockIT::bootstrap);
            final Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

            assertThat(sqlState(error)).as("lock_not_available, got %s", error).isEqualTo("55P03");
            assertThat(elapsed).isBetween(Duration.ofMillis(500), Duration.ofSeconds(3));
            assertThat(indexExists("sf_num")).as("the failed boot changed nothing").isFalse();
            assertThat(storedRow()).isEqualTo(row);
        }

        bootstrap();
        assertThat(indexExists("sf_num")).as("the next boot re-creates the dropped index").isTrue();
    }

    private static void bootstrap() {
        PostgresSchemaManager.of(connectionFactory, PostgresSearchSchema.descriptor(), TIMEOUT).bootstrap();
    }

    /** One thing in one transaction, as the updater writes it; fails if a lock wait exceeds 2 s. */
    private static void writeThing(final String thingId) {
        Mono.usingWhen(Mono.from(connectionFactory.create()),
                        connection -> Mono.from(connection.beginTransaction())
                                .then(update(connection, "SET LOCAL lock_timeout = '2s'"))
                                .then(update(connection, "INSERT INTO search_things (thing_id, namespace, revision, "
                                        + "thing) VALUES ('" + thingId + "', 'it', 1, '{}')"))
                                .then(update(connection, "INSERT INTO search_flat (thing_id, path, wpath, type_rank, "
                                        + "val_text) VALUES ('" + thingId + "', '/attributes/a', '/attributes/a', 3, "
                                        + "'x')"))
                                .then(Mono.from(connection.commitTransaction())),
                        Connection::close)
                .block(TIMEOUT);
    }

    private static Mono<Void> update(final Connection connection, final String sql) {
        return Flux.from(connection.createStatement(sql).execute()).flatMap(Result::getRowsUpdated).then();
    }

    /** Returns once the boot finished, or once some backend waits for a lock in a CREATE INDEX (old code path). */
    private static void awaitDoneOrWaitingForALock(final Future<?> boot) throws InterruptedException {
        final long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (!boot.isDone() && System.nanoTime() < deadline) {
            if (!"0".equals(scalar("SELECT count(*) FROM pg_stat_activity WHERE wait_event_type = 'Lock' "
                    + "AND query LIKE 'CREATE INDEX IF NOT EXISTS %'"))) {
                return;
            }
            Thread.sleep(20);
        }
    }

    private static Duration timed(final Runnable action) {
        final long start = System.nanoTime();
        action.run();
        return Duration.ofNanos(System.nanoTime() - start);
    }

    private static String storedRow() {
        return scalar("SELECT version || '/' || checksum FROM schema_version WHERE component = '"
                + PostgresSearchSchema.COMPONENT + "'");
    }

    /** A side session holding a table lock in an open transaction; {@link #close()} rolls it back. */
    private static final class HeldLock implements AutoCloseable {

        private final Connection connection;

        private HeldLock(final Connection connection) {
            this.connection = connection;
        }

        static HeldLock on(final String table, final String mode) {
            final Connection connection = Mono.from(connectionFactory.create()).block(TIMEOUT);
            Mono.from(connection.beginTransaction())
                    .thenMany(Flux.from(connection.createStatement("LOCK TABLE " + table + " IN " + mode + " MODE")
                            .execute()).flatMap(Result::getRowsUpdated))
                    .then()
                    .block(TIMEOUT);
            return new HeldLock(connection);
        }

        @Override
        public void close() {
            Mono.from(connection.rollbackTransaction())
                    .onErrorResume(error -> Mono.empty())
                    .then(Mono.from(connection.close()))
                    .block(TIMEOUT);
        }
    }

    private static String sqlState(@Nullable final Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof R2dbcException r2dbc && r2dbc.getSqlState() != null) {
                return r2dbc.getSqlState();
            }
        }
        return "none";
    }

    private static boolean indexExists(final String index) {
        return Boolean.parseBoolean(scalar(
                "SELECT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = '" + index + "')"));
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
