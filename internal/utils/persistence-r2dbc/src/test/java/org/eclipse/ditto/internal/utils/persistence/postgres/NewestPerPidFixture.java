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

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.internal.utils.persistence.postgres.client.DittoPostgresClient;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.config.DefaultPostgresConfig;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.PostgresSchemaManager;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.testkit.PostgresDbResource;
import org.eclipse.ditto.internal.utils.persistence.postgres.ops.PostgresPersistenceOperations;
import org.eclipse.ditto.internal.utils.persistence.postgres.ops.PostgresTableNames;
import org.eclipse.ditto.internal.utils.persistence.postgres.readjournal.PostgresReadJournal;
import org.eclipse.ditto.internal.utils.persistence.postgres.schema.PostgresSchema;

import com.typesafe.config.ConfigFactory;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.Result;
import io.r2dbc.spi.Statement;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Real-PostgreSQL fixture for integration tests of the newest-row-per-pid queries (journal and snapshot side).
 * <p>
 * It starts one Testcontainers PostgreSQL, bootstraps the persistence schema, and hands out pooled
 * {@link PostgresPersistenceOperations} / {@link PostgresReadJournal} instances for any entity table set (for example
 * {@code connections}, which is where production runs the tag queries). It also offers:
 * </p>
 * <ul>
 *     <li>set-based bulk seeding ({@link #seedJournal}): one {@code INSERT … SELECT … generate_series} statement, so a
 *     fixture can exceed the read-journal portal fetch size and give the planner realistic statistics after
 *     {@link #analyze};</li>
 *     <li>{@link #explain}: {@code EXPLAIN (COSTS OFF)} (or any other option list, e.g. {@code ANALYZE, BUFFERS}) of a
 *     parameterised statement, with planner session settings such as {@code enable_seqscan = off} applied on the same
 *     connection first. On small fixtures the planner may legitimately prefer a sequential scan, so plan-shape
 *     assertions should check index eligibility this way.</li>
 *     <li>{@link #queryAsText}: ad-hoc queries, e.g. against {@code pg_stat_activity}, on a connection outside the
 *     pool.</li>
 * </ul>
 * <p>
 * {@link #start()} throws when Docker is unreachable; callers turn that into a skip with
 * {@code Assume.assumeNoException}.
 * </p>
 */
@NotThreadSafe
public final class NewestPerPidFixture implements AutoCloseable {

    private static final Duration BLOCK_TIMEOUT = Duration.ofSeconds(60);

    private final PostgresDbResource postgres;
    private final ConnectionFactory ddlFactory;
    private final DittoPostgresClient client;

    private NewestPerPidFixture(final PostgresDbResource postgres, final ConnectionFactory ddlFactory,
            final DittoPostgresClient client) {
        this.postgres = postgres;
        this.ddlFactory = ddlFactory;
        this.client = client;
    }

    /**
     * Starts the container and bootstraps the persistence schema.
     *
     * @return the running fixture.
     * @throws RuntimeException if Docker / Testcontainers is unavailable.
     */
    public static NewestPerPidFixture start() {
        final PostgresDbResource postgres = new PostgresDbResource();
        postgres.start();
        try {
            final ConnectionFactory ddlFactory = postgres.newConnectionFactory();
            PostgresSchemaManager.of(ddlFactory, PostgresSchema.descriptor()).bootstrap();
            final ConnectionPool pool = new ConnectionPool(ConnectionPoolConfiguration.builder(ddlFactory)
                    .name("newest-per-pid-fixture-pool").maxSize(8).build());
            final DittoPostgresClient client =
                    DittoPostgresClient.forConnectionPool(pool, DefaultPostgresConfig.of(ConfigFactory.empty()));
            return new NewestPerPidFixture(postgres, ddlFactory, client);
        } catch (final Throwable t) {
            // any failure after the container started (including an Error) stops it before propagating unchanged.
            try {
                postgres.stop();
            } catch (final RuntimeException stopFailure) {
                t.addSuppressed(stopFailure);
            }
            throw t;
        }
    }

    /**
     * @param entityPrefix the entity table set, e.g. {@code connections}.
     * @return operations bound to that entity's tables, sharing the fixture's pool.
     */
    public PostgresPersistenceOperations operations(final String entityPrefix) {
        return PostgresPersistenceOperations.of(client, entityPrefix);
    }

    /**
     * @param entityPrefix the entity table set, e.g. {@code connections}.
     * @return a read journal over that entity's tables, sharing the fixture's pool.
     */
    public PostgresReadJournal readJournal(final String entityPrefix) {
        return PostgresReadJournal.of(operations(entityPrefix));
    }

    /**
     * A separate pooled client over the fixture's database whose connection factory is decorated first, e.g. with an
     * r2dbc-proxy listener that observes every statement. The caller closes it.
     *
     * @param decorator wraps the fixture's connection factory.
     * @return the client.
     */
    public DittoPostgresClient newClient(final UnaryOperator<ConnectionFactory> decorator) {
        final ConnectionPool pool = new ConnectionPool(ConnectionPoolConfiguration.builder(decorator.apply(ddlFactory))
                .name("newest-per-pid-fixture-decorated-pool").maxSize(4).build());
        return DittoPostgresClient.forConnectionPool(pool, DefaultPostgresConfig.of(ConfigFactory.empty()));
    }

    /**
     * The pid the bulk seeding generates for index {@code i}: the prefix plus {@code i} zero-padded to six digits, so
     * pid order equals index order.
     *
     * @param pidPrefix the pid prefix passed to {@link #seedJournal}.
     * @param i the 1-based pid index.
     * @return the generated pid.
     */
    public static String pid(final String pidPrefix, final int i) {
        return pidPrefix + String.format("%06d", i);
    }

    /**
     * Bulk-inserts {@code pidCount × eventsPerPid} journal rows in one statement (pids {@link #pid(String, int)
     * pid(pidPrefix, 1..pidCount)}, sequence numbers {@code 1..eventsPerPid}, event {@code {"i":i,"s":s}}) and
     * upserts the matching {@code _journal_seq} high-water-marks, as the journal write path would.
     *
     * @param entityPrefix the entity table set, e.g. {@code connections}.
     * @param pidPrefix the pid prefix.
     * @param pidCount the number of pids.
     * @param eventsPerPid the number of events per pid.
     * @param tagsExpression a SQL {@code text[]} expression over the pid index {@code i} and the sequence number
     * {@code s}, e.g. {@code CASE WHEN s = 4 THEN ARRAY['x'] ELSE ARRAY['always-alive', 'priority-' || (i % 10)] END}.
     */
    public void seedJournal(final String entityPrefix, final String pidPrefix, final int pidCount,
            final int eventsPerPid, final String tagsExpression) {
        final PostgresTableNames tables = PostgresTableNames.of(entityPrefix);
        final String pidExpression = "$1 || lpad(i::text, 6, '0')";
        execute("INSERT INTO " + tables.journalTable() + " (pid, sn, manifest, tags, event) "
                        + "SELECT " + pidExpression + ", s, 'manifest', " + tagsExpression + ", "
                        + "jsonb_build_object('i', i, 's', s) "
                        + "FROM generate_series(1, $2) AS i, generate_series(1, $3) AS s",
                List.of(pidPrefix, pidCount, eventsPerPid));
        execute("INSERT INTO " + tables.journalSeqTable() + " (pid, highest_sn) "
                        + "SELECT " + pidExpression + ", $3 FROM generate_series(1, $2) AS i "
                        + "ON CONFLICT (pid) DO UPDATE SET highest_sn = GREATEST("
                        + tables.journalSeqTable() + ".highest_sn, excluded.highest_sn)",
                List.of(pidPrefix, pidCount, (long) eventsPerPid));
    }

    /**
     * Runs {@code ANALYZE} on a table so plan-shape assertions see real statistics.
     *
     * @param table the table name.
     */
    public void analyze(final String table) {
        execute("ANALYZE " + table, List.of());
    }

    /**
     * Executes one statement on a fresh connection (outside the pool).
     *
     * @param sql the statement, with {@code $n} placeholders.
     * @param binds the positional bind values.
     */
    public void execute(final String sql, final List<Object> binds) {
        Mono.usingWhen(Mono.from(ddlFactory.create()),
                        conn -> Flux.from(bind(conn.createStatement(sql), binds).execute())
                                .flatMap(Result::getRowsUpdated)
                                .then(),
                        Connection::close)
                .block(BLOCK_TIMEOUT);
    }

    /**
     * Runs a query on a fresh connection (outside the pool) and returns every row as column name to text value.
     * Cast non-text columns in the SQL (e.g. {@code backend_xmin::text}); SQL {@code NULL} stays {@code null}.
     *
     * @param sql the query, with {@code $n} placeholders.
     * @param binds the positional bind values.
     * @return the rows in result order.
     */
    public List<Map<String, String>> queryAsText(final String sql, final List<Object> binds) {
        final List<Map<String, String>> rows = Flux.usingWhen(Mono.from(ddlFactory.create()),
                        conn -> Flux.from(bind(conn.createStatement(sql), binds).execute())
                                .flatMap(result -> result.map((row, meta) -> {
                                    final Map<String, String> columns = new LinkedHashMap<>();
                                    meta.getColumnMetadatas().forEach(column ->
                                            columns.put(column.getName(), row.get(column.getName(), String.class)));
                                    return columns;
                                })),
                        Connection::close)
                .collectList()
                .block(BLOCK_TIMEOUT);
        return rows == null ? List.of() : rows;
    }

    /**
     * Returns the {@code EXPLAIN (COSTS OFF)} plan of a parameterised statement, planned with the bound values (a
     * custom plan, as production gets with unnamed statements).
     *
     * @param sql the statement to explain, with {@code $n} placeholders.
     * @param binds the positional bind values.
     * @param sessionSettings planner settings applied first on the same connection, e.g. {@code enable_seqscan = off}.
     * @return the plan, one plan line per text line.
     */
    public String explain(final String sql, final List<Object> binds, final String... sessionSettings) {
        return explain("COSTS OFF", sql, binds, sessionSettings);
    }

    /**
     * Returns the {@code EXPLAIN (<options>)} plan of a parameterised statement, planned with the bound values.
     *
     * @param explainOptions the EXPLAIN option list without parentheses, e.g. {@code COSTS OFF} or
     * {@code ANALYZE, BUFFERS} (which executes the statement and reports buffer usage and temp-file spills).
     * @param sql the statement to explain, with {@code $n} placeholders.
     * @param binds the positional bind values.
     * @param sessionSettings planner settings applied first on the same connection, e.g. {@code enable_seqscan = off}.
     * @return the plan, one plan line per text line.
     */
    public String explain(final String explainOptions, final String sql, final List<Object> binds,
            final String... sessionSettings) {
        final List<String> lines = new ArrayList<>();
        Flux.usingWhen(Mono.from(ddlFactory.create()),
                        conn -> Flux.fromArray(sessionSettings)
                                .concatMap(setting -> Flux.from(conn.createStatement("SET " + setting).execute())
                                        .flatMap(Result::getRowsUpdated))
                                .thenMany(Flux.from(bind(conn.createStatement(
                                                        "EXPLAIN (" + explainOptions + ") " + sql), binds)
                                                .execute())
                                        .flatMap(result -> result.map((row, meta) -> String.valueOf(row.get(0))))),
                        Connection::close)
                .doOnNext(lines::add)
                .blockLast(BLOCK_TIMEOUT);
        return String.join("\n", lines);
    }

    @Override
    public void close() {
        try {
            client.close();
        } finally {
            postgres.stop();
        }
    }

    private static Statement bind(final Statement statement, final List<Object> binds) {
        for (int i = 0; i < binds.size(); i++) {
            statement.bind(i, binds.get(i));
        }
        return statement;
    }

}
