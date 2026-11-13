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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import org.eclipse.ditto.internal.utils.metrics.DittoMetrics;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.PostgresSchemaHealer;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.config.DefaultPostgresConfig;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.monitoring.PostgresMetrics;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.testkit.PostgresDbResource;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import com.typesafe.config.ConfigFactory;

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
 * Rolling-upgrade integration test for {@link PostgresSchemaManager} against a real PostgreSQL (Testcontainers): a
 * vN pod booting (or self-healing) against a {@code schema_version} row already advanced to vN+1 by an upgraded pod.
 * <p>
 * Uses a synthetic component with two schema versions: v1 creates {@code compat_items} plus the index
 * {@code compat_items_val_idx}; v2 drops that index and adds a nullable column, a backward-compatible change, so v2
 * normally declares {@code minCompatibleVersion = 1}. "No DDL ran" is proven by that index: v1's DDL would re-create
 * it, so after a v1 boot/heal against a v2 row it must still be absent.
 * </p>
 * <p>
 * Runs under failsafe ({@code *IT}); skipped when no Docker daemon is reachable.
 * </p>
 */
public final class PostgresSchemaManagerCompatibilityIT {

    private static final String COMPONENT = "compat-it";
    private static final String TABLE = "compat_items";
    private static final String V1_INDEX = "compat_items_val_idx";
    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    private static final TableContract CONTRACT = new TableContract(TABLE, "PRIMARY KEY (id)",
            Map.of("id", "text", "val", "bigint"), Set.of("id"));

    private static final String CREATE_TABLE = "CREATE TABLE IF NOT EXISTS " + TABLE + " ("
            + "id TEXT COLLATE \"C\" PRIMARY KEY, val BIGINT NOT NULL)";

    /** v1: the table plus an index on {@code val}. */
    private static final PostgresSchemaDescriptor V1 = descriptor(1, 1, List.of(
            CREATE_TABLE,
            "CREATE INDEX IF NOT EXISTS " + V1_INDEX + " ON " + TABLE + " (val)"));

    private static final List<String> V2_DDL = List.of(
            CREATE_TABLE,
            "DROP INDEX IF EXISTS " + V1_INDEX,
            "ALTER TABLE " + TABLE + " ADD COLUMN IF NOT EXISTS note TEXT");

    /** v2, backward compatible with v1 (index drop + nullable column). */
    private static final PostgresSchemaDescriptor V2_COMPATIBLE = descriptor(2, 1, V2_DDL);

    /** The same v2 DDL, but declared incompatible with v1 ({@code minCompatibleVersion = 2}). */
    private static final PostgresSchemaDescriptor V2_INCOMPATIBLE = descriptor(2, 2, V2_DDL);

    /** A login role that may use and create in the schema and has DML on schema_version, but owns nothing. */
    private static final String READER_ROLE = "compat_reader";
    private static final String READER_PASSWORD = "reader_secret";

    private static final PostgresDbResource POSTGRES = new PostgresDbResource();
    private static ConnectionFactory connectionFactory;

    private final ListAppender<ILoggingEvent> managerLog = new ListAppender<>();
    private final ListAppender<ILoggingEvent> healerLog = new ListAppender<>();
    @Nullable private Level originalManagerLevel;

    @BeforeClass
    public static void startContainer() {
        try {
            POSTGRES.start();
        } catch (final Throwable t) {
            Assume.assumeNoException(
                    "Docker/Testcontainers unavailable — skipping PostgresSchemaManagerCompatibilityIT", t);
        }
        connectionFactory = POSTGRES.newConnectionFactory();
    }

    @AfterClass
    public static void stopContainer() {
        POSTGRES.stop();
    }

    @Before
    public void resetDatabase() {
        dropReaderRole();
        runSql("DROP TABLE IF EXISTS " + TABLE + ", schema_version CASCADE");
    }

    @Before
    public void captureLogs() {
        // logback-test.xml roots at WARN; the manager's compatible-newer and completion lines are INFO.
        final Logger manager = (Logger) LoggerFactory.getLogger(PostgresSchemaManager.class);
        originalManagerLevel = manager.getLevel();
        manager.setLevel(Level.INFO);
        managerLog.start();
        manager.addAppender(managerLog);
        healerLog.start();
        ((Logger) LoggerFactory.getLogger(PostgresSchemaHealer.class)).addAppender(healerLog);
    }

    @After
    public void releaseLogs() {
        final Logger manager = (Logger) LoggerFactory.getLogger(PostgresSchemaManager.class);
        manager.detachAppender(managerLog);
        manager.setLevel(originalManagerLevel);
        managerLog.stop();
        ((Logger) LoggerFactory.getLogger(PostgresSchemaHealer.class)).detachAppender(healerLog);
        healerLog.stop();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // boot
    // ---------------------------------------------------------------------------------------------------------------

    @Test
    public void firstBootWritesVersionChecksumAndMinCompatibleVersion() {
        bootstrap(V1);

        assertThat(indexExists(V1_INDEX)).isTrue();
        assertThat(storedRow()).isEqualTo(new Row(1, PostgresSchemaManager.checksum(V1), 1));
    }

    @Test
    public void upgradeAdvancesTheRowAndWritesTheCodesMinCompatibleVersion() {
        bootstrap(V1);
        bootstrap(V2_COMPATIBLE);

        assertThat(indexExists(V1_INDEX)).as("v2 DDL dropped the v1 index").isFalse();
        assertThat(columnExists(TABLE, "note")).isTrue();
        assertThat(storedRow()).isEqualTo(new Row(2, PostgresSchemaManager.checksum(V2_COMPATIBLE), 1));
    }

    @Test
    public void olderCodeBootsAgainstNewerCompatibleSchemaWithoutRunningDdlOrTouchingTheRow() {
        bootstrap(V1);
        bootstrap(V2_COMPATIBLE);
        final Row v2Row = storedRow();

        // A not-yet-upgraded v1 pod restarts mid-roll.
        bootstrap(V1);

        assertThat(indexExists(V1_INDEX))
                .as("v1 must not re-create the index v2 dropped (no DDL in the compatible-newer path)")
                .isFalse();
        assertThat(columnExists(TABLE, "note")).as("the v2 column is untouched").isTrue();
        assertThat(storedRow()).as("the row is not written back to v1").isEqualTo(v2Row);
        assertThat(v2Row).isEqualTo(new Row(2, PostgresSchemaManager.checksum(V2_COMPATIBLE), 1));
    }

    @Test
    public void olderCodeRefusesNewerIncompatibleSchema() {
        bootstrap(V1);
        bootstrap(V2_INCOMPATIBLE);
        final Row v2Row = storedRow();

        assertThatThrownBy(() -> bootstrap(V1))
                .isInstanceOf(SchemaBootException.class)
                .hasMessageContaining("min_compatible_version 2")
                .hasMessageContaining("stored schema version 2");

        assertThat(indexExists(V1_INDEX)).isFalse();
        assertThat(storedRow()).isEqualTo(v2Row);
        assertThat(v2Row.minCompatibleVersion()).isEqualTo(2);
    }

    @Test
    public void compatibleNewerBootStillVerifiesTheColumnsThisCodeNeeds() {
        bootstrap(V1);
        bootstrap(V2_COMPATIBLE);
        // Break a fact v1 relies on (the val column's type) behind the manager's back; the compatible-newer path runs
        // no DDL but must still refuse on catalog divergence.
        runSql("ALTER TABLE " + TABLE + " ALTER COLUMN val TYPE TEXT");

        assertThatThrownBy(() -> bootstrap(V1))
                .isInstanceOf(SchemaBootException.class)
                .hasMessageContaining("'val'")
                .hasMessageContaining("data_type");
    }

    @Test
    public void compatibleNewerBootLogsBothVersionsAndDoesNotReportTheCodesChecksumAsStored() {
        bootstrap(V1);
        bootstrap(V2_COMPATIBLE);
        managerLog.list.clear();

        bootstrap(V1);

        assertThat(messages(managerLog, Level.INFO))
                .anySatisfy(message -> assertThat(message)
                        .contains("stored schema version 2")
                        .contains("this code's version 1")
                        .contains("min_compatible_version 1")
                        .contains("Running no DDL"))
                .anySatisfy(message -> assertThat(message)
                        .contains("bootstrap complete")
                        .contains("stored schema version 2"))
                .as("the v1 checksum is not the stored one and must not be logged as if it were")
                .noneSatisfy(message -> assertThat(message).contains(PostgresSchemaManager.checksum(V1)));
    }

    @Test
    public void compatibleNewerBootAndHealNeedNoOwnershipOfSchemaVersion() {
        bootstrap(V1);
        bootstrap(V2_COMPATIBLE);
        final Row v2Row = storedRow();
        // A role that may create in the schema and read/write schema_version (all a boot needed before the
        // min_compatible_version column existed) but owns neither table. ALTER TABLE checks ownership before
        // IF NOT EXISTS, so it must not even be issued once the column exists.
        runSql("CREATE ROLE " + READER_ROLE + " LOGIN PASSWORD '" + READER_PASSWORD + "'");
        runSql("GRANT USAGE, CREATE ON SCHEMA public TO " + READER_ROLE);
        runSql("GRANT SELECT, INSERT, UPDATE ON schema_version TO " + READER_ROLE);
        runSql("GRANT SELECT ON " + TABLE + " TO " + READER_ROLE);
        final ConnectionFactory readerFactory = POSTGRES.newConnectionFactory(READER_ROLE, READER_PASSWORD);

        PostgresSchemaManager.of(readerFactory, V1, TIMEOUT).bootstrap();
        PostgresSchemaManager.of(readerFactory, V1, TIMEOUT).ensureTablesReactive().block(TIMEOUT);

        assertThat(indexExists(V1_INDEX)).isFalse();
        assertThat(storedRow()).isEqualTo(v2Row);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // legacy (pre-min_compatible_version) schema_version table
    // ---------------------------------------------------------------------------------------------------------------

    @Test
    public void legacyRowWithoutTheColumnGetsTheColumnAddedAndBackfilled() {
        createLegacySchemaVersionRow(1, PostgresSchemaManager.checksum(V1));
        assertThat(columnExists("schema_version", "min_compatible_version")).isFalse();

        bootstrap(V1);

        assertThat(columnExists("schema_version", "min_compatible_version")).isTrue();
        assertThat(storedRow()).isEqualTo(new Row(1, PostgresSchemaManager.checksum(V1), 1));
    }

    @Test
    public void legacyNewerRowTreatsMissingMinCompatibleVersionAsTheStoredVersion() {
        // Written by a pre-T05 v2 pod: no min_compatible_version column. NULL counts as min = version = 2 > 1
        // -> refuse.
        createLegacySchemaVersionRow(2, PostgresSchemaManager.checksum(V2_COMPATIBLE));

        assertThatThrownBy(() -> bootstrap(V1))
                .isInstanceOf(SchemaBootException.class)
                .hasMessageContaining("min_compatible_version 2");
    }

    // ---------------------------------------------------------------------------------------------------------------
    // self-heal (ensureTablesReactive)
    // ---------------------------------------------------------------------------------------------------------------

    @Test
    public void healWithNewerCompatibleStoredVersionRunsNoDdlAndLeavesTheRowUnchanged() {
        bootstrap(V1);
        bootstrap(V2_COMPATIBLE);
        final Row v2Row = storedRow();
        // The heal trigger: a missing table under the newer schema.
        runSql("DROP TABLE " + TABLE);

        heal(V1);

        assertThat(tableExists(TABLE)).as("a v1 heal against a newer compatible schema runs no DDL").isFalse();
        assertThat(indexExists(V1_INDEX)).isFalse();
        assertThat(storedRow()).isEqualTo(v2Row);
    }

    @Test
    public void healWithNewerIncompatibleStoredVersionFailsWithoutDdl() {
        bootstrap(V1);
        bootstrap(V2_INCOMPATIBLE);
        final Row v2Row = storedRow();
        runSql("DROP TABLE " + TABLE);

        assertThatThrownBy(() -> heal(V1))
                .isInstanceOf(SchemaBootException.class)
                .hasMessageContaining("min_compatible_version 2");

        assertThat(tableExists(TABLE)).isFalse();
        assertThat(storedRow()).isEqualTo(v2Row);
    }

    @Test
    public void healWithSameStoredVersionStillRecreatesMissingTables() {
        // Unchanged behavior: at the same (or an older) version the heal re-runs the create-only DDL.
        bootstrap(V1);
        final Row v1Row = storedRow();
        runSql("DROP TABLE " + TABLE);

        heal(V1);

        assertThat(tableExists(TABLE)).isTrue();
        assertThat(indexExists(V1_INDEX)).isTrue();
        assertThat(storedRow()).as("a heal never writes the row").isEqualTo(v1Row);
    }

    @Test
    public void healOnAWipedDatabaseRecreatesTheTablesAndTheCompatibilityColumn() {
        // Nothing exists (not even schema_version): heal recreates everything, as before, and leaves no row.
        heal(V1);

        assertThat(tableExists(TABLE)).isTrue();
        assertThat(columnExists("schema_version", "min_compatible_version")).isTrue();
        assertThat(storedRow()).isNull();
    }

    @Test
    public void healerCountsAndWarnsWhenItRecreatedTables() {
        bootstrap(V1);
        runSql("DROP TABLE " + TABLE);
        final long before = healCount();

        healWithHealer(V1);

        assertThat(tableExists(TABLE)).isTrue();
        assertThat(healCount()).as("a heal that ran DDL is counted").isEqualTo(before + 1);
        assertThat(messages(healerLog, Level.WARN))
                .anySatisfy(message -> assertThat(message).contains("Missing PostgreSQL table detected")
                        .contains("recreated"));
    }

    @Test
    public void healerDoesNotReportANoOpHealAsARecreation() {
        bootstrap(V1);
        bootstrap(V2_COMPATIBLE);
        runSql("DROP TABLE " + TABLE);
        final long before = healCount();

        healWithHealer(V1);

        assertThat(tableExists(TABLE)).as("no DDL against the newer compatible schema").isFalse();
        assertThat(healCount()).as("a heal that ran no DDL is not counted").isEqualTo(before);
        assertThat(messages(healerLog, Level.WARN)).noneSatisfy(message -> assertThat(message).contains("recreat"));
        assertThat(messages(managerLog, Level.INFO))
                .anySatisfy(message -> assertThat(message).contains("self-heal").contains("Running no DDL"));
    }

    // ---------------------------------------------------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------------------------------------------------

    private record Row(int version, String checksum, @Nullable Integer minCompatibleVersion) {}

    private static PostgresSchemaDescriptor descriptor(final int version, final int minCompatibleVersion,
            final List<String> ddl) {
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
                return minCompatibleVersion;
            }

            @Override
            public List<String> ddlStatements() {
                return ddl;
            }

            @Override
            public List<TableContract> tableContracts() {
                return List.of(CONTRACT);
            }
        };
    }

    private static void bootstrap(final PostgresSchemaDescriptor descriptor) {
        PostgresSchemaManager.of(connectionFactory, descriptor, TIMEOUT).bootstrap();
    }

    private static void heal(final PostgresSchemaDescriptor descriptor) {
        PostgresSchemaManager.of(connectionFactory, descriptor, TIMEOUT).ensureTablesReactive().block(TIMEOUT);
    }

    private static void healWithHealer(final PostgresSchemaDescriptor descriptor) {
        final PostgresSchemaHealer healer = PostgresSchemaHealer.of(DefaultPostgresConfig.of(ConfigFactory.parseString(
                        "ditto.postgresql.uri = \"" + POSTGRES.getR2dbcUrl() + "\"\n"
                                + "ditto.postgresql.ssl.mode = \"disable\"\n"
                                + "ditto.postgresql.schema.self-heal = true\n")
                .getConfig("ditto")));
        try {
            healer.registerDescriptor(descriptor);
            healer.heal().block(TIMEOUT);
        } finally {
            healer.dispose();
        }
    }

    private static long healCount() {
        return DittoMetrics.counter(PostgresMetrics.SELF_HEAL_COUNT)
                .tag(PostgresMetrics.TAG_ENGINE, PostgresMetrics.ENGINE_POSTGRES)
                .getCount();
    }

    private static List<String> messages(final ListAppender<ILoggingEvent> appender, final Level level) {
        return appender.list.stream()
                .filter(event -> event.getLevel().equals(level))
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    private static void dropReaderRole() {
        runSql("DO $$ BEGIN IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '" + READER_ROLE + "') THEN "
                + "EXECUTE 'DROP OWNED BY " + READER_ROLE + "'; EXECUTE 'DROP ROLE " + READER_ROLE + "'; "
                + "END IF; END $$");
    }

    /** The schema_version table exactly as pre-T05 code created it (no min_compatible_version column) + one row. */
    private static void createLegacySchemaVersionRow(final int version, final String checksum) {
        runSql(PostgresSchemaManager.SCHEMA_VERSION_DDL);
        runSql("INSERT INTO schema_version (component, version, checksum) VALUES ('" + COMPONENT + "', " + version
                + ", '" + checksum + "')");
    }

    @Nullable
    private static Row storedRow() {
        return Mono.usingWhen(Mono.from(connectionFactory.create()),
                        conn -> Flux.from(conn.createStatement(
                                                "SELECT version, checksum, min_compatible_version FROM schema_version "
                                                        + "WHERE component = $1")
                                        .bind(0, COMPONENT)
                                        .execute())
                                .flatMap(result -> result.map((row, meta) -> new Row(
                                        row.get("version", Integer.class),
                                        row.get("checksum", String.class),
                                        row.get("min_compatible_version", Integer.class))))
                                .next(),
                        Connection::close)
                .block(TIMEOUT);
    }

    private static boolean tableExists(final String table) {
        return Boolean.parseBoolean(scalar("SELECT EXISTS (SELECT 1 FROM information_schema.tables "
                + "WHERE table_schema = current_schema() AND table_name = '" + table + "')"));
    }

    private static boolean indexExists(final String index) {
        return Boolean.parseBoolean(scalar("SELECT EXISTS (SELECT 1 FROM pg_indexes "
                + "WHERE schemaname = current_schema() AND indexname = '" + index + "')"));
    }

    private static boolean columnExists(final String table, final String column) {
        return Boolean.parseBoolean(scalar("SELECT EXISTS (SELECT 1 FROM information_schema.columns "
                + "WHERE table_schema = current_schema() AND table_name = '" + table + "' AND column_name = '"
                + column + "')"));
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
