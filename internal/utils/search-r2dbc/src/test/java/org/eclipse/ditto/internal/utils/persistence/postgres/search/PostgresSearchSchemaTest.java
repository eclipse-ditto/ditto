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

import java.util.List;

import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.PostgresSchemaManager;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.TableContract;
import org.eclipse.ditto.internal.utils.persistence.postgres.search.pathindex.SystemSearchIndex;
import org.junit.Test;

/**
 * Unit tests for the {@link PostgresSearchSchema} descriptor: the DDL statement set, table contracts and checksum
 * machinery, verified WITHOUT a live PostgreSQL (the bootstrap-against-a-real-DB behavior is covered by
 * {@code PostgresSearchSchemaManagerIT}).
 */
public final class PostgresSearchSchemaTest {

    @Test
    public void descriptorIdentity() {
        assertThat(PostgresSearchSchema.descriptor().component()).isEqualTo("ditto-postgres-search");
        assertThat(PostgresSearchSchema.descriptor().version()).isEqualTo(3);
        // v2 only adds reloptions, v3 only an index built outside the DDL: v1 code runs against both unchanged
        assertThat(PostgresSearchSchema.descriptor().minCompatibleVersion()).isEqualTo(1);
        assertThat(PostgresSearchSchema.COMPONENT).isEqualTo("ditto-postgres-search");
    }

    @Test
    public void firstStatementCreatesTheTrgmExtension() {
        assertThat(PostgresSearchSchema.ddlStatements().get(0))
                .isEqualTo("CREATE EXTENSION IF NOT EXISTS pg_trgm");
    }

    @Test
    public void everyCreateStatementIsIdempotent() {
        for (final String ddl : PostgresSearchSchema.ddlStatements()) {
            if (ddl.regionMatches(true, 0, "CREATE TABLE", 0, "CREATE TABLE".length())) {
                assertThat(ddl).containsIgnoringCase("CREATE TABLE IF NOT EXISTS");
            } else if (ddl.regionMatches(true, 0, "CREATE INDEX", 0, "CREATE INDEX".length())) {
                assertThat(ddl).containsIgnoringCase("CREATE INDEX IF NOT EXISTS");
            }
        }
    }

    @Test
    public void ddlDeclaresTheThreeTablesAndTheirIndexes() {
        final List<String> ddl = PostgresSearchSchema.ddlStatements();
        assertThat(ddl).anyMatch(s -> s.contains("CREATE TABLE IF NOT EXISTS search_things"));
        assertThat(ddl).anyMatch(s -> s.contains("CREATE TABLE IF NOT EXISTS search_flat"));
        assertThat(ddl).anyMatch(s -> s.contains("CREATE TABLE IF NOT EXISTS search_sync"));
        assertThat(ddl).anyMatch(s -> s.contains("st_namespace"));
        assertThat(ddl).anyMatch(s -> s.contains("st_global_read") && s.contains("USING gin"));
        assertThat(ddl).anyMatch(s -> s.contains("st_referenced_pols") && s.contains("jsonb_path_ops"));
        assertThat(ddl).anyMatch(s -> s.contains("sf_num") && s.contains("WHERE val_num IS NOT NULL"));
        assertThat(ddl).anyMatch(s -> s.contains("sf_exists"));
    }

    @Test
    public void trgmIndexUsesTheCUtf8CollationOverrideAndTrgmOps() {
        assertThat(PostgresSearchSchema.ddlStatements())
                .anyMatch(s -> s.contains("sf_trgm")
                        && s.contains("(val_text COLLATE \"C.utf8\")")
                        && s.contains("gin_trgm_ops")
                        && s.contains("WHERE val_text IS NOT NULL"));
    }

    @Test
    public void searchThingsAndSearchFlatLowerTheAutovacuumScaleFactorAfterTheirCreation() {
        final List<String> ddl = PostgresSearchSchema.ddlStatements();
        final String things = "ALTER TABLE search_things SET (autovacuum_vacuum_scale_factor = 0.01)";
        final String flat = "ALTER TABLE search_flat SET (autovacuum_vacuum_scale_factor = 0.01)";

        assertThat(ddl).containsOnlyOnce(things, flat);
        assertThat(ddl.indexOf(things)).isGreaterThan(indexOfStatementStartingWith(ddl,
                "CREATE TABLE IF NOT EXISTS search_things"));
        assertThat(ddl.indexOf(flat)).isGreaterThan(indexOfStatementStartingWith(ddl,
                "CREATE TABLE IF NOT EXISTS search_flat"));
    }

    @Test
    public void onlySearchThingsAndSearchFlatCarryReloptions() {
        // search_sync (one bookmark row) keeps the server's autovacuum settings; so do cost limit and delay.
        assertThat(PostgresSearchSchema.ddlStatements())
                .filteredOn(s -> s.startsWith("ALTER TABLE"))
                .hasSize(2)
                .noneMatch(s -> s.contains("search_sync"))
                .allMatch(s -> s.endsWith(" SET (autovacuum_vacuum_scale_factor = 0.01)"));
    }

    @Test
    public void scopedPerPathTrigramIndexIsNotPartOfTheChecksummedDdl() {
        // Operator-configured per-path indexes are built by PathIndexMaintenance with CREATE INDEX CONCURRENTLY after
        // the bootstrap, outside the checksummed DDL (a path list change must never alter the schema checksum).
        assertThat(PostgresSchemaManager.effectiveDdl(PostgresSearchSchema.descriptor()))
                .noneMatch(s -> s.contains("gin_trgm_ops) WHERE wpath ="))
                .noneMatch(s -> s.contains("WHERE wpath"))
                .noneMatch(s -> s.contains("CONCURRENTLY"))
                .noneMatch(s -> s.toLowerCase().contains("sf_trgm_scoped"))
                .noneMatch(s -> s.contains("sf_path_"));
    }

    @Test
    public void theSystemIndexIsNotPartOfTheChecksummedDdl() {
        // sf_multi (search schema v3) is built by PathIndexMaintenance with CREATE INDEX CONCURRENTLY after the
        // bootstrap: a plain build in the bootstrap transaction would block writes for the whole build.
        assertThat(PostgresSearchSchema.ddlStatements())
                .noneMatch(statement -> statement.contains(SystemSearchIndex.MULTI_VALUED.indexName()))
                .noneMatch(statement -> statement.contains("ord = 1"));
    }

    @Test
    public void ddlDoesNotOwnTheSharedSchemaVersionTable() {
        // schema_version is owned/prepended by PostgresSchemaManager, never by the descriptor.
        assertThat(PostgresSearchSchema.ddlStatements())
                .noneMatch(s -> s.contains("schema_version"));
    }

    @Test
    public void effectiveDdlPrependsTheSharedSchemaVersionTable() {
        final List<String> effective = PostgresSchemaManager.effectiveDdl(PostgresSearchSchema.descriptor());
        assertThat(effective.get(0)).isEqualTo(PostgresSchemaManager.SCHEMA_VERSION_DDL);
        assertThat(effective).hasSize(PostgresSearchSchema.ddlStatements().size() + 1);
    }

    @Test
    public void tableContractsCoverTheThreeTablesWithTheirPrimaryKeys() {
        final List<TableContract> contracts = PostgresSearchSchema.tableContracts();
        assertThat(contracts).extracting(TableContract::tableName)
                .containsExactly("search_things", "search_flat", "search_sync");

        final TableContract flat = contracts.get(1);
        assertThat(flat.tableName()).isEqualTo("search_flat");
        assertThat(flat.primaryKeyDef()).isEqualTo("PRIMARY KEY (thing_id, path, wpath, ord)");
        assertThat(flat.collatedColumns()).containsExactlyInAnyOrder("thing_id", "path", "wpath", "val_text");

        final TableContract things = contracts.get(0);
        assertThat(things.primaryKeyDef()).isEqualTo("PRIMARY KEY (thing_id)");
        assertThat(things.collatedColumns()).containsExactlyInAnyOrder("thing_id", "namespace");
    }

    @Test
    public void checksumIsDeterministic() {
        assertThat(PostgresSchemaManager.checksum(PostgresSearchSchema.descriptor()))
                .isEqualTo(PostgresSchemaManager.checksum(PostgresSearchSchema.descriptor()))
                .hasSize(64);
    }

    @Test
    public void everyTableAndIndexOfTheDdlIsNamedForTheSameVersionFastPath() {
        // A same-version boot skips the DDL only when all of these exist; an unparsable CREATE statement would make
        // the Optional empty and every boot run the DDL (and wait for a running per-path index build).
        assertThat(PostgresSchemaManager.namedRelations(PostgresSearchSchema.descriptor())).hasValue(List.of(
                "search_things", "st_namespace", "st_global_read", "st_policy", "st_referenced_pols",
                "st_delete_at", "st_modified",
                "search_flat", "sf_num", "sf_text", "sf_bool", "sf_exists", "sf_trgm",
                "search_sync"));
    }

    private static int indexOfStatementStartingWith(final List<String> ddl, final String prefix) {
        for (int i = 0; i < ddl.size(); i++) {
            if (ddl.get(i).startsWith(prefix)) {
                return i;
            }
        }
        throw new AssertionError("no statement starts with " + prefix);
    }
}
