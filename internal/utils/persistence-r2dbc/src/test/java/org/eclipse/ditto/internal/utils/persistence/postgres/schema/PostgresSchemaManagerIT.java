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
package org.eclipse.ditto.internal.utils.persistence.postgres.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.PostgresSchemaManager;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.SchemaBootException;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.testkit.PostgresDbResource;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.Result;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Integration test for {@link PostgresSchemaManager} against a real PostgreSQL (PG 16) via Testcontainers. The upgrade
 * and mixed-version cases run the literal v2 and v3 fixtures ({@link PostgresSchemaV2}, {@link PostgresSchemaV3})
 * against the current schema.
 * <p>
 * Runs under the failsafe phase ({@code *IT}); skipped offline / when no Docker daemon is reachable (the
 * {@link #startContainer() Assume} guard turns an unreachable Docker into a skip, not a failure).
 * </p>
 */
public final class PostgresSchemaManagerIT {

    private static final PostgresDbResource POSTGRES = new PostgresDbResource();
    private static ConnectionFactory connectionFactory;

    @BeforeClass
    public static void startContainer() {
        try {
            POSTGRES.start();
        } catch (final Throwable t) {
            Assume.assumeNoException("Docker/Testcontainers unavailable — skipping PostgresSchemaManagerIT", t);
        }
        connectionFactory = POSTGRES.newConnectionFactory();
    }

    @AfterClass
    public static void stopContainer() {
        POSTGRES.stop();
    }

    @Test
    public void bootstrapCreatesAllTablesAndIsIdempotent() {
        PostgresSchemaManager.of(connectionFactory, PostgresSchema.descriptor()).bootstrap();

        // every entity's three tables exist
        for (final String entity : PostgresSchema.ENTITIES) {
            assertThat(tableExists(entity + "_journal")).isTrue();
            assertThat(tableExists(entity + "_journal_seq")).isTrue();
            assertThat(tableExists(entity + "_snaps")).isTrue();
        }
        assertThat(tableExists("schema_version")).isTrue();

        // re-run is idempotent (IF NOT EXISTS + checksum match)
        PostgresSchemaManager.of(connectionFactory, PostgresSchema.descriptor()).bootstrap();
        assertThat(scalar("SELECT checksum FROM schema_version WHERE component = '"
                + PostgresSchema.COMPONENT + "'")).isEqualTo(PostgresSchemaManager.checksum(PostgresSchema.descriptor()));
    }

    @Test
    public void divergentPreExistingPkRefusesToBoot() {
        // Pre-create a divergent things_journal with the OLD (pid, sn, written_at) PK, then bootstrap:
        // CREATE TABLE IF NOT EXISTS keeps the existing table, so the live-catalog check must detect the
        // mismatch and refuse to boot rather than silently running against a schema with the wrong primary key.
        runDdl("DROP TABLE IF EXISTS things_journal CASCADE");
        runDdl("CREATE TABLE things_journal (pid TEXT, sn BIGINT, seq BIGINT GENERATED ALWAYS AS IDENTITY, "
                + "manifest TEXT, tags TEXT[], event JSONB, written_at TIMESTAMPTZ, "
                + "PRIMARY KEY (pid, sn, written_at))");

        assertThatThrownBy(() -> PostgresSchemaManager.of(connectionFactory, PostgresSchema.descriptor()).bootstrap())
                .isInstanceOf(SchemaBootException.class)
                .hasMessageContaining("divergent primary key");

        // cleanup so the idempotent test is order-independent
        runDdl("DROP TABLE IF EXISTS things_journal CASCADE");
    }

    @Test
    public void bootstrapIgnoresSameNamedTablesInOtherSchemas() {
        // A decoy schema with a WRONG-shaped things_journal must not corrupt verification of the real one: an
        // unqualified pg_constraint/information_schema.columns lookup by bare table name would merge/collide the
        // decoy's catalog rows into the real table's, causing a false SchemaBootException (or a false pass).
        runDdl("CREATE SCHEMA IF NOT EXISTS decoy");
        runDdl("CREATE TABLE IF NOT EXISTS decoy.things_journal (wrong BIGINT PRIMARY KEY)");
        try {
            PostgresSchemaManager.of(connectionFactory, PostgresSchema.descriptor()).bootstrap();
        } finally {
            // cleanup so repeated runs against the same container stay deterministic.
            runDdl("DROP SCHEMA IF EXISTS decoy CASCADE");
        }
    }

    @Test
    public void currentCodeBootsAgainstANewerCompatibleSchemaWithoutRunningDdlOrTouchingTheRow() {
        final int newer = PostgresSchema.VERSION + 1;
        PostgresSchemaManager.of(connectionFactory, PostgresSchema.descriptor()).bootstrap();
        // Simulate a newer pod mid-roll: it advanced the shared row (declaring this version compatible) and dropped
        // an index that this code's DDL creates.
        runDdl("UPDATE schema_version SET version = " + newer + ", checksum = 'newer-checksum', "
                + "min_compatible_version = " + PostgresSchema.VERSION
                + " WHERE component = '" + PostgresSchema.COMPONENT + "'");
        runDdl("DROP INDEX things_journal_tags_idx");
        try {
            // A pod of this version restarts: it must boot, run no DDL and leave the newer row alone.
            PostgresSchemaManager.of(connectionFactory, PostgresSchema.descriptor()).bootstrap();

            assertThat(indexExists("things_journal_tags_idx"))
                    .as("this code's DDL must not run against the newer schema")
                    .isFalse();
            assertThat(storedRow()).isEqualTo(newer + "/newer-checksum/" + PostgresSchema.VERSION);
        } finally {
            restoreCurrentSchema();
        }
    }

    @Test
    public void currentCodeRefusesANewerIncompatibleSchema() {
        final int newer = PostgresSchema.VERSION + 1;
        PostgresSchemaManager.of(connectionFactory, PostgresSchema.descriptor()).bootstrap();
        runDdl("UPDATE schema_version SET version = " + newer + ", checksum = 'newer-checksum', "
                + "min_compatible_version = " + newer + " WHERE component = '" + PostgresSchema.COMPONENT + "'");
        try {
            assertThatThrownBy(() -> PostgresSchemaManager.of(connectionFactory, PostgresSchema.descriptor())
                    .bootstrap())
                    .isInstanceOf(SchemaBootException.class)
                    .hasMessageContaining("min_compatible_version " + newer);
            assertThat(storedRow()).isEqualTo(newer + "/newer-checksum/" + newer);
        } finally {
            restoreCurrentSchema();
        }
    }

    private static final List<String> PRUNED_TABLE_V3 =
            List.of("autovacuum_vacuum_scale_factor=0.01", "autovacuum_vacuum_cost_limit=2000");
    private static final List<String> SCALE_FACTOR_ONLY = List.of("autovacuum_vacuum_scale_factor=0.01");

    @Test
    public void freshBootstrapCreatesNoLifecycleIndexAndSetsTheV3Reloptions() {
        reset();

        PostgresSchemaManager.of(connectionFactory, PostgresSchema.descriptor()).bootstrap();

        for (final String e : PostgresSchema.ENTITIES) {
            assertThat(indexExists(e + "_snaps_lifecycle_idx")).as(e).isFalse();
            assertThat(reloptions(e + "_journal")).as(e).containsExactlyInAnyOrderElementsOf(PRUNED_TABLE_V3);
            assertThat(reloptions(e + "_snaps")).as(e).containsExactlyInAnyOrderElementsOf(PRUNED_TABLE_V3);
            assertThat(reloptions(e + "_journal_seq")).as(e).containsExactlyElementsOf(SCALE_FACTOR_ONLY);
        }
        assertThat(storedRow()).isEqualTo("4/" + PostgresSchemaManager.checksum(PostgresSchema.descriptor()) + "/2");
    }

    @Test
    public void upgradeFromV2DropsTheLifecycleIndexesSetsTheReloptionsAddsTheRememberTablesAndAdvancesTheRowTo4() {
        reset();
        PostgresSchemaManager.of(connectionFactory, PostgresSchemaV2.descriptor()).bootstrap();
        assertThat(storedRow()).isEqualTo("2/" + PostgresSchemaV2.CHECKSUM + "/2");
        for (final String e : PostgresSchemaV3.ENTITIES) {
            assertThat(indexExists(e + "_snaps_lifecycle_idx")).as(e + " before the upgrade").isTrue();
        }
        assertThat(reloptions("things_journal")).containsExactlyElementsOf(SCALE_FACTOR_ONLY);
        final String journalFile = relfilenode("things_journal");
        final String snapsFile = relfilenode("things_snaps");

        // the first v4 pod of a rolling upgrade
        PostgresSchemaManager.of(connectionFactory, PostgresSchema.descriptor()).bootstrap();

        for (final String e : PostgresSchema.ENTITIES) {
            assertThat(indexExists(e + "_snaps_lifecycle_idx")).as(e + ": the upgrade drops it").isFalse();
            assertThat(reloptions(e + "_journal")).as(e).containsExactlyInAnyOrderElementsOf(PRUNED_TABLE_V3);
            assertThat(reloptions(e + "_snaps")).as(e).containsExactlyInAnyOrderElementsOf(PRUNED_TABLE_V3);
            assertThat(reloptions(e + "_journal_seq")).as(e).containsExactlyElementsOf(SCALE_FACTOR_ONLY);
        }
        assertThat(relfilenode("things_journal")).as("setting reloptions does not rewrite the table")
                .isEqualTo(journalFile);
        assertThat(relfilenode("things_snaps")).as("setting reloptions does not rewrite the table")
                .isEqualTo(snapsFile);
        assertThat(tableExists("connection_remember_journal")).as("the upgrade also adds the v4 table set").isTrue();
        assertThat(storedRow()).isEqualTo("4/" + PostgresSchemaManager.checksum(PostgresSchema.descriptor()) + "/2");
    }

    @Test
    public void upgradeFromV3CreatesTheConnectionRememberTablesAndAdvancesTheRowTo4() {
        reset();
        PostgresSchemaManager.of(connectionFactory, PostgresSchemaV3.descriptor()).bootstrap();
        assertThat(storedRow()).isEqualTo("3/" + PostgresSchemaV3.CHECKSUM + "/2");
        for (final String table : List.of("connection_remember_journal", "connection_remember_journal_seq",
                "connection_remember_snaps")) {
            assertThat(tableExists(table)).as(table + " before the upgrade").isFalse();
        }
        final String journalFile = relfilenode("things_journal");

        // the first v4 pod of a rolling upgrade
        PostgresSchemaManager.of(connectionFactory, PostgresSchema.descriptor()).bootstrap();

        for (final String table : List.of("connection_remember_journal", "connection_remember_journal_seq",
                "connection_remember_snaps")) {
            assertThat(tableExists(table)).as(table + ": the upgrade creates it").isTrue();
        }
        assertThat(indexExists("connection_remember_journal_tags_idx")).isTrue();
        assertThat(reloptions("connection_remember_journal")).containsExactlyInAnyOrderElementsOf(PRUNED_TABLE_V3);
        assertThat(reloptions("connection_remember_snaps")).containsExactlyInAnyOrderElementsOf(PRUNED_TABLE_V3);
        assertThat(reloptions("connection_remember_journal_seq")).containsExactlyElementsOf(SCALE_FACTOR_ONLY);
        assertThat(relfilenode("things_journal")).as("the existing tables are not rewritten").isEqualTo(journalFile);
        assertThat(storedRow()).isEqualTo("4/" + PostgresSchemaManager.checksum(PostgresSchema.descriptor()) + "/2");
    }

    @Test
    public void v3BootsAgainstAStoredV4SchemaRunningNoDdl() {
        reset();
        PostgresSchemaManager.of(connectionFactory, PostgresSchemaV3.descriptor()).bootstrap();
        PostgresSchemaManager.of(connectionFactory, PostgresSchema.descriptor()).bootstrap();
        final String v4Row = storedRow();
        final ListAppender<ILoggingEvent> managerLog = captureManagerLog();
        try {
            // A not-yet-upgraded v3 pod restarts mid-roll: v4 declares v2+ compatible, so it boots, runs no DDL and
            // leaves the v4 row alone.
            PostgresSchemaManager.of(connectionFactory, PostgresSchemaV3.descriptor()).bootstrap();

            assertThat(storedRow()).as("the row is not written back to v3").isEqualTo(v4Row)
                    .startsWith("4/").endsWith("/2");
            assertThat(managerLog.list.stream()
                    .filter(event -> event.getLevel() == Level.INFO)
                    .map(ILoggingEvent::getFormattedMessage))
                    .anySatisfy(message -> assertThat(message)
                            .contains("stored schema version 4")
                            .contains("this code's version 3")
                            .contains("Running no DDL"));
        } finally {
            releaseManagerLog(managerLog);
        }
    }

    @Test
    public void v2BootsAgainstAStoredV4SchemaWithoutRecreatingTheLifecycleIndex() {
        reset();
        PostgresSchemaManager.of(connectionFactory, PostgresSchemaV2.descriptor()).bootstrap();
        PostgresSchemaManager.of(connectionFactory, PostgresSchema.descriptor()).bootstrap();
        final String v4Row = storedRow();
        final ListAppender<ILoggingEvent> managerLog = captureManagerLog();
        try {
            // A not-yet-upgraded v2 pod restarts mid-roll. Its DDL (CREATE INDEX IF NOT EXISTS <e>_snaps_lifecycle_idx)
            // would re-create the indexes v3 dropped: their absence afterwards proves that no DDL ran.
            PostgresSchemaManager.of(connectionFactory, PostgresSchemaV2.descriptor()).bootstrap();

            for (final String e : PostgresSchema.ENTITIES) {
                assertThat(indexExists(e + "_snaps_lifecycle_idx")).as(e + ": v2 DDL must not run against v4")
                        .isFalse();
                assertThat(reloptions(e + "_journal")).as(e).containsExactlyInAnyOrderElementsOf(PRUNED_TABLE_V3);
            }
            assertThat(storedRow()).as("the row is not written back to v2").isEqualTo(v4Row)
                    .startsWith("4/").endsWith("/2");
            assertThat(managerLog.list.stream()
                    .filter(event -> event.getLevel() == Level.INFO)
                    .map(ILoggingEvent::getFormattedMessage))
                    .anySatisfy(message -> assertThat(message)
                            .contains("stored schema version 4")
                            .contains("this code's version 2")
                            .contains("Running no DDL"));
        } finally {
            releaseManagerLog(managerLog);
        }
    }

    /** Puts the shared row back to this code's values and re-runs the bootstrap (re-creates dropped indexes). */
    private static void restoreCurrentSchema() {
        runDdl("UPDATE schema_version SET version = " + PostgresSchema.VERSION + ", checksum = '"
                + PostgresSchemaManager.checksum(PostgresSchema.descriptor()) + "', min_compatible_version = "
                + PostgresSchema.MIN_COMPATIBLE_VERSION + " WHERE component = '" + PostgresSchema.COMPONENT + "'");
        PostgresSchemaManager.of(connectionFactory, PostgresSchema.descriptor()).bootstrap();
    }

    /** Drops every persistence table and the shared row table: the next bootstrap starts from an empty database. */
    private static void reset() {
        final String tables = PostgresSchema.ENTITIES.stream()
                .flatMap(e -> Stream.of(e + "_journal", e + "_journal_seq", e + "_snaps"))
                .collect(Collectors.joining(", "));
        runDdl("DROP TABLE IF EXISTS " + tables + ", schema_version CASCADE");
    }

    private static List<String> reloptions(final String table) {
        final String joined = scalar("SELECT coalesce(array_to_string(reloptions, ','), '') FROM pg_class "
                + "WHERE oid = to_regclass('" + table + "')");
        return joined.isEmpty() ? List.of() : List.of(joined.split(","));
    }

    private static String relfilenode(final String table) {
        return scalar("SELECT relfilenode::text FROM pg_class WHERE oid = to_regclass('" + table + "')");
    }

    private static ListAppender<ILoggingEvent> captureManagerLog() {
        // logback-test.xml roots at WARN; the compatible-newer line is INFO
        final Logger logger = (Logger) LoggerFactory.getLogger(PostgresSchemaManager.class);
        logger.setLevel(Level.INFO);
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private static void releaseManagerLog(final ListAppender<ILoggingEvent> appender) {
        final Logger logger = (Logger) LoggerFactory.getLogger(PostgresSchemaManager.class);
        logger.detachAppender(appender);
        logger.setLevel(null);
        appender.stop();
    }

    private static String storedRow() {
        return scalar("SELECT version || '/' || checksum || '/' || min_compatible_version FROM schema_version "
                + "WHERE component = '" + PostgresSchema.COMPONENT + "'");
    }

    private static boolean indexExists(final String index) {
        return Boolean.parseBoolean(scalar(
                "SELECT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = '" + index + "')"));
    }

    private static boolean tableExists(final String table) {
        return Boolean.parseBoolean(scalar(
                "SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = '" + table + "')"));
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
