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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.PostgresSchemaManager;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.testkit.PostgresDbResource;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.PostgresSearchSchema;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.pathindex.PathIndexMaintenance.Outcome;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.pathindex.PathIndexMaintenance.Status;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.PostgresSortClause;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.SearchQueryAssembler;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.ast.RenderedSql;
import org.eclipse.ditto.rql.query.SortDirection;
import org.eclipse.ditto.rql.query.SortOption;
import org.eclipse.ditto.rql.query.criteria.Criteria;
import org.eclipse.ditto.rql.query.criteria.CriteriaFactory;
import org.eclipse.ditto.rql.query.expression.ThingsFieldExpressionFactory;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.reactivestreams.Publisher;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryMetadata;
import io.r2dbc.spi.Result;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * {@link PathIndexMaintenance} against a real PostgreSQL 16 (Testcontainers) with the production search schema: the
 * concurrent build, that the planner uses a scoped index for {@code ilike} on its path (and only there), the repair of
 * an INVALID index left by a killed or cancelled build, removal, the lock, the schema-version guard and the session
 * settings.
 * <p>
 * Plans are EXPLAINs of the production statements ({@link SearchQueryAssembler}) under
 * {@code plan_cache_mode = force_custom_plan} (the shipped search pool setting), with {@code max_parallel_workers = 0}
 * for finds (production runs them leader-only) and {@code enable_seqscan = off} (index eligibility on a small fixture;
 * it does not favour one index over another).
 * </p>
 * <p>
 * Skipped when no Docker daemon is reachable.
 * </p>
 */
public final class PathIndexMaintenanceIT {

    private static final PostgresDbResource POSTGRES = new PostgresDbResource();
    private static ConnectionFactory connectionFactory;

    private static final int THINGS = 20_000;
    private static final List<String> SUBJECTS = List.of("ditto:reader");
    private static final Duration RUN_TIMEOUT = Duration.ofMinutes(2L);

    private static final ThingsFieldExpressionFactory FEF = ThingsFieldExpressionFactory.of(Map.of(
            "thingId", "_id",
            "namespace", "_namespace"));
    private static final CriteriaFactory CF = CriteriaFactory.getInstance();

    private static final PathIndex PROP0 = PathIndex.of("features/*/properties/prop0", PathIndexKind.TRIGRAM, FEF);
    private static final PathIndex NAME = PathIndex.of("attributes/name", PathIndexKind.TRIGRAM, FEF);
    private static final PathIndex QUOTED = PathIndex.of("attributes/it's/a\\b", PathIndexKind.TRIGRAM, FEF);

    @BeforeClass
    public static void startContainer() {
        try {
            POSTGRES.start();
        } catch (final Throwable t) {
            Assume.assumeNoException("Docker/Testcontainers unavailable — skipping PathIndexMaintenanceIT", t);
        }
        connectionFactory = POSTGRES.newConnectionFactory();
        PostgresSchemaManager.of(connectionFactory, PostgresSearchSchema.descriptor()).bootstrap();
        loadFixture();
        query("VACUUM ANALYZE search_things");
        query("VACUUM ANALYZE search_flat");
    }

    @AfterClass
    public static void stopContainer() {
        POSTGRES.stop();
    }

    @After
    public void dropManagedIndexes() {
        for (final String name : managedIndexes().keySet()) {
            query("DROP INDEX " + name);
        }
    }

    // ================================================================================================= build

    @Test
    public void buildsTheConfiguredIndexesConcurrentlyAndIsUpToDateAfterwards() {
        final Outcome first = run(maintenance(List.of(PROP0, NAME)));

        assertThat(first.status()).isEqualTo(Status.APPLIED);
        assertThat(first.built()).containsExactly(PROP0.name(), NAME.name());
        assertThat(first.failed()).isEmpty();
        assertThat(managedIndexes()).containsOnly(Map.entry(PROP0.name(), true), Map.entry(NAME.name(), true));
        assertThat(indexDefinition(PROP0.name()))
                .isEqualTo("CREATE INDEX " + PROP0.name() + " ON public.search_flat USING gin "
                        + "(val_text COLLATE \"C.utf8\" gin_trgm_ops) "
                        + "WHERE ((wpath = '/features/*/properties/prop0'::text) AND (val_text IS NOT NULL))");
        // the same indexed expression as the table-wide sf_trgm
        assertThat(indexDefinition("sf_trgm"))
                .contains("USING gin (val_text COLLATE \"C.utf8\" gin_trgm_ops) WHERE (val_text IS NOT NULL)");

        final Outcome second = run(maintenance(List.of(PROP0, NAME)));
        assertThat(second).isEqualTo(new Outcome(Status.UP_TO_DATE, List.of(), List.of(), List.of(), List.of()));
    }

    // ================================================================================================= plans

    @Test
    public void ilikeOnTheConfiguredPathUsesTheScopedIndexWithoutAnExistsLeg() {
        run(maintenance(List.of(PROP0)));

        final String plan = explainFind(CF.fieldCriteria(FEF.filterBy("features/*/properties/prop0"),
                CF.ilike("*UILDIN*")));

        assertScopedIndexCondition(plan, PROP0.name(), "~~* '%UILDIN%'");
        assertThat(plan).as("plan:%n%s", plan)
                .doesNotContain("on sf_trgm")
                .doesNotContain("sf_exists")
                .doesNotContain("BitmapAnd");
    }

    @Test
    public void caseSensitiveTrigramLikeOnTheConfiguredPathUsesTheScopedIndexToo() {
        run(maintenance(List.of(PROP0)));

        final String plan = explainFind(CF.fieldCriteria(FEF.filterBy("features/*/properties/prop0"),
                CF.like("*uildin*")));

        assertScopedIndexCondition(plan, PROP0.name(), "~~ '%uildin%'");
    }

    @Test
    public void ilikeOnAnotherPathDoesNotUseTheScopedIndex() {
        run(maintenance(List.of(PROP0)));

        final String plan = explainFind(CF.fieldCriteria(FEF.filterBy("attributes/name"), CF.ilike("*UILDIN*")));

        assertThat(plan).as("plan:%n%s", plan)
                .doesNotContain(PROP0.name())
                .contains("Bitmap Index Scan on sf_trgm");
    }

    @Test
    public void escapedPathLiteralEqualsTheBoundWpath() {
        run(maintenance(List.of(QUOTED)));

        final String plan = explainFind(CF.fieldCriteria(FEF.filterBy("attributes/it's/a\\b"),
                CF.ilike("*UILDIN*")));

        assertScopedIndexCondition(plan, QUOTED.name(), "~~* '%UILDIN%'");
    }

    @Test
    public void aGenericPlanCannotProveThePartialPredicate() {
        // Why the search pool forces custom plans: with the wpath as an unknown $1 the predicate is not provable.
        run(maintenance(List.of(PROP0)));
        final RenderedSql count = SearchQueryAssembler.count(
                CF.fieldCriteria(FEF.filterBy("features/*/properties/prop0"), CF.ilike("*UILDIN*")), null, 0, 0);
        assertThat(count.bindValues()).containsExactly("/features/*/properties/prop0", "%UILDIN%");

        // A server-side prepared statement, as the plancache sees it. Over JDBC: r2dbc would treat $1 as its own bind.
        final Function<String, String> explainPrepared = planCacheMode -> {
            try (java.sql.Connection jdbc = DriverManager.getConnection("jdbc:postgresql://"
                    + POSTGRES.getHost() + ":" + POSTGRES.getPort() + "/" + POSTGRES.getDatabaseName(),
                    PostgresDbResource.DDL_USER, PostgresDbResource.DDL_PASSWORD);
                 Statement statement = jdbc.createStatement()) {
                statement.execute("PREPARE q(text, text) AS " + count.sql());
                statement.execute("SET plan_cache_mode = " + planCacheMode);
                statement.execute("SET enable_seqscan = off");
                final List<String> lines = new ArrayList<>();
                try (ResultSet resultSet = statement.executeQuery(
                        "EXPLAIN EXECUTE q('/features/*/properties/prop0', '%UILDIN%')")) {
                    while (resultSet.next()) {
                        lines.add(resultSet.getString(1));
                    }
                }
                return String.join("\n", lines);
            } catch (final SQLException e) {
                throw new AssertionError(e);
            }
        };

        final String custom = explainPrepared.apply("force_custom_plan");
        final String generic = explainPrepared.apply("force_generic_plan");

        assertThat(custom).as("custom plan:%n%s", custom).contains("Bitmap Index Scan on " + PROP0.name());
        assertThat(generic).as("generic plan:%n%s", generic)
                .doesNotContain(PROP0.name())
                .contains("$1");
    }

    // ================================================================================================= repair

    @Test
    public void aKilledBuildLeavesAnInvalidIndexThatTheNextRunRepairs() throws Exception {
        final Connection blocker = openBlockingWriter();
        final CompletableFuture<Outcome> first;
        try {
            first = maintenance(List.of(PROP0)).run().toFuture();
            final int buildPid = awaitBackend("CREATE INDEX CONCURRENTLY", null);
            // the catalog entry is committed before the build waits for writers: the index exists, INVALID
            assertThat(managedIndexes()).containsExactly(Map.entry(PROP0.name(), false));

            query("SELECT pg_terminate_backend(" + buildPid + ")");
            final Outcome killed = first.get(RUN_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            assertThat(killed.status()).isEqualTo(Status.APPLIED);
            assertThat(killed.failed()).containsExactly(PROP0.name());
            assertThat(killed.built()).isEmpty();
        } finally {
            release(blocker);
        }
        // the build's connection is gone, so the leftover could not be dropped: IF NOT EXISTS would skip it forever
        assertThat(managedIndexes()).containsExactly(Map.entry(PROP0.name(), false));

        final Outcome next = run(maintenance(List.of(PROP0)));

        assertThat(next.status()).isEqualTo(Status.APPLIED);
        assertThat(next.repaired()).containsExactly(PROP0.name());
        assertThat(next.built()).containsExactly(PROP0.name());
        assertThat(managedIndexes()).containsExactly(Map.entry(PROP0.name(), true));
    }

    @Test
    public void aCancelledBuildIsCleanedUpAndBuiltByTheNextRun() throws Exception {
        final Connection blocker = openBlockingWriter();
        final CompletableFuture<Outcome> first;
        try {
            first = maintenance(List.of(PROP0)).run().toFuture();
            final int buildPid = awaitBackend("CREATE INDEX CONCURRENTLY", null);
            query("SELECT pg_cancel_backend(" + buildPid + ")");
            // the failed build's leftover is dropped on the same connection; that drop waits for the writer too
            awaitBackend("DROP INDEX CONCURRENTLY", buildPid);
        } finally {
            release(blocker);
        }
        final Outcome cancelled = first.get(RUN_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        assertThat(cancelled.status()).isEqualTo(Status.APPLIED);
        assertThat(cancelled.failed()).containsExactly(PROP0.name());
        assertThat(managedIndexes()).isEmpty();

        final Outcome next = run(maintenance(List.of(PROP0)));

        assertThat(next.built()).containsExactly(PROP0.name());
        assertThat(managedIndexes()).containsExactly(Map.entry(PROP0.name(), true));
    }

    // ================================================================================================= removal

    @Test
    public void aRemovedPathIsDroppedAndOtherIndexesAreLeftAlone() {
        run(maintenance(List.of(PROP0, NAME)));
        final String prop0Oid = query("SELECT '" + PROP0.name() + "'::regclass::oid::text").get(0);
        query("CREATE INDEX it_manual_zip ON search_flat (val_text) WHERE wpath = '/attributes/zip'");
        try {
            final Outcome outcome = run(maintenance(List.of(PROP0)));

            assertThat(outcome.status()).isEqualTo(Status.APPLIED);
            assertThat(outcome.dropped()).containsExactly(NAME.name());
            assertThat(outcome.built()).isEmpty();
            assertThat(managedIndexes()).containsExactly(Map.entry(PROP0.name(), true));
            assertThat(query("SELECT '" + PROP0.name() + "'::regclass::oid::text")).containsExactly(prop0Oid);
            assertThat(query("SELECT count(*)::text FROM pg_class WHERE relname = 'it_manual_zip'"))
                    .containsExactly("1");
            assertThat(query("SELECT count(*)::text FROM pg_class WHERE relname IN ('sf_trgm', 'sf_text')"))
                    .containsExactly("2");
        } finally {
            query("DROP INDEX IF EXISTS it_manual_zip");
        }
    }

    @Test
    public void anEmptyConfigurationDropsEveryManagedIndex() {
        run(maintenance(List.of(PROP0, NAME)));

        final Outcome outcome = run(maintenance(List.of()));

        assertThat(outcome.dropped()).containsExactlyInAnyOrder(PROP0.name(), NAME.name());
        assertThat(managedIndexes()).isEmpty();
    }

    @Test
    public void anUnsetListLeavesManagedIndexesAlone() {
        run(maintenance(List.of(PROP0)));
        final String oid = query("SELECT '" + PROP0.name() + "'::regclass::oid::text").get(0);
        final ListAppender<ILoggingEvent> logs = captureLogs();

        final Outcome outcome = run(PathIndexMaintenance.forConnectionFactory(connectionFactory, null, true,
                Duration.ofMillis(100L)));

        assertThat(outcome).isEqualTo(new Outcome(Status.NOT_CONFIGURED, List.of(), List.of(), List.of(), List.of()));
        assertThat(managedIndexes()).containsExactly(Map.entry(PROP0.name(), true));
        assertThat(query("SELECT '" + PROP0.name() + "'::regclass::oid::text")).containsExactly(oid);
        // INFO: unset is a documented, non-destructive setting (e.g. a blue/green twin), not a warning
        assertThat(logs.list).anyMatch(event -> event.getLevel() == Level.INFO
                && event.getFormattedMessage().contains("not configured")
                && event.getFormattedMessage().contains(PROP0.name()));
    }

    // ================================================================================================= guards

    @Test
    public void anotherInstanceHoldingTheLockMeansThisOneChangesNothing() {
        final Connection holder = Mono.from(connectionFactory.create()).block();
        try {
            assertThat(rows(holder, new RenderedSql("SELECT pg_try_advisory_lock("
                    + PathIndexMaintenance.ADVISORY_LOCK_KEY + ")::text", List.of())).blockLast()).isEqualTo("true");

            final Outcome outcome = run(maintenance(List.of(PROP0)));

            assertThat(outcome.status()).isEqualTo(Status.LOCKED_BY_ANOTHER_INSTANCE);
            assertThat(managedIndexes()).isEmpty();
        } finally {
            Mono.from(holder.close()).block();
        }
    }

    @Test
    public void aNewerStoredSchemaVersionMeansNoChange() {
        query("UPDATE schema_version SET version = " + (PostgresSearchSchema.VERSION + 1) + " WHERE component = '"
                + PostgresSearchSchema.COMPONENT + "'");
        try {
            final Outcome outcome = run(maintenance(List.of(PROP0)));

            assertThat(outcome.status()).isEqualTo(Status.SCHEMA_VERSION_MISMATCH);
            assertThat(managedIndexes()).isEmpty();
        } finally {
            query("UPDATE schema_version SET version = " + PostgresSearchSchema.VERSION + " WHERE component = '"
                    + PostgresSearchSchema.COMPONENT + "'");
        }
    }

    @Test
    public void aMissingSchemaVersionTableMeansNoChange() {
        query("ALTER TABLE schema_version RENAME TO schema_version_moved");
        try {
            final Outcome outcome = run(maintenance(List.of(PROP0)));

            assertThat(outcome.status()).isEqualTo(Status.SCHEMA_VERSION_MISMATCH);
            assertThat(managedIndexes()).isEmpty();
        } finally {
            query("ALTER TABLE schema_version_moved RENAME TO schema_version");
        }
    }

    @Test
    public void withoutASessionCapableConnectionNothingIsChangedButUpToDateIsStillReported() {
        final Outcome refused = run(PathIndexMaintenance.forConnectionFactory(connectionFactory, List.of(PROP0), false,
                Duration.ofMillis(100L)));
        assertThat(refused.status()).isEqualTo(Status.NO_SESSION_CAPABLE_CONNECTION);
        assertThat(managedIndexes()).isEmpty();

        run(maintenance(List.of(PROP0)));
        final Outcome upToDate = run(PathIndexMaintenance.forConnectionFactory(connectionFactory, List.of(PROP0),
                false, Duration.ofMillis(100L)));
        assertThat(upToDate.status()).isEqualTo(Status.UP_TO_DATE);
    }

    // ================================================================================================= concurrency

    @Test
    public void aSecondRunDuringABuildNeitherBuildsNorDropsThePeersIndex() throws Exception {
        final Connection blocker = openBlockingWriter();
        final CompletableFuture<Outcome> first = maintenance(List.of(PROP0)).run().toFuture();
        try {
            final int buildPid = awaitBackend("CREATE INDEX CONCURRENTLY", null);
            // to everyone else the peer's in-progress index looks exactly like a failed build's leftover
            assertThat(managedIndexes()).containsExactly(Map.entry(PROP0.name(), false));

            final Outcome second = run(maintenance(List.of(PROP0)));

            assertThat(second.status()).isEqualTo(Status.LOCKED_BY_ANOTHER_INSTANCE);
            assertThat(managedIndexes()).containsExactly(Map.entry(PROP0.name(), false));
            assertThat(query("SELECT state FROM pg_stat_activity WHERE pid = " + buildPid)).containsExactly("active");
        } finally {
            release(blocker);
            // also after a failed assertion: let the first run finish before the next test
            awaitQuietly(first);
        }
        final Outcome built = first.get(RUN_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        assertThat(built.built()).containsExactly(PROP0.name());
        assertThat(managedIndexes()).containsExactly(Map.entry(PROP0.name(), true));
    }

    @Test
    public void aRunThatGetsTheLockAfterThePeerFinishedReportsUpToDate() throws Exception {
        // the second run reads the catalog (index missing -> build), then waits at its lock statement until the peer
        // has built the index; under the lock it re-reads the catalog and finds nothing to do
        final CompletableFuture<Void> atLock = new CompletableFuture<>();
        final CompletableFuture<Void> gate = new CompletableFuture<>();
        final ConnectionFactory gated = wrapping(real -> withStatements(real, (connection, sql) ->
                sql.startsWith("SELECT pg_try_advisory_lock")
                        ? gated(connection.createStatement(sql), atLock, gate)
                        : connection.createStatement(sql)));
        final CompletableFuture<Outcome> second = PathIndexMaintenance.forConnectionFactory(gated, List.of(PROP0),
                true, Duration.ofMillis(100L)).run().toFuture();
        try {
            atLock.get(30L, TimeUnit.SECONDS);

            final Outcome first = run(maintenance(List.of(PROP0)));
            assertThat(first.built()).containsExactly(PROP0.name());
        } finally {
            // also after a failed assertion: open the gate so the second run ends and closes its connection
            gate.complete(null);
            awaitQuietly(second);
        }

        assertThat(second.get(RUN_TIMEOUT.toSeconds(), TimeUnit.SECONDS))
                .isEqualTo(new Outcome(Status.UP_TO_DATE, List.of(), List.of(), List.of(), List.of()));
        assertThat(advisoryLockHolders()).isZero();
    }

    @Test
    public void aStopDuringABuildLeavesTheBuildRunningAndItsIndexToTheBuild() throws Exception {
        final Connection blocker = openBlockingWriter();
        try {
            final Disposable running = maintenance(List.of(PROP0)).run().subscribe();
            final int buildPid = awaitBackend("CREATE INDEX CONCURRENTLY", null);

            // what the provider does at actor-system termination: the subscription is disposed, the connection closed
            running.dispose();

            // PostgreSQL keeps the build and its session lock: a start meanwhile changes nothing (it does not drop the
            // INVALID-looking index of the running build)
            Thread.sleep(500L);
            assertThat(query("SELECT state FROM pg_stat_activity WHERE pid = " + buildPid)).containsExactly("active");
            final Outcome meanwhile = run(maintenance(List.of(PROP0)));
            assertThat(meanwhile.status()).isEqualTo(Status.LOCKED_BY_ANOTHER_INSTANCE);
            assertThat(managedIndexes()).containsExactly(Map.entry(PROP0.name(), false));
        } finally {
            release(blocker);
        }
        // the orphaned build completes, its backend ends and releases the lock
        awaitTrue(() -> Map.of(PROP0.name(), true).equals(managedIndexes()), "the orphaned build completes");
        awaitTrue(() -> advisoryLockHolders() == 0, "the orphaned build's session ends");

        final Outcome next = run(maintenance(List.of(PROP0)));
        assertThat(next.status()).isEqualTo(Status.UP_TO_DATE);
    }

    @Test
    public void statementsOnDifferentBackendsAreNotOneSessionAndChangeNothing() throws Exception {
        // a transaction-mode pooler stand-in: the lock statement runs on another server connection than the rest
        final Connection other = Mono.from(connectionFactory.create()).block();
        try {
            final ConnectionFactory pooled = wrapping(real -> withStatements(real, (connection, sql) ->
                    sql.startsWith("SELECT pg_try_advisory_lock")
                            ? other.createStatement(sql)
                            : connection.createStatement(sql)));

            final Outcome outcome = run(PathIndexMaintenance.forConnectionFactory(pooled, List.of(PROP0), true,
                    Duration.ofMillis(100L)));

            assertThat(outcome.status()).isEqualTo(Status.NOT_A_SESSION);
            assertThat(managedIndexes()).isEmpty();
            // why a transaction pooler is unsafe: the lock stays with the other server connection
            assertThat(advisoryLockHolders()).isEqualTo(1);
        } finally {
            Mono.from(other.close()).block();
        }
        awaitTrue(() -> advisoryLockHolders() == 0, "the lock ends with the other session");
    }

    // ================================================================================================= session

    @Test
    public void aRunEndsWithTheSessionsOwnTimeoutsAndWithoutTheLock() {
        // one physical connection that outlives the run, as a pooled server connection would: role defaults of 7s/6s
        // are what RESET returns to; without the RESETs the session would keep 0 (no timeout)
        query("ALTER ROLE " + PostgresDbResource.DDL_USER + " SET statement_timeout = '7s'");
        query("ALTER ROLE " + PostgresDbResource.DDL_USER + " SET lock_timeout = '6s'");
        final Connection shared;
        try {
            shared = Mono.from(connectionFactory.create()).block();
        } finally {
            query("ALTER ROLE " + PostgresDbResource.DDL_USER + " RESET statement_timeout");
            query("ALTER ROLE " + PostgresDbResource.DDL_USER + " RESET lock_timeout");
        }
        try {
            final ConnectionFactory single = new ConnectionFactory() {
                @Override
                public Publisher<? extends Connection> create() {
                    return Mono.just(withoutClose(shared));
                }

                @Override
                public ConnectionFactoryMetadata getMetadata() {
                    return connectionFactory.getMetadata();
                }
            };

            final Outcome outcome = run(PathIndexMaintenance.forConnectionFactory(single, List.of(PROP0), true,
                    Duration.ofMinutes(10L)));

            assertThat(outcome.built()).containsExactly(PROP0.name());
            assertThat(rows(shared, new RenderedSql("SHOW statement_timeout", List.of())).blockLast())
                    .isEqualTo("7s");
            assertThat(rows(shared, new RenderedSql("SHOW lock_timeout", List.of())).blockLast()).isEqualTo("6s");
            assertThat(advisoryLockHolders()).isZero();
        } finally {
            Mono.from(shared.close()).block();
        }
    }

    // ======================================================================================== session (timeouts)

    @Test
    public void theBuildIgnoresRoleTimeoutsAndLogsItsProgress() throws Exception {
        final ListAppender<ILoggingEvent> logs = captureLogs();
        final Connection blocker = openBlockingWriter();
        final CompletableFuture<Outcome> run;
        try {
            query("ALTER ROLE " + PostgresDbResource.DDL_USER + " SET statement_timeout = '300ms'");
            query("ALTER ROLE " + PostgresDbResource.DDL_USER + " SET lock_timeout = '300ms'");
            try {
                run = maintenance(List.of(PROP0)).run().toFuture();
                awaitBackend("CREATE INDEX CONCURRENTLY", null);
                // the build now waits for the writer far longer than both role defaults would allow
                Thread.sleep(1_500L);
            } finally {
                query("ALTER ROLE " + PostgresDbResource.DDL_USER + " RESET statement_timeout");
                query("ALTER ROLE " + PostgresDbResource.DDL_USER + " RESET lock_timeout");
            }
        } finally {
            release(blocker);
        }

        final Outcome outcome = run.get(RUN_TIMEOUT.toSeconds(), TimeUnit.SECONDS);

        assertThat(outcome.built()).containsExactly(PROP0.name());
        assertThat(outcome.failed()).isEmpty();
        assertThat(managedIndexes()).containsExactly(Map.entry(PROP0.name(), true));
        assertThat(logs.list).extracting(ILoggingEvent::getFormattedMessage)
                .anyMatch(message -> message.startsWith("Building search path index " + PROP0.name() + ": phase '")
                        && message.contains("waiting for writers"))
                .anyMatch(message -> message.startsWith("Built search path index " + PROP0.name()));
    }

    // ================================================================================================= fixture

    private static void loadFixture() {
        query("INSERT INTO search_things (thing_id, namespace, revision, thing, global_read) "
                + "SELECT 'it:t-' || i, 'it', 1, '{}'::jsonb, ARRAY['ditto:reader'] "
                + "FROM generate_series(1, " + THINGS + ") AS i");
        // /attributes/name: md5 hex (never contains the letters of "uildin"), every 1000th a building-<i> name.
        query("INSERT INTO search_flat (thing_id, path, wpath, f_id, ord, type_rank, val_text) "
                + "SELECT 'it:t-' || i, '/attributes/name', '/attributes/name', NULL, 0, 3, "
                + "CASE WHEN i % 1000 = 0 THEN 'building-' || i ELSE md5(i::text) END "
                + "FROM generate_series(1, " + THINGS + ") AS i");
        // The same values on a key with a quote and a backslash.
        query("INSERT INTO search_flat (thing_id, path, wpath, f_id, ord, type_rank, val_text) "
                + "SELECT 'it:t-' || i, E'/attributes/it''s/a\\\\b', E'/attributes/it''s/a\\\\b', NULL, 0, 3, "
                + "CASE WHEN i % 1000 = 0 THEN 'building-' || i ELSE md5(i::text) END "
                + "FROM generate_series(1, " + THINGS + ") AS i");
        query("INSERT INTO search_flat (thing_id, path, wpath, f_id, ord, type_rank, val_text) "
                + "SELECT 'it:t-' || i, '/attributes/zip', '/attributes/zip', NULL, 0, 3, "
                + "(10000 + (i * 7919) % 90000)::text FROM generate_series(1, " + THINGS + ") AS i");
        // Three features per thing, each prop0 leaf as the flattener writes it: the exact row and the /features/* row.
        query("INSERT INTO search_flat (thing_id, path, wpath, f_id, ord, type_rank, val_text) "
                + "SELECT 'it:t-' || i, '/features/f' || k || '/properties/prop0', wp.w, 'f' || k, 0, 3, "
                + "CASE WHEN (i * 3 + k) % 1000 = 0 THEN 'building-' || i || '-' || k ELSE md5(i || '-' || k) END "
                + "FROM generate_series(1, " + THINGS + ") AS i, generate_series(0, 2) AS k, "
                + "LATERAL (VALUES ('/features/f' || k || '/properties/prop0'), ('/features/*/properties/prop0')) "
                + "AS wp(w)");
        // Ten filler attributes per thing.
        query("INSERT INTO search_flat (thing_id, path, wpath, f_id, ord, type_rank, val_text) "
                + "SELECT 'it:t-' || i, '/attributes/a' || j, '/attributes/a' || j, NULL, 0, 3, "
                + "'value-' || ((i * 31 + j * 7919) % 100000) "
                + "FROM generate_series(1, " + THINGS + ") AS i, generate_series(0, 9) AS j");
    }

    // ================================================================================================= helpers

    private static PathIndexMaintenance maintenance(final List<PathIndex> configured) {
        return PathIndexMaintenance.forConnectionFactory(connectionFactory, configured, true,
                Duration.ofMillis(100L));
    }

    /** @return how many backends hold the maintenance's advisory lock. */
    private static int advisoryLockHolders() {
        return Integer.parseInt(query("SELECT count(*)::text FROM pg_locks WHERE locktype = 'advisory' AND granted "
                + "AND objsubid = 1 AND ((classid::bigint << 32) | objid::bigint) = "
                + PathIndexMaintenance.ADVISORY_LOCK_KEY).get(0));
    }

    /** Waits for a run to end (outcome or error), ignoring how it ended: for cleanup in {@code finally} blocks. */
    private static void awaitQuietly(final CompletableFuture<?> run) {
        try {
            run.get(RUN_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        } catch (final Exception e) {
            // the test's own assertions report the failure
        }
    }

    private static void awaitTrue(final BooleanSupplier condition, final String what)
            throws InterruptedException {
        final Instant deadline = Instant.now().plusSeconds(30L);
        while (Instant.now().isBefore(deadline)) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(100L);
        }
        throw new AssertionError("not within 30 s: " + what);
    }

    /** A connection factory over the container's whose connections are wrapped. */
    private static ConnectionFactory wrapping(final UnaryOperator<Connection> wrapper) {
        return new ConnectionFactory() {
            @Override
            public Publisher<? extends Connection> create() {
                return Mono.from(connectionFactory.create()).map(wrapper);
            }

            @Override
            public ConnectionFactoryMetadata getMetadata() {
                return connectionFactory.getMetadata();
            }
        };
    }

    /** The connection with {@code createStatement} replaced; everything else delegates. */
    private static Connection withStatements(final Connection real,
            final BiFunction<Connection, String, io.r2dbc.spi.Statement> createStatement) {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                (proxy, method, args) -> "createStatement".equals(method.getName())
                        ? createStatement.apply(real, (String) args[0])
                        : invoke(real, method, args));
    }

    /** The connection with a no-op {@code close()}, so it outlives the run that uses it. */
    private static Connection withoutClose(final Connection real) {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                (proxy, method, args) -> "close".equals(method.getName())
                        ? Mono.empty()
                        : invoke(real, method, args));
    }

    /** The statement, executed only once the gate is open; {@code reached} completes when execution is requested. */
    private static io.r2dbc.spi.Statement gated(final io.r2dbc.spi.Statement real,
            final CompletableFuture<Void> reached, final CompletableFuture<Void> gate) {
        return (io.r2dbc.spi.Statement) Proxy.newProxyInstance(io.r2dbc.spi.Statement.class.getClassLoader(),
                new Class<?>[]{io.r2dbc.spi.Statement.class}, (proxy, method, args) -> {
                    if ("execute".equals(method.getName())) {
                        return Mono.fromRunnable(() -> reached.complete(null))
                                .then(Mono.fromFuture(gate))
                                .thenMany(Flux.defer(() -> Flux.from(real.execute())));
                    }
                    final Object result = invoke(real, method, args);
                    return result == real ? proxy : result;
                });
    }

    private static Object invoke(final Object target, final Method method, final Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (final InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private static Outcome run(final PathIndexMaintenance maintenance) {
        return maintenance.run().block(RUN_TIMEOUT);
    }

    /**
     * Asserts that the scoped index serves the pattern: a bitmap scan on it whose index condition is the pattern match
     * on the index expression (not a full read of the partial index).
     */
    private static void assertScopedIndexCondition(final String plan, final String indexName,
            final String operatorAndPattern) {
        final List<String> lines = List.of(plan.split("\n"));
        final int scan = lines.stream().filter(line -> line.contains("Bitmap Index Scan on " + indexName)).findFirst()
                .map(lines::indexOf)
                .orElse(-1);
        assertThat(scan).as("%s bitmap scan in plan:%n%s", indexName, plan).isNotNegative();
        assertThat(lines.get(scan + 1).trim())
                .as("%s index condition in plan:%n%s", indexName, plan)
                .isEqualTo("Index Cond: ((val_text)::text " + operatorAndPattern + "::text)");
    }

    /** EXPLAINs the production find statement (default sort, page of 25 + 1) as the persistence executes it. */
    private static String explainFind(final Criteria criteria) {
        final RenderedSql find = SearchQueryAssembler.findAll(criteria, SUBJECTS,
                PostgresSortClause.of(List.of(new SortOption(FEF.sortByThingId(), SortDirection.ASC))), 26, 0);
        return String.join("\n", query(connection -> Flux.concat(
                        execute(connection, "SET plan_cache_mode = force_custom_plan"),
                        execute(connection, "SET max_parallel_workers = 0"),
                        execute(connection, "SET enable_seqscan = off"))
                .thenMany(rows(connection, new RenderedSql("EXPLAIN " + find.sql(), find.binds())))));
    }

    private static Map<String, Boolean> managedIndexes() {
        final Map<String, Boolean> indexes = new LinkedHashMap<>();
        for (final String row : query("SELECT c.relname || ':' || i.indisvalid FROM pg_index i "
                + "JOIN pg_class c ON c.oid = i.indexrelid JOIN pg_class t ON t.oid = i.indrelid "
                + "WHERE t.relname = 'search_flat' AND starts_with(c.relname, 'sf_path_') ORDER BY c.relname")) {
            final int colon = row.lastIndexOf(':');
            indexes.put(row.substring(0, colon), Boolean.parseBoolean(row.substring(colon + 1)));
        }
        return indexes;
    }

    private static String indexDefinition(final String name) {
        return query("SELECT pg_get_indexdef('" + name + "'::regclass)").get(0);
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

    /**
     * Waits until a backend runs a statement starting with the prefix and waits for a lock; returns its pid.
     */
    private static int awaitBackend(final String statementPrefix, final Integer pid) throws InterruptedException {
        final Instant deadline = Instant.now().plusSeconds(30L);
        while (Instant.now().isBefore(deadline)) {
            final List<String> pids = query("SELECT pid::text FROM pg_stat_activity WHERE query LIKE '"
                    + statementPrefix + "%' AND wait_event_type = 'Lock'"
                    + (pid == null ? "" : " AND pid = " + pid));
            if (!pids.isEmpty()) {
                return Integer.parseInt(pids.get(0));
            }
            Thread.sleep(50L);
        }
        throw new AssertionError("no backend waiting in <" + statementPrefix + "> within 30 s");
    }

    private static ListAppender<ILoggingEvent> captureLogs() {
        final Logger logger = (Logger) LoggerFactory.getLogger(PathIndexMaintenance.class);
        logger.setLevel(Level.INFO);
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private static Flux<String> rows(final Connection connection, final RenderedSql rendered) {
        return Flux.from(rendered.applyTo(connection.createStatement(rendered.sql())).execute())
                .flatMap(result -> result.map((row, meta) -> row.get(0, String.class)));
    }

    private static Mono<Void> execute(final Connection connection, final String sql) {
        return Flux.from(connection.createStatement(sql).execute()).flatMap(Result::getRowsUpdated).then();
    }

    private static List<String> query(final Function<Connection, Flux<String>> body) {
        return Flux.usingWhen(Mono.from(connectionFactory.create()), body, Connection::close)
                .collectList()
                .block();
    }

    /** Runs one statement on a fresh connection and returns the first column of every row as text. */
    private static List<String> query(final String sql) {
        final List<String> result = new ArrayList<>();
        Flux.usingWhen(Mono.from(connectionFactory.create()),
                        connection -> Flux.from(connection.createStatement(sql).execute())
                                .flatMap(r -> r.map((row, meta) -> {
                                    if (meta.getColumnMetadatas().isEmpty()) {
                                        return "";
                                    }
                                    final Object value = row.get(0);
                                    return value == null ? "" : value.toString();
                                })),
                        Connection::close)
                .doOnNext(result::add)
                .blockLast();
        return result;
    }

}
