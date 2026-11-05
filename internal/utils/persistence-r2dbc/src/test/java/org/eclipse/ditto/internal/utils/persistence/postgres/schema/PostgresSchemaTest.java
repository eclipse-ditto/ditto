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

import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.PostgresSchemaManager;
import org.junit.Test;

/**
 * Unit tests pinning the canonical DDL shapes to their structural invariants: every statement is idempotent
 * ({@code IF NOT EXISTS}), the journal sequence column uses an IDENTITY generator, every persistence id column (on the
 * journal, the high-water-mark table, and the snapshot table) uses {@code COLLATE "C"} for byte-ordered comparisons,
 * the tags column is backed by a GIN index, the snapshot table
 * carries its full primary key and no secondary index, the per-entity high-water-mark table exists, and autovacuum is
 * tuned per table.
 */
public final class PostgresSchemaTest {

    // The effective DDL = the manager-owned shared schema_version table + this component's own statements, i.e. exactly
    // what the bootstrap executes (schema_version is no longer folded into PostgresSchema.ddlStatements() itself).
    private static final List<String> DDL = PostgresSchemaManager.effectiveDdl(PostgresSchema.descriptor());

    @Test
    public void descriptorDeclaresVersionAndMinCompatibleVersion() {
        assertThat(PostgresSchema.descriptor().version()).isEqualTo(4);
        assertThat(PostgresSchema.descriptor().minCompatibleVersion()).isEqualTo(2);
    }

    @Test
    public void everyStatementIsIdempotent() {
        // Every CREATE carries IF NOT EXISTS; ALTERs are inherently idempotent (SET).
        assertThat(DDL).allSatisfy(stmt -> {
            if (stmt.startsWith("CREATE")) {
                assertThat(stmt).contains("IF NOT EXISTS");
            }
        });
    }

    @Test
    public void coversAllFiveTableSets() {
        assertThat(PostgresSchema.ENTITIES)
                .containsExactly("things", "policies", "connections", "wot", "connection_remember");
        for (final String e : PostgresSchema.ENTITIES) {
            assertThat(DDL).anyMatch(s -> s.contains(e + "_journal ("));
            assertThat(DDL).anyMatch(s -> s.contains(e + "_journal_seq ("));
            assertThat(DDL).anyMatch(s -> s.contains(e + "_snaps ("));
        }
    }

    @Test
    public void journalHasIdentityOffsetCollateCAndGin() {
        final String journal = stmtContaining("things_journal (");
        assertThat(journal).contains("seq BIGINT GENERATED ALWAYS AS IDENTITY");   // database-generated sequence
        assertThat(journal).contains("pid TEXT NOT NULL COLLATE \"C\"");           // byte-ordered comparisons
        assertThat(journal).contains("tags TEXT[] NOT NULL DEFAULT '{}'");
        assertThat(journal).contains("event JSONB NOT NULL");
        assertThat(journal).contains("PRIMARY KEY (pid, sn)");
        assertThat(stmtContaining("things_journal_tags_idx")).contains("USING GIN (tags)");  // tag membership lookups
    }

    @Test
    public void highWaterMarkTableHasHighestSnAndDeletedTo() {
        final String seq = stmtContaining("things_journal_seq (");
        assertThat(seq).contains("pid TEXT COLLATE \"C\" PRIMARY KEY");   // byte-ordered primary key
        assertThat(seq).contains("highest_sn BIGINT NOT NULL");
        assertThat(seq).contains("deleted_to BIGINT NOT NULL DEFAULT 0");
    }

    @Test
    public void everyPidColumnUsesCollateC() {
        // pid is COLLATE "C" on the journal, the high-water-mark table, AND the snaps table so that pid > $1
        // cursor pagination and primary-key ordering are byte-ordered and locale-independent across all tables.
        for (final String e : PostgresSchema.ENTITIES) {
            assertThat(stmtContaining(e + "_journal (")).contains("pid TEXT NOT NULL COLLATE \"C\"");
            assertThat(stmtContaining(e + "_journal_seq (")).contains("pid TEXT COLLATE \"C\" PRIMARY KEY");
            assertThat(stmtContaining(e + "_snaps (")).contains("pid TEXT NOT NULL COLLATE \"C\"");
        }
        // No DDL statement may declare a pid TEXT column without immediately attaching COLLATE "C".
        assertThat(DDL)
                .filteredOn(s -> s.contains("pid TEXT"))
                .allSatisfy(s -> assertThat(s).containsPattern("pid TEXT( NOT NULL)? COLLATE \"C\""));
    }

    @Test
    public void snapshotTableHasThreeColumnPkAndNoSecondaryIndex() {
        final String snaps = stmtContaining("things_snaps (");
        assertThat(snaps).contains("snapshot JSONB NOT NULL");
        assertThat(snaps).contains("PRIMARY KEY (pid, sn, written_at)");
        // v2 dropped the (pid, sn DESC) index, v3 the partial lifecycle = 'DELETED' index: no query filters on
        // lifecycle (DELETED snapshots are filtered after the newest-per-pid pick), so the PK serves every snaps access
        // path. Older databases converge via DROP INDEX IF EXISTS.
        assertThat(DDL).noneMatch(s -> s.startsWith("CREATE INDEX") && s.contains("_snaps"));
        for (final String e : PostgresSchema.ENTITIES) {
            assertThat(DDL).contains("DROP INDEX IF EXISTS " + e + "_snaps_pid_sn_desc_idx",
                    "DROP INDEX IF EXISTS " + e + "_snaps_lifecycle_idx");
        }
    }

    @Test
    public void autovacuumTunedPerTable() {
        for (final String e : PostgresSchema.ENTITIES) {
            assertThat(DDL).contains(
                    "ALTER TABLE " + e + "_journal SET (autovacuum_vacuum_scale_factor = 0.01, "
                            + "autovacuum_vacuum_cost_limit = 2000)",
                    "ALTER TABLE " + e + "_journal_seq SET (autovacuum_vacuum_scale_factor = 0.01)",
                    "ALTER TABLE " + e + "_snaps SET (autovacuum_vacuum_scale_factor = 0.01, "
                            + "autovacuum_vacuum_cost_limit = 2000)");
        }
    }

    @Test
    public void everyReloptionIsKnownToPostgresql10() {
        // The persistence backend still boots on PostgreSQL 10+; an unknown storage parameter (e.g.
        // autovacuum_vacuum_insert_scale_factor, PostgreSQL 13+) would fail the upgrade boot there.
        final Set<String> pg10 = Set.of("autovacuum_vacuum_scale_factor", "autovacuum_vacuum_threshold",
                "autovacuum_vacuum_cost_limit", "autovacuum_vacuum_cost_delay", "autovacuum_analyze_scale_factor",
                "autovacuum_analyze_threshold", "fillfactor");
        final Pattern option = Pattern.compile("(\\w+) = ");
        assertThat(DDL).filteredOn(s -> s.startsWith("ALTER TABLE") && s.contains(" SET ("))
                .isNotEmpty()
                .allSatisfy(s -> {
                    final Matcher m = option.matcher(s.substring(s.indexOf(" SET (")));
                    while (m.find()) {
                        assertThat(pg10).as(s).contains(m.group(1));
                    }
                });
    }

    @Test
    public void everyDdlEntryIsOnePlainStatement() {
        // the same-version fast path reads only the start of each entry (SchemaDdlRelations)
        assertThat(DDL).noneMatch(s -> s.contains(";") || s.contains("--") || s.contains("/*"));
    }

    @Test
    public void schemaVersionTablePresent() {
        assertThat(stmtContaining("schema_version (")).contains("checksum TEXT NOT NULL");
    }

    @Test
    public void doesNotUseSessionAdvisoryLockOrIndisvalidRebuild() {
        assertThat(DDL).noneMatch(s -> s.contains("CONCURRENTLY"));
        assertThat(DDL).noneMatch(s -> s.contains("pg_advisory_lock("));   // the bootstrap uses pg_advisory_XACT_lock
    }

    @Test
    public void everyTableAndIndexOfTheDdlIsNamedForTheSameVersionFastPath() {
        // E_journal_seq has no named index: the fast path must name tables too, or a dropped one would never be
        // re-created by a same-version boot or a self-heal.
        final List<String> expected = PostgresSchema.ENTITIES.stream()
                .flatMap(entity -> Stream.of(entity + "_journal", entity + "_journal_tags_idx",
                        entity + "_journal_seq", entity + "_snaps"))
                .toList();

        assertThat(PostgresSchemaManager.namedRelations(PostgresSchema.descriptor())).hasValue(expected);
    }

    private static String stmtContaining(final String needle) {
        return DDL.stream().filter(s -> s.contains(needle)).findFirst()
                .orElseThrow(() -> new AssertionError("No DDL statement contains: " + needle));
    }

}
