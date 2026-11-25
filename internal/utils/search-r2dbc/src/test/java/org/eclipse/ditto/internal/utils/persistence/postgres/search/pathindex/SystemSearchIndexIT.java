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
package org.eclipse.ditto.internal.utils.persistence.postgres.search.pathindex;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import javax.annotation.Nullable;

import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.PostgresSchemaManager;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.testkit.PostgresDbResource;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.PostgresSearchSchema;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.pathindex.PathIndexMaintenance.Outcome;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.pathindex.PathIndexMaintenance.Status;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.Result;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * The system index {@code sf_multi} ({@link SystemSearchIndex#MULTI_VALUED}) against a real PostgreSQL
 * (Testcontainers) with the production search schema: {@link PathIndexMaintenance} builds it concurrently whether or not
 * per-path indexes are configured, repairs it when INVALID, leaves it to newer code during a rolling upgrade, never
 * drops it as an unconfigured per-path index, and needs a session-capable connection; and {@link SystemIndexProbe}, the
 * per-instance gate of the range legs, sees it valid exactly while it is.
 * <p>
 * Skipped when no Docker daemon is reachable.
 * </p>
 */
public final class SystemSearchIndexIT {

    private static final PostgresDbResource POSTGRES = new PostgresDbResource();
    private static ConnectionFactory connectionFactory;

    private static final int THINGS = 500;
    private static final String MULTI = SystemSearchIndex.MULTI_VALUED.indexName();
    private static final Duration RUN_TIMEOUT = Duration.ofMinutes(2L);

    @BeforeClass
    public static void startContainer() {
        try {
            POSTGRES.start();
        } catch (final Throwable t) {
            Assume.assumeNoException("Docker/Testcontainers unavailable — skipping SystemSearchIndexIT", t);
        }
        connectionFactory = POSTGRES.newConnectionFactory();
        PostgresSchemaManager.of(connectionFactory, PostgresSearchSchema.descriptor()).bootstrap();
        loadFixture();
    }

    @AfterClass
    public static void stopContainer() {
        POSTGRES.stop();
    }

    @Before
    public void dropTheSystemIndex() {
        runDdl("DROP INDEX IF EXISTS " + MULTI);
    }

    // ================================================================================================= maintenance

    @Test
    public void theSystemIndexIsBuiltConcurrentlyWithoutAConfiguredPathList() {
        final Outcome first = run(withSystemIndexes(null));

        assertThat(first.status()).isEqualTo(Status.APPLIED);
        assertThat(first.built()).containsExactly(MULTI);
        assertThat(indexValid(MULTI)).isTrue();
        assertThat(indexDefinition(MULTI)).endsWith("USING btree (wpath, thing_id) WHERE (ord = 1)");

        assertThat(run(withSystemIndexes(null)))
                .isEqualTo(new Outcome(Status.NOT_CONFIGURED, List.of(), List.of(), List.of(), List.of()));
    }

    @Test
    public void writersKeepWritingWhileTheSystemIndexIsBuilt() throws Exception {
        // An open writer transaction makes CREATE INDEX CONCURRENTLY wait (after its catalog entry is committed)
        final Connection blocker = openBlockingWriter();
        final CompletableFuture<Outcome> build = withSystemIndexes(null).run().toFuture();
        try {
            awaitBackend("CREATE INDEX CONCURRENTLY");
            // a second writer inserts and commits while the build is running: not blocked
            final long started = System.nanoTime();
            insertFlatRow("it:probe-thing", "/attributes/x", 0);
            assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(2L));
        } finally {
            release(blocker);
        }
        final Outcome outcome = build.get(RUN_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        assertThat(outcome.built()).containsExactly(MULTI);
        assertThat(indexValid(MULTI)).isTrue();
    }

    @Test
    public void anInvalidSystemIndexIsRepairedByTheNextRun() {
        run(withSystemIndexes(null));
        markInvalid(MULTI);

        final Outcome repaired = run(withSystemIndexes(null));

        assertThat(repaired.status()).isEqualTo(Status.APPLIED);
        assertThat(repaired.repaired()).containsExactly(MULTI);
        assertThat(repaired.built()).containsExactly(MULTI);
        assertThat(indexValid(MULTI)).isTrue();
    }

    @Test
    public void aNewerStoredSchemaVersionLeavesTheSystemIndexToTheNewerCode() {
        setStoredVersion(PostgresSearchSchema.VERSION + 1);
        try {
            assertThat(run(withSystemIndexes(null)).status()).isEqualTo(Status.SCHEMA_VERSION_MISMATCH);
            assertThat(indexExists(MULTI)).isFalse();
        } finally {
            setStoredVersion(PostgresSearchSchema.VERSION);
        }
    }

    @Test
    public void withoutASessionCapableConnectionTheSystemIndexIsNotBuilt() {
        final Outcome outcome = run(PathIndexMaintenance.forConnectionFactory(connectionFactory,
                List.of(SystemSearchIndex.MULTI_VALUED), null, false, Duration.ofMillis(200L)));

        assertThat(outcome.status()).isEqualTo(Status.NO_SESSION_CAPABLE_CONNECTION);
        assertThat(indexExists(MULTI)).isFalse();
    }

    @Test
    public void perPathRemovalNeverDropsTheSystemIndex() {
        run(withSystemIndexes(null));

        final Outcome emptyList = run(withSystemIndexes(List.of())); // an explicitly empty per-path list

        assertThat(emptyList.dropped()).doesNotContain(MULTI);
        assertThat(indexValid(MULTI)).isTrue();
    }

    // ================================================================================================= probe

    @Test
    public void theProbeReportsValidOnlyWhileTheIndexIsValid() {
        final SystemIndexProbe probe = SystemIndexProbe.of(SystemSearchIndex.MULTI_VALUED);
        assertThat(probe.getAsBoolean()).as("not valid before the first check").isFalse();
        assertThat(probe.refresh(connectionFactory).block(Duration.ofSeconds(10L))).isFalse();

        run(withSystemIndexes(null));
        assertThat(probe.refresh(connectionFactory).block(Duration.ofSeconds(10L))).isTrue();
        assertThat(probe.getAsBoolean()).isTrue();

        markInvalid(MULTI);
        assertThat(probe.refresh(connectionFactory).block(Duration.ofSeconds(10L))).isFalse();
        assertThat(probe.getAsBoolean()).isFalse();

        runDdl("DROP INDEX " + MULTI);
        assertThat(probe.refresh(connectionFactory).block(Duration.ofSeconds(10L))).isFalse();
    }

    @Test
    public void thePollPicksUpAnIndexBuiltLater() throws Exception {
        final SystemIndexProbe probe = SystemIndexProbe.of(SystemSearchIndex.MULTI_VALUED);
        final Disposable poll = probe.poll(connectionFactory, Duration.ofMillis(100L));
        try {
            run(withSystemIndexes(null));
            awaitTrue(probe::getAsBoolean, "the poll sees sf_multi valid");
        } finally {
            poll.dispose();
        }
    }

    // ================================================================================================= fixture

    /** About 1,800 search_flat rows: a scalar and two filler attributes per thing, a 3-element array on every 5th. */
    private static void loadFixture() {
        runDdl("INSERT INTO search_things (thing_id, namespace, revision, thing, global_read) "
                + "SELECT 'it:t-' || i, 'it', 1, '{}'::jsonb, ARRAY['ditto:reader'] "
                + "FROM generate_series(1, " + THINGS + ") AS i");
        runDdl("INSERT INTO search_flat (thing_id, path, wpath, f_id, ord, type_rank, val_num) "
                + "SELECT 'it:t-' || i, '/attributes/n', '/attributes/n', NULL, 0, 2, i "
                + "FROM generate_series(1, " + THINGS + ") AS i");
        runDdl("INSERT INTO search_flat (thing_id, path, wpath, f_id, ord, type_rank, val_num) "
                + "SELECT 'it:t-' || i, '/attributes/arr', '/attributes/arr', NULL, k, 2, i * 10 + k "
                + "FROM generate_series(1, " + THINGS + ") AS i, generate_series(0, 2) AS k WHERE i % 5 = 0");
        runDdl("INSERT INTO search_flat (thing_id, path, wpath, f_id, ord, type_rank, val_text) "
                + "SELECT 'it:t-' || i, '/attributes/a' || j, '/attributes/a' || j, NULL, 0, 3, md5(i || '-' || j) "
                + "FROM generate_series(1, " + THINGS + ") AS i, generate_series(0, 1) AS j");
    }

    // ================================================================================================= helpers

    private static PathIndexMaintenance withSystemIndexes(@Nullable final List<PathIndex> configured) {
        return PathIndexMaintenance.forConnectionFactory(connectionFactory,
                List.of(SystemSearchIndex.MULTI_VALUED), configured, true, Duration.ofMillis(200L));
    }

    private static Outcome run(final PathIndexMaintenance maintenance) {
        return maintenance.run().block(RUN_TIMEOUT);
    }

    private static boolean indexExists(final String name) {
        return !query("SELECT 1 FROM pg_class WHERE oid = to_regclass('" + name + "')").isEmpty();
    }

    private static boolean indexValid(final String name) {
        final List<String> valid = query("SELECT indisvalid::text FROM pg_index WHERE indexrelid = to_regclass('"
                + name + "')");
        return !valid.isEmpty() && Boolean.parseBoolean(valid.get(0));
    }

    private static String indexDefinition(final String name) {
        return query("SELECT pg_get_indexdef('" + name + "'::regclass)").get(0);
    }

    /** What a failed or cancelled CREATE INDEX CONCURRENTLY leaves behind (the test kit's user is a superuser). */
    private static void markInvalid(final String name) {
        runDdl("UPDATE pg_index SET indisvalid = false WHERE indexrelid = '" + name + "'::regclass");
    }

    private static void setStoredVersion(final int version) {
        runDdl("UPDATE schema_version SET version = " + version + " WHERE component = '"
                + PostgresSearchSchema.COMPONENT + "'");
    }

    private static void insertFlatRow(final String thingId, final String path, final int ord) {
        runDdl("INSERT INTO search_things (thing_id, namespace, revision, thing, global_read) VALUES ('" + thingId
                + "', 'it', 1, '{}'::jsonb, ARRAY['ditto:reader']) ON CONFLICT DO NOTHING");
        runDdl("INSERT INTO search_flat (thing_id, path, wpath, f_id, ord, type_rank, val_num) VALUES ('" + thingId
                + "', '" + path + "', '" + path + "', NULL, " + ord + ", 2, 1) ON CONFLICT DO NOTHING");
    }

    /** Opens a transaction that holds ROW EXCLUSIVE on search_flat, like an in-flight write: CIC waits for it. */
    private static Connection openBlockingWriter() {
        final Connection blocker = Mono.from(connectionFactory.create()).block();
        Mono.from(blocker.beginTransaction()).block();
        execute(blocker, "LOCK TABLE search_flat IN ROW EXCLUSIVE MODE").block();
        return blocker;
    }

    private static void release(final Connection blocker) {
        Mono.from(blocker.rollbackTransaction()).onErrorResume(e -> Mono.empty()).block();
        Mono.from(blocker.close()).onErrorResume(e -> Mono.empty()).block();
    }

    /** Waits until a backend runs a statement starting with the prefix and waits for a lock. */
    private static void awaitBackend(final String statementPrefix) throws InterruptedException {
        final Instant deadline = Instant.now().plusSeconds(30L);
        while (Instant.now().isBefore(deadline)) {
            if (!query("SELECT pid::text FROM pg_stat_activity WHERE query LIKE '" + statementPrefix
                    + "%' AND wait_event_type = 'Lock'").isEmpty()) {
                return;
            }
            Thread.sleep(50L);
        }
        throw new AssertionError("no backend waiting in <" + statementPrefix + "> within 30 s");
    }

    private static void awaitTrue(final BooleanSupplier condition, final String what) throws InterruptedException {
        final Instant deadline = Instant.now().plusSeconds(30L);
        while (Instant.now().isBefore(deadline)) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50L);
        }
        throw new AssertionError("not within 30 s: " + what);
    }

    private static Mono<Void> execute(final Connection connection, final String sql) {
        return Flux.from(connection.createStatement(sql).execute()).flatMap(Result::getRowsUpdated).then();
    }

    private static void runDdl(final String sql) {
        Mono.usingWhen(Mono.from(connectionFactory.create()), connection -> execute(connection, sql),
                        Connection::close)
                .block();
    }

    /** Runs one statement on a fresh connection and returns the first column of every row as text. */
    private static List<String> query(final String sql) {
        final List<String> result = new ArrayList<>();
        Flux.usingWhen(Mono.from(connectionFactory.create()),
                        connection -> Flux.from(connection.createStatement(sql).execute())
                                .flatMap(r -> r.map((row, meta) -> String.valueOf(row.get(0)))),
                        Connection::close)
                .doOnNext(result::add)
                .blockLast();
        return result;
    }

}
