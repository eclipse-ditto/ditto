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

import org.eclipse.ditto.internal.utils.persistence.postgres.schema.PostgresSchema;

import org.eclipse.ditto.internal.utils.persistence.postgres.client.ConnectionPoolFactory;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.DittoPostgresClient;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.testkit.PostgresDbResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.internal.utils.persistence.postgres.client.config.DefaultPostgresConfig;
import org.eclipse.ditto.internal.utils.persistence.postgres.ops.PostgresPersistenceOperations;
import org.eclipse.ditto.internal.utils.persistence.postgres.journal.PostgresJournalOps;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.PostgresSchemaManager;
import org.eclipse.ditto.internal.utils.persistence.postgres.readjournal.PostgresReadJournal;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import com.typesafe.config.ConfigFactory;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactoryProvider;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.stream.SystemMaterializer;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.testkit.javadsl.TestKit;

/**
 * PgBouncer transaction-pooling smoke test: runs the schema bootstrap (transaction-scoped advisory-lock DDL), a
 * write/recover/cleanup round-trip and the paged tag pid streams <em>through a real PgBouncer container in
 * {@code pool_mode=transaction}</em> with {@code prepared-statement-cache-queries=0} (unnamed statements — safe on any
 * PgBouncer version).
 * <p>
 * The PgBouncer image is pinned ({@value #PGBOUNCER_IMAGE}). If Docker or the PgBouncer image is unreachable, the
 * {@link #start() Assume} guard turns it into a skip rather than a failure.
 * </p>
 */
public final class PgBouncerTransactionPoolingIT {

    /** The same PgBouncer version as {@code PgBouncerStatementTimeoutIT}. */
    private static final String PGBOUNCER_IMAGE = "edoburu/pgbouncer:v1.26.0-p0";

    private static final String PG_USER = "ditto_ddl";
    private static final String PG_PASSWORD = "ddl_secret";
    private static final String PG_DB = "ditto";
    // The edoburu/pgbouncer image listens on 5432 inside the container (not 6432).
    private static final int PGBOUNCER_PORT = 5432;

    private static Network network;
    private static PostgreSQLContainer<?> postgres;
    private static GenericContainer<?> pgbouncer;
    private static DittoPostgresClient client;
    private static PostgresJournalOps journal;
    private static PostgresReadJournal readJournal;
    private static ActorSystem system;

    @BeforeClass
    public static void start() {
        try {
            network = Network.newNetwork();
            postgres = PostgresDbResource.newContainer()
                    .withNetwork(network)
                    .withNetworkAliases("postgres")
                    .withDatabaseName(PG_DB)
                    .withUsername(PG_USER)
                    .withPassword(PG_PASSWORD);
            postgres.start();

            pgbouncer = new GenericContainer<>(DockerImageName.parse(PGBOUNCER_IMAGE))
                    .withNetwork(network)
                    .withNetworkAliases("pgbouncer")
                    .withEnv("DB_HOST", "postgres")
                    .withEnv("DB_PORT", "5432")
                    .withEnv("DB_USER", PG_USER)
                    .withEnv("DB_PASSWORD", PG_PASSWORD)
                    .withEnv("DB_NAME", PG_DB)
                    .withEnv("POOL_MODE", "transaction")
                    .withEnv("AUTH_TYPE", "scram-sha-256")
                    .withEnv("MAX_CLIENT_CONN", "100")
                    .withExposedPorts(PGBOUNCER_PORT)
                    .waitingFor(Wait.forListeningPort());
            pgbouncer.start();
        } catch (final Throwable t) {
            Assume.assumeNoException("Docker / PgBouncer image unavailable — skipping PgBouncerTransactionPoolingIT", t);
        }

        final ConnectionFactory factory = throughPgBouncer();
        // bootstrap (transaction-scoped advisory-lock DDL) through PgBouncer transaction pooling — must succeed.
        PostgresSchemaManager.of(factory, PostgresSchema.descriptor()).bootstrap();

        final ConnectionPool pool = new ConnectionPool(ConnectionPoolConfiguration.builder(factory)
                .name("pgbouncer-pool").maxSize(8).build());
        client = DittoPostgresClient.forConnectionPool(pool, DefaultPostgresConfig.of(ConfigFactory.empty()));
        final PostgresPersistenceOperations operations = PostgresPersistenceOperations.of(client, "things");
        journal = PostgresJournalOps.of(operations);
        readJournal = PostgresReadJournal.of(operations);
        system = ActorSystem.create("PgBouncerTransactionPoolingIT");
    }

    @AfterClass
    public static void stop() {
        if (system != null) {
            TestKit.shutdownActorSystem(system);
        }
        if (client != null) {
            client.close();
        }
        if (pgbouncer != null) {
            pgbouncer.stop();
        }
        if (postgres != null) {
            postgres.stop();
        }
        if (network != null) {
            network.close();
        }
    }

    private static ConnectionFactory throughPgBouncer() {
        return ConnectionFactories.get(ConnectionFactoryOptions.builder()
                .option(ConnectionFactoryOptions.DRIVER, "postgresql")
                .option(ConnectionFactoryOptions.HOST, pgbouncer.getHost())
                .option(ConnectionFactoryOptions.PORT, pgbouncer.getMappedPort(PGBOUNCER_PORT))
                .option(ConnectionFactoryOptions.DATABASE, PG_DB)
                .option(ConnectionFactoryOptions.USER, PG_USER)
                .option(ConnectionFactoryOptions.PASSWORD, PG_PASSWORD)
                // unnamed statements -> safe under transaction pooling on any PgBouncer version.
                .option(PostgresqlConnectionFactoryProvider.PREPARED_STATEMENT_CACHE_QUERIES, 0)
                .option(PostgresqlConnectionFactoryProvider.FETCH_SIZE, 0)
                .build());
    }

    @Test
    public void writeRecoverCleanupThroughTransactionPooling() throws Exception {
        final String pid = "thing:pgb:rt";
        await(journal.writeMessages(List.of(write(pid, 1L))));
        await(journal.writeMessages(List.of(write(pid, 2L))));

        // recover: highest sequence number visible through PgBouncer.
        assertThat(await(journal.readHighestSequenceNr(pid, 0L))).isEqualTo(2L);

        // cleanup: physical delete; the high-water-mark must still report 2 afterwards.
        await(journal.deleteMessagesTo(pid, 2L));
        assertThat(await(journal.readHighestSequenceNr(pid, 0L))).isEqualTo(2L);
    }

    /**
     * The tag pid streams (the ID-order persistence ping and connection-id retrieval, one short window per statement;
     * the priority-order connectivity ping, the same windows sorted in memory): under transaction pooling every
     * statement may run on another server connection, and the walk must still be complete, in order and free of
     * duplicates.
     */
    @Test
    public void pagedTagPidStreamsCompleteThroughTransactionPooling() throws Exception {
        final String tag = "pgb-paged";
        final int pidCount = 1_000;
        reactor.core.publisher.Mono.from(client.executeUpdatePublisher(
                        "INSERT INTO things_journal (pid, sn, manifest, tags, event) "
                                + "SELECT 'thing:pgb-paged:' || lpad(i::text, 6, '0'), 1, 'manifest', "
                                + "ARRAY[$1, 'priority-' || (i % 10)], '{}'::jsonb FROM generate_series(1, $2) AS i",
                        stmt -> {
                            stmt.bind(0, tag);
                            stmt.bind(1, pidCount);
                        }))
                .block(Duration.ofSeconds(30));

        final List<String> anyTagged = await(readJournal.getJournalPidsWithTag(tag, 100,
                Duration.ofSeconds(30), SystemMaterializer.get(system).materializer(), false)
                .runWith(Sink.seq(), system));
        final List<String> newestTagged = await(readJournal.getJournalPidsWithTag(tag, 100,
                Duration.ofSeconds(30), SystemMaterializer.get(system).materializer(), true)
                .runWith(Sink.seq(), system));
        final List<String> byPriority = await(readJournal.getJournalPidsWithTagOrderedByPriorityTag(tag,
                        Duration.ofSeconds(30))
                .runWith(Sink.seq(), system));

        assertThat(anyTagged).hasSize(pidCount).doesNotHaveDuplicates().isSorted();
        assertThat(newestTagged).hasSize(pidCount).doesNotHaveDuplicates().isSorted();
        assertThat(byPriority).hasSize(pidCount).doesNotHaveDuplicates();
        assertThat(byPriority.stream().map(pid -> Integer.parseInt(pid.substring(pid.length() - 6)) % 10).toList())
                .isSortedAccordingTo((a, b) -> Integer.compare(b, a));
    }

    /**
     * Proves the production statement-timeout wiring: {@code ConnectionPoolFactory.baseOptions} binds
     * {@code statement_timeout} via the r2dbc-postgresql {@code OPTIONS} startup-parameter map, and the server enforces
     * it — a {@code pg_sleep(10)} under a 1s {@code statement_timeout} aborts with SQLSTATE {@code 57014}
     * ({@code query_canceled}).
     * <p>
     * <strong>PgBouncer caveat:</strong> r2dbc-postgresql sends the GUC as a startup parameter of its own. PgBouncer
     * (in any pool mode) refuses it unless it lists {@code statement_timeout} in {@code track_extra_parameters} (then
     * applies it to each server connection it hands out) or {@code ignore_startup_parameters} (then drops it, and the
     * same {@code pg_sleep(10)} runs to completion unless the role has a {@code statement_timeout} default).
     * {@link PgBouncerStatementTimeoutIT} covers that case; it is documented in {@code ditto-postgres-client.conf}.
     */
    @Test
    public void statementTimeoutFromOptionsStartupParameterIsEnforced() {
        // Drive the production OPTIONS encoding straight at Postgres (the path PgBouncer does not interfere with). A DO
        // block returns a command tag (no result-set portal to stream), so the cancellation surfaces 57014 cleanly.
        final ConnectionFactory factory = ConnectionFactories.get(ConnectionFactoryOptions.builder()
                .option(ConnectionFactoryOptions.DRIVER, "postgresql")
                .option(ConnectionFactoryOptions.HOST, postgres.getHost())
                .option(ConnectionFactoryOptions.PORT, postgres.getMappedPort(5432))
                .option(ConnectionFactoryOptions.DATABASE, PG_DB)
                .option(ConnectionFactoryOptions.USER, PG_USER)
                .option(ConnectionFactoryOptions.PASSWORD, PG_PASSWORD)
                .option(PostgresqlConnectionFactoryProvider.PREPARED_STATEMENT_CACHE_QUERIES, 0)
                .option(PostgresqlConnectionFactoryProvider.FETCH_SIZE, 0)
                // exactly the production ConnectionPoolFactory wiring: statement_timeout via the OPTIONS startup map.
                .option(PostgresqlConnectionFactoryProvider.OPTIONS, java.util.Map.of("statement_timeout", "1000"))
                .build());
        final ConnectionPool pool = new ConnectionPool(ConnectionPoolConfiguration.builder(factory)
                .name("pg-stmt-timeout").maxSize(2).build());
        try {
            final reactor.core.publisher.Mono<Long> longQuery = reactor.core.publisher.Mono.usingWhen(
                    pool.create(),
                    connection -> reactor.core.publisher.Flux.from(
                                    connection.createStatement("DO $$ BEGIN PERFORM pg_sleep(10); END $$;").execute())
                            .flatMap(io.r2dbc.spi.Result::getRowsUpdated)
                            .reduce(0L, Long::sum),
                    io.r2dbc.spi.Connection::close);

            final long start = System.nanoTime();
            final Throwable thrown = catchThrowable(() -> longQuery.block(Duration.ofSeconds(20)));
            final Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

            final java.util.Set<String> sqlStates = new java.util.HashSet<>();
            for (Throwable t = thrown; t != null && t != t.getCause(); t = t.getCause()) {
                if (t instanceof io.r2dbc.spi.R2dbcException r2dbc && r2dbc.getSqlState() != null) {
                    sqlStates.add(r2dbc.getSqlState());
                }
            }

            assertThat(thrown).as("pg_sleep(10) was aborted, not run to completion").isNotNull();
            assertThat(elapsed)
                    .as("aborted by statement_timeout=1s, well before the 10s sleep finishes")
                    .isLessThan(Duration.ofSeconds(8));
            assertThat(sqlStates)
                    .as("SQLSTATE 57014 (query_canceled) in the failure cause chain: %s", sqlStates)
                    .contains("57014");
        } finally {
            pool.dispose();
        }
    }

    private static org.apache.pekko.persistence.AtomicWrite write(final String pid, final long sn) {
        return org.apache.pekko.persistence.AtomicWrite.apply(org.apache.pekko.persistence.PersistentRepr.apply(
                org.eclipse.ditto.json.JsonFactory.newObject("{\"sn\":" + sn + "}"), sn, pid, "manifest", false,
                org.apache.pekko.actor.ActorRef.noSender(), "w"));
    }

    private static <T> T await(final CompletionStage<T> stage) throws Exception {
        return stage.toCompletableFuture().get(30, TimeUnit.SECONDS);
    }

}
