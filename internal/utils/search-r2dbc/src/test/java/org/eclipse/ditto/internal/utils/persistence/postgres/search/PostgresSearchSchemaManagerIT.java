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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.PostgresSchemaManager;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.SchemaBootException;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.testkit.PostgresDbResource;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.pathindex.SystemSearchIndex;
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
 * Integration test for the search-schema bootstrap (the descriptor-driven {@link PostgresSchemaManager} applied to
 * {@link PostgresSearchSchema}) against a real PostgreSQL (PG 16) via Testcontainers.
 * <p>
 * Runs under the failsafe phase ({@code *IT}); skipped offline / when no Docker daemon is reachable (the
 * {@link #startContainer() Assume} guard turns an unreachable Docker into a skip, not a failure).
 * </p>
 */
public final class PostgresSearchSchemaManagerIT {

    /** {@code pg_class.reloptions} of search_things and search_flat from schema v2 on. */
    private static final String V2_RELOPTIONS = "autovacuum_vacuum_scale_factor=0.01";
    private static final String NO_RELOPTIONS = "<none>";

    private static final PostgresDbResource POSTGRES = new PostgresDbResource();
    private static ConnectionFactory connectionFactory;

    @BeforeClass
    public static void startContainer() {
        try {
            POSTGRES.start();
        } catch (final Throwable t) {
            Assume.assumeNoException("Docker/Testcontainers unavailable — skipping PostgresSearchSchemaManagerIT", t);
        }
        connectionFactory = POSTGRES.newConnectionFactory();
    }

    @AfterClass
    public static void stopContainer() {
        POSTGRES.stop();
    }

    @Test
    public void bootstrapCreatesAllTablesAndIndexesAndIsIdempotent() {
        reset();
        PostgresSchemaManager.of(connectionFactory, PostgresSearchSchema.descriptor()).bootstrap();

        assertThat(tableExists("search_things")).isTrue();
        assertThat(tableExists("search_flat")).isTrue();
        assertThat(tableExists("search_sync")).isTrue();
        assertThat(tableExists("schema_version")).isTrue();

        // all declared indexes are present
        for (final String index : new String[] {
                "st_namespace", "st_global_read", "st_policy", "st_referenced_pols", "st_delete_at", "st_modified",
                "sf_num", "sf_text", "sf_bool", "sf_exists", "sf_trgm"}) {
            assertThat(indexExists(index)).as("index %s exists", index).isTrue();
        }

        // re-run is idempotent (IF NOT EXISTS + checksum match)
        PostgresSchemaManager.of(connectionFactory, PostgresSearchSchema.descriptor()).bootstrap();
        assertThat(scalar("SELECT checksum FROM schema_version WHERE component = '"
                + PostgresSearchSchema.COMPONENT + "'"))
                .isEqualTo(PostgresSchemaManager.checksum(PostgresSearchSchema.descriptor()));
    }

    @Test
    public void trgmIndexCarriesTheCUtf8CollationOverride() {
        reset();
        PostgresSchemaManager.of(connectionFactory, PostgresSearchSchema.descriptor()).bootstrap();

        final String indexDef = scalar("SELECT indexdef FROM pg_indexes WHERE indexname = 'sf_trgm'");
        assertThat(indexDef)
                .as("sf_trgm must build the trigram GIN on (val_text COLLATE \"C.utf8\")")
                .contains("gin_trgm_ops")
                .contains("C.utf8");
    }

    @Test
    public void divergentPreExistingPrimaryKeyRefusesToBoot() {
        reset();
        // Pre-create search_sync with a divergent PK; CREATE TABLE IF NOT EXISTS keeps it, so the live-catalog check
        // must detect the mismatch and refuse to boot.
        runDdl("CREATE TABLE search_sync (id TEXT, ts TIMESTAMPTZ, tag TEXT, PRIMARY KEY (id, ts))");

        assertThatThrownBy(() ->
                PostgresSchemaManager.of(connectionFactory, PostgresSearchSchema.descriptor()).bootstrap())
                .isInstanceOf(SchemaBootException.class)
                .hasMessageContaining("divergent primary key");
    }

    @Test
    public void checksumMismatchAtSameVersionRefusesToBoot() {
        reset();
        PostgresSchemaManager.of(connectionFactory, PostgresSearchSchema.descriptor()).bootstrap();

        // Tamper the stored checksum for this component at the SAME version -> drift -> refuse to boot.
        runDdl("UPDATE schema_version SET checksum = 'deadbeef' WHERE component = '"
                + PostgresSearchSchema.COMPONENT + "'");

        assertThatThrownBy(() ->
                PostgresSchemaManager.of(connectionFactory, PostgresSearchSchema.descriptor()).bootstrap())
                .isInstanceOf(SchemaBootException.class)
                .hasMessageContaining("checksum mismatch");
    }

    @Test
    public void freshBootstrapSetsTheAutovacuumReloptionsOnSearchThingsAndSearchFlat() {
        reset();

        PostgresSchemaManager.of(connectionFactory, PostgresSearchSchema.descriptor()).bootstrap();

        assertThat(reloptions("search_things")).isEqualTo(V2_RELOPTIONS);
        assertThat(reloptions("search_flat")).isEqualTo(V2_RELOPTIONS);
        assertThat(reloptions("search_sync")).as("search_sync keeps the server defaults").isEqualTo(NO_RELOPTIONS);
        assertThat(storedRow()).isEqualTo("3/" + PostgresSchemaManager.checksum(PostgresSearchSchema.descriptor())
                + "/1");
    }

    @Test
    public void upgradeFromV1AppliesTheReloptionsInPlaceAndAdvancesTheRowTo2WithMinCompatibleVersion1() {
        reset();
        PostgresSchemaManager.of(connectionFactory, PostgresSearchSchemaV1.descriptor()).bootstrap();
        assertThat(storedRow()).isEqualTo("1/" + PostgresSearchSchemaV1.CHECKSUM + "/1");
        assertThat(reloptions("search_things")).isEqualTo(NO_RELOPTIONS);
        assertThat(reloptions("search_flat")).isEqualTo(NO_RELOPTIONS);
        final String thingsFile = relfilenode("search_things");
        final String flatFile = relfilenode("search_flat");

        // the first v2 pod of a rolling upgrade
        PostgresSchemaManager.of(connectionFactory, PostgresSearchSchemaV2.descriptor()).bootstrap();

        assertThat(reloptions("search_things")).isEqualTo(V2_RELOPTIONS);
        assertThat(reloptions("search_flat")).isEqualTo(V2_RELOPTIONS);
        assertThat(reloptions("search_sync")).isEqualTo(NO_RELOPTIONS);
        assertThat(relfilenode("search_things")).as("setting reloptions does not rewrite the table")
                .isEqualTo(thingsFile);
        assertThat(relfilenode("search_flat")).as("setting reloptions does not rewrite the table")
                .isEqualTo(flatFile);
        assertThat(storedRow()).isEqualTo("2/" + PostgresSearchSchemaV2.CHECKSUM + "/1");
    }

    @Test
    public void upgradeFromV2AdvancesTheRowTo3WithTheSameChecksumAndBuildsNoIndexInTheBootstrap() {
        reset();
        PostgresSchemaManager.of(connectionFactory, PostgresSearchSchemaV2.descriptor()).bootstrap();
        assertThat(storedRow()).isEqualTo("2/" + PostgresSearchSchemaV2.CHECKSUM + "/1");
        final String flatFile = relfilenode("search_flat");

        // the first v3 pod of a rolling upgrade
        PostgresSchemaManager.of(connectionFactory, PostgresSearchSchema.descriptor()).bootstrap();

        assertThat(storedRow()).isEqualTo("3/" + PostgresSearchSchemaV2.CHECKSUM + "/1");
        assertThat(indexExists(SystemSearchIndex.MULTI_VALUED.indexName()))
                .as("sf_multi is built CONCURRENTLY by the maintenance, never by the bootstrap transaction")
                .isFalse();
        assertThat(relfilenode("search_flat")).isEqualTo(flatFile);
        assertThat(reloptions("search_flat")).isEqualTo(V2_RELOPTIONS);
    }

    @Test
    public void v1BootsAgainstAStoredV2SchemaWithoutRunningDdlOrTouchingTheRow() {
        reset();
        PostgresSchemaManager.of(connectionFactory, PostgresSearchSchemaV1.descriptor()).bootstrap();
        PostgresSchemaManager.of(connectionFactory, PostgresSearchSchemaV2.descriptor()).bootstrap();
        final String v2Row = storedRow();
        // Probe for "no DDL ran": the v1 DDL re-creates this index (CREATE INDEX IF NOT EXISTS) whenever it runs.
        runDdl("DROP INDEX sf_exists");
        final ListAppender<ILoggingEvent> managerLog = captureManagerLog();
        try {
            // a not-yet-upgraded v1 pod restarts mid-roll
            PostgresSchemaManager.of(connectionFactory, PostgresSearchSchemaV1.descriptor()).bootstrap();

            assertThat(indexExists("sf_exists")).as("v1 DDL must not run against the v2 schema").isFalse();
            assertThat(storedRow()).as("the row is not written back to v1").isEqualTo(v2Row)
                    .isEqualTo("2/" + PostgresSearchSchemaV2.CHECKSUM + "/1");
            assertThat(reloptions("search_things")).isEqualTo(V2_RELOPTIONS);
            assertThat(reloptions("search_flat")).isEqualTo(V2_RELOPTIONS);
            assertThat(managerLog.list.stream()
                    .filter(event -> event.getLevel() == Level.INFO)
                    .map(ILoggingEvent::getFormattedMessage))
                    .anySatisfy(message -> assertThat(message)
                            .contains("stored schema version 2")
                            .contains("this code's version 1")
                            .contains("Running no DDL"));
        } finally {
            releaseManagerLog(managerLog);
        }
    }

    @Test
    public void v2BootsAgainstAStoredV3SchemaWithoutRunningDdlOrTouchingTheRow() {
        reset();
        PostgresSchemaManager.of(connectionFactory, PostgresSearchSchema.descriptor()).bootstrap();
        final String v3Row = storedRow();
        // Probe for "no DDL ran": the v2 DDL re-creates this index (CREATE INDEX IF NOT EXISTS) whenever it runs.
        runDdl("DROP INDEX sf_exists");
        final ListAppender<ILoggingEvent> managerLog = captureManagerLog();
        try {
            // a not-yet-upgraded v2 pod restarts mid-roll
            PostgresSchemaManager.of(connectionFactory, PostgresSearchSchemaV2.descriptor()).bootstrap();

            assertThat(indexExists("sf_exists")).as("v2 DDL must not run against the v3 schema").isFalse();
            assertThat(storedRow()).isEqualTo(v3Row).isEqualTo("3/" + PostgresSearchSchemaV2.CHECKSUM + "/1");
            assertThat(managerLog.list.stream().filter(event -> event.getLevel() == Level.INFO)
                    .map(ILoggingEvent::getFormattedMessage))
                    .anySatisfy(message -> assertThat(message).contains("stored schema version 3")
                            .contains("this code's version 2").contains("Running no DDL"));
        } finally {
            releaseManagerLog(managerLog);
        }
    }

    @Test
    public void selfHealRecreatesAMissingSearchFlatWithItsReloptions() {
        reset();
        PostgresSchemaManager.of(connectionFactory, PostgresSearchSchema.descriptor()).bootstrap();
        runDdl("DROP TABLE search_flat");

        PostgresSchemaManager.of(connectionFactory, PostgresSearchSchema.descriptor()).ensureTablesReactive()
                .block(Duration.ofSeconds(60));

        assertThat(tableExists("search_flat")).isTrue();
        assertThat(reloptions("search_flat")).isEqualTo(V2_RELOPTIONS);
    }

    private static void reset() {
        runDdl("DROP TABLE IF EXISTS search_flat, search_things, search_sync, schema_version CASCADE");
    }

    private static String storedRow() {
        return scalar("SELECT version || '/' || checksum || '/' || min_compatible_version FROM schema_version "
                + "WHERE component = '" + PostgresSearchSchema.COMPONENT + "'");
    }

    private static String reloptions(final String table) {
        return scalar("SELECT coalesce(array_to_string(reloptions, ','), '" + NO_RELOPTIONS + "') FROM pg_class "
                + "WHERE oid = to_regclass('" + table + "')");
    }

    private static String relfilenode(final String table) {
        return scalar("SELECT relfilenode FROM pg_class WHERE oid = to_regclass('" + table + "')");
    }

    private static ListAppender<ILoggingEvent> captureManagerLog() {
        // logback-test.xml roots at WARN; the manager's compatible-newer line is INFO.
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

    private static boolean tableExists(final String table) {
        return Boolean.parseBoolean(scalar(
                "SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = '" + table + "')"));
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
