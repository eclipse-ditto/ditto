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
package org.eclipse.ditto.internal.utils.persistence.postgres.client.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import org.eclipse.ditto.internal.utils.persistence.postgres.client.testkit.PostgresDbResource;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.R2dbcException;
import io.r2dbc.spi.Result;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * The same-version fast path and the DDL lock timeout of {@link PostgresSchemaManager} against a real PostgreSQL
 * (Testcontainers): a boot or self-heal whose stored row matches the code and whose tables and indexes all exist runs
 * no DDL (proved by a reset reloption that stays reset, and by completing while another session holds
 * {@code SHARE UPDATE EXCLUSIVE} — the lock of a running {@code CREATE INDEX CONCURRENTLY}); a boot that has to run
 * DDL gives up after the 500 ms {@code lock_timeout} instead of waiting.
 * <p>
 * Runs under failsafe ({@code *IT}); skipped when no Docker daemon is reachable.
 * </p>
 */
public final class PostgresSchemaManagerFastPathIT {

    private static final String COMPONENT = "fast-path-it";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String RELOPTION = "autovacuum_vacuum_scale_factor=0.01";
    private static final String NO_RELOPTIONS = "<none>";
    private static final String LOCK_NOT_AVAILABLE = "55P03";

    private static final String CREATE_ITEMS =
            "CREATE TABLE IF NOT EXISTS fp_items (id TEXT COLLATE \"C\" PRIMARY KEY, val BIGINT NOT NULL)";
    private static final String CREATE_SEQ =
            "CREATE TABLE IF NOT EXISTS fp_seq (id TEXT COLLATE \"C\" PRIMARY KEY, n BIGINT NOT NULL)";
    private static final String ITEMS_RELOPTION =
            "ALTER TABLE fp_items SET (autovacuum_vacuum_scale_factor = 0.01)";

    private static final PostgresSchemaDescriptor V1 = descriptor(1, List.of(CREATE_ITEMS,
            "CREATE INDEX IF NOT EXISTS fp_items_val_idx ON fp_items (val)", CREATE_SEQ, ITEMS_RELOPTION));

    /** The same schema, but its index statement uses a quoted name the fast path does not read. */
    private static final PostgresSchemaDescriptor V1_UNNAMEABLE = descriptor(1, List.of(CREATE_ITEMS,
            "CREATE INDEX IF NOT EXISTS \"fp_items_val_idx\" ON fp_items (val)", CREATE_SEQ, ITEMS_RELOPTION));

    /** A backward-compatible bump that adds an index on fp_seq. */
    private static final PostgresSchemaDescriptor V2 = descriptor(2, List.of(CREATE_ITEMS,
            "CREATE INDEX IF NOT EXISTS fp_items_val_idx ON fp_items (val)", CREATE_SEQ, ITEMS_RELOPTION,
            "CREATE INDEX IF NOT EXISTS fp_seq_n_idx ON fp_seq (n)"));

    private static final PostgresDbResource POSTGRES = new PostgresDbResource();
    private static ConnectionFactory connectionFactory;

    private final ListAppender<ILoggingEvent> managerLog = new ListAppender<>();
    @Nullable private Level originalManagerLevel;

    @BeforeClass
    public static void startContainer() {
        try {
            POSTGRES.start();
        } catch (final Throwable t) {
            Assume.assumeNoException("Docker/Testcontainers unavailable — skipping PostgresSchemaManagerFastPathIT", t);
        }
        connectionFactory = POSTGRES.newConnectionFactory();
    }

    @AfterClass
    public static void stopContainer() {
        POSTGRES.stop();
    }

    @Before
    public void resetDatabaseAndCaptureLogs() {
        runSql("DROP TABLE IF EXISTS fp_items, fp_seq, schema_version CASCADE");
        final Logger manager = (Logger) LoggerFactory.getLogger(PostgresSchemaManager.class);
        originalManagerLevel = manager.getLevel();
        manager.setLevel(Level.INFO);   // logback-test.xml roots at WARN
        managerLog.start();
        manager.addAppender(managerLog);
    }

    @After
    public void releaseLogs() {
        final Logger manager = (Logger) LoggerFactory.getLogger(PostgresSchemaManager.class);
        manager.detachAppender(managerLog);
        manager.setLevel(originalManagerLevel);
        managerLog.stop();
    }

    // ---- boot ------------------------------------------------------------------------------------------------------

    @Test
    public void aSameVersionBootWithEveryTableAndIndexPresentRunsNoDdl() {
        bootstrap(V1);
        final String row = storedRow();
        runSql("ALTER TABLE fp_items RESET (autovacuum_vacuum_scale_factor)");
        managerLog.list.clear();

        bootstrap(V1);

        assertThat(reloptions("fp_items")).as("the DDL did not run, so the reset reloption stays reset")
                .isEqualTo(NO_RELOPTIONS);
        assertThat(storedRow()).isEqualTo(row);
        assertThat(messages(Level.INFO)).anySatisfy(message -> assertThat(message)
                .contains("bootstrap complete").contains(COMPONENT).contains("no DDL"));
    }

    @Test
    public void aSameVersionBootCompletesWhileAnotherSessionHoldsTheLockOfAConcurrentIndexBuild() {
        bootstrap(V1);

        try (HeldLock build = HeldLock.on("fp_items", "SHARE UPDATE EXCLUSIVE")) {
            final long start = System.nanoTime();
            bootstrap(V1);
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(5));
        }
    }

    @Test
    public void aMissingIndexMakesTheBootRunTheWholeDdl() {
        bootstrap(V1);
        runSql("ALTER TABLE fp_items RESET (autovacuum_vacuum_scale_factor)");
        runSql("DROP INDEX fp_items_val_idx");
        managerLog.list.clear();

        bootstrap(V1);

        assertThat(indexExists("fp_items_val_idx")).isTrue();
        assertThat(reloptions("fp_items")).as("the whole DDL ran").isEqualTo(RELOPTION);
        assertThat(messages(Level.INFO)).anySatisfy(message -> assertThat(message)
                .contains("missing").contains("fp_items_val_idx"));
    }

    @Test
    public void aMissingTableWithoutANamedIndexMakesTheBootRecreateIt() {
        bootstrap(V1);
        runSql("DROP TABLE fp_seq");

        bootstrap(V1);   // an index-only probe would skip the DDL and verifyCatalog would refuse to boot

        assertThat(tableExists("fp_seq")).isTrue();
    }

    @Test
    public void aStatementWhoseNameCannotBeReadMakesEverySameVersionBootRunTheDdl() {
        bootstrap(V1_UNNAMEABLE);
        runSql("ALTER TABLE fp_items RESET (autovacuum_vacuum_scale_factor)");

        bootstrap(V1_UNNAMEABLE);

        assertThat(reloptions("fp_items")).isEqualTo(RELOPTION);
    }

    @Test
    public void aBootThatMustRunDdlGivesUpAfterTheLockTimeoutAndTheNextBootRecreatesTheIndex() {
        bootstrap(V1);
        runSql("DROP INDEX fp_items_val_idx");
        final String row = storedRow();

        try (HeldLock build = HeldLock.on("fp_items", "SHARE UPDATE EXCLUSIVE")) {
            final long start = System.nanoTime();
            final Throwable error = catchThrowable(() -> bootstrap(V1));
            final Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

            assertThat(sqlState(error)).as("lock_not_available, got %s", error).isEqualTo(LOCK_NOT_AVAILABLE);
            assertThat(elapsed).isBetween(Duration.ofMillis(500), Duration.ofSeconds(3));
            assertThat(indexExists("fp_items_val_idx")).as("the failed boot changed nothing").isFalse();
            assertThat(storedRow()).isEqualTo(row);
            assertThat(messages(Level.WARN)).anySatisfy(message -> assertThat(message)
                    .contains(COMPONENT).contains("lock_timeout"));
        }

        bootstrap(V1);
        assertThat(indexExists("fp_items_val_idx")).isTrue();
    }

    @Test
    public void aVersionBumpRunsItsDdlUnderTheLockTimeoutToo() {
        bootstrap(V1);

        try (HeldLock build = HeldLock.on("fp_seq", "SHARE UPDATE EXCLUSIVE")) {
            final Throwable error = catchThrowable(() -> bootstrap(V2));
            assertThat(sqlState(error)).as("lock_not_available, got %s", error).isEqualTo(LOCK_NOT_AVAILABLE);
            assertThat(storedRow()).startsWith("1/");
        }

        bootstrap(V2);
        assertThat(indexExists("fp_seq_n_idx")).isTrue();
        assertThat(storedRow()).startsWith("2/");
    }

    // ---- self-heal -------------------------------------------------------------------------------------------------

    @Test
    public void aHealWithEveryTableAndIndexPresentRunsNoDdl() {
        bootstrap(V1);
        runSql("ALTER TABLE fp_items RESET (autovacuum_vacuum_scale_factor)");

        assertThat(heal(V1)).isEqualTo(SchemaVersionDecision.UP_TO_DATE);
        assertThat(reloptions("fp_items")).isEqualTo(NO_RELOPTIONS);
    }

    @Test
    public void aHealRecreatesAMissingTableWithoutANamedIndex() {
        bootstrap(V1);
        runSql("DROP TABLE fp_seq");

        assertThat(heal(V1)).isEqualTo(SchemaVersionDecision.APPLY);
        assertThat(tableExists("fp_seq")).isTrue();
    }

    @Test
    public void aHealThatMustRunDdlGivesUpAfterTheLockTimeout() {
        bootstrap(V1);
        runSql("DROP TABLE fp_seq");

        try (HeldLock build = HeldLock.on("fp_items", "SHARE UPDATE EXCLUSIVE")) {
            final long start = System.nanoTime();
            final Throwable error = catchThrowable(() -> heal(V1));
            assertThat(sqlState(error)).as("lock_not_available, got %s", error).isEqualTo(LOCK_NOT_AVAILABLE);
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(3));
            assertThat(tableExists("fp_seq")).as("the failed heal changed nothing").isFalse();
        }
    }

    // ---- helpers ---------------------------------------------------------------------------------------------------

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

    private static PostgresSchemaDescriptor descriptor(final int version, final List<String> ddl) {
        final List<TableContract> contracts = List.of(
                new TableContract("fp_items", "PRIMARY KEY (id)", Map.of("id", "text", "val", "bigint"),
                        Set.of("id")),
                new TableContract("fp_seq", "PRIMARY KEY (id)", Map.of("id", "text", "n", "bigint"), Set.of("id")));
        return new PostgresSchemaDescriptor() {
            @Override
            public String component() {
                return COMPONENT;
            }

            @Override
            public int version() {
                return version;
            }

            @Override
            public int minCompatibleVersion() {
                return 1;
            }

            @Override
            public List<String> ddlStatements() {
                return ddl;
            }

            @Override
            public List<TableContract> tableContracts() {
                return contracts;
            }
        };
    }

    private static void bootstrap(final PostgresSchemaDescriptor descriptor) {
        PostgresSchemaManager.of(connectionFactory, descriptor, TIMEOUT).bootstrap();
    }

    @Nullable
    private static SchemaVersionDecision heal(final PostgresSchemaDescriptor descriptor) {
        return PostgresSchemaManager.of(connectionFactory, descriptor, TIMEOUT).ensureTablesReactive().block(TIMEOUT);
    }

    private static String sqlState(@Nullable final Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof R2dbcException r2dbc && r2dbc.getSqlState() != null) {
                return r2dbc.getSqlState();
            }
        }
        return "none";
    }

    private List<String> messages(final Level level) {
        return managerLog.list.stream()
                .filter(event -> event.getLevel().equals(level))
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    private static String storedRow() {
        return scalar("SELECT version || '/' || checksum FROM schema_version WHERE component = '" + COMPONENT + "'");
    }

    private static String reloptions(final String table) {
        return scalar("SELECT coalesce(array_to_string(reloptions, ','), '" + NO_RELOPTIONS + "') FROM pg_class "
                + "WHERE oid = to_regclass('" + table + "')");
    }

    private static boolean tableExists(final String table) {
        return Boolean.parseBoolean(scalar("SELECT EXISTS (SELECT 1 FROM information_schema.tables "
                + "WHERE table_schema = current_schema() AND table_name = '" + table + "')"));
    }

    private static boolean indexExists(final String index) {
        return Boolean.parseBoolean(scalar("SELECT EXISTS (SELECT 1 FROM pg_indexes "
                + "WHERE schemaname = current_schema() AND indexname = '" + index + "')"));
    }

    private static String scalar(final String sql) {
        return Mono.usingWhen(Mono.from(connectionFactory.create()),
                        conn -> Flux.from(conn.createStatement(sql).execute())
                                .flatMap(result -> result.map((row, meta) -> String.valueOf(row.get(0))))
                                .next(),
                        Connection::close)
                .block(TIMEOUT);
    }

    private static void runSql(final String sql) {
        Mono.usingWhen(Mono.from(connectionFactory.create()),
                        conn -> Flux.from(conn.createStatement(sql).execute())
                                .flatMap(Result::getRowsUpdated)
                                .then(),
                        Connection::close)
                .block(TIMEOUT);
    }

}
