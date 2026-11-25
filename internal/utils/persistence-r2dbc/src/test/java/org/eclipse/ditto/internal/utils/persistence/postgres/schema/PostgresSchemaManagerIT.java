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

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.Result;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Integration test for {@link PostgresSchemaManager} against a real PostgreSQL (PG 16) via Testcontainers.
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
        assertThat(tableExists(PostgresSchema.ENCRYPTION_MIGRATION_TABLE)).as("the progress table").isTrue();
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

    private static final List<String> PRUNED_TABLE =
            List.of("autovacuum_vacuum_scale_factor=0.01", "autovacuum_vacuum_cost_limit=2000");
    private static final List<String> SCALE_FACTOR_ONLY = List.of("autovacuum_vacuum_scale_factor=0.01");

    @Test
    public void freshBootstrapSetsTheReloptionsCreatesNoSnapshotIndexAndStoresTheRow() {
        reset();

        PostgresSchemaManager.of(connectionFactory, PostgresSchema.descriptor()).bootstrap();

        for (final String e : PostgresSchema.ENTITIES) {
            assertThat(scalar("SELECT count(*) FROM pg_indexes WHERE tablename = '" + e + "_snaps'"))
                    .as(e + "_snaps: the primary key index only").isEqualTo("1");
            assertThat(reloptions(e + "_journal")).as(e).containsExactlyInAnyOrderElementsOf(PRUNED_TABLE);
            assertThat(reloptions(e + "_snaps")).as(e).containsExactlyInAnyOrderElementsOf(PRUNED_TABLE);
            assertThat(reloptions(e + "_journal_seq")).as(e).containsExactlyElementsOf(SCALE_FACTOR_ONLY);
        }
        assertThat(reloptions(PostgresSchema.ENCRYPTION_MIGRATION_TABLE)).as("one row: no autovacuum tuning")
                .isEmpty();
        assertThat(scalar("SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE contype = 'p' AND conrelid = "
                + "to_regclass('" + PostgresSchema.ENCRYPTION_MIGRATION_TABLE + "')")).isEqualTo("PRIMARY KEY (id)");
        assertThat(storedRow()).isEqualTo("1/" + PostgresSchemaManager.checksum(PostgresSchema.descriptor()) + "/1");
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
        runDdl("DROP TABLE IF EXISTS " + tables + ", " + PostgresSchema.ENCRYPTION_MIGRATION_TABLE
                + ", schema_version CASCADE");
    }

    private static List<String> reloptions(final String table) {
        final String joined = scalar("SELECT coalesce(array_to_string(reloptions, ','), '') FROM pg_class "
                + "WHERE oid = to_regclass('" + table + "')");
        return joined.isEmpty() ? List.of() : List.of(joined.split(","));
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
