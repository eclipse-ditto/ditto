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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.SchemaBootException;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.SchemaVerifier;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.TableContract;
import org.junit.Test;

/**
 * Unit tests for {@link SchemaVerifier}'s live-catalog refuse-to-boot guard (the version/checksum decision against the
 * recorded schema version is covered by postgres-client's {@code SchemaVerifierVersionDecisionTest}): a comparison of
 * each table's primary key, column types, and the {@code COLLATE "C"} collation of its persistence-id columns
 * (including the NULL-collation drift case that {@code data_type} cannot detect). Pure logic, no live PostgreSQL.
 */
public final class SchemaVerifierTest {

    private static final TableContract JOURNAL = new TableContract(
            "things_journal", "PRIMARY KEY (pid, sn)",
            Map.of("pid", "text", "sn", "bigint", "event", "jsonb"), Set.of("pid"));

    @Test
    public void matchingCatalogPasses() {
        assertThatCode(() -> SchemaVerifier.verifyTable(JOURNAL, "PRIMARY KEY (pid, sn)",
                Map.of("pid", "text", "sn", "bigint", "event", "jsonb")))
                .doesNotThrowAnyException();
    }

    @Test
    public void catalogPkComparisonIgnoresWhitespaceAndCase() {
        assertThatCode(() -> SchemaVerifier.verifyTable(JOURNAL, "primary key  (pid,  sn)",
                Map.of("pid", "text", "sn", "bigint", "event", "jsonb")))
                .doesNotThrowAnyException();
    }

    @Test
    public void divergentPreExistingPkRefusesToBoot() {
        // An old (pid, sn, written_at) PK survives the new (pid, sn) DDL because CREATE TABLE IF NOT EXISTS
        // leaves the pre-existing table untouched; the live-catalog check must catch the divergence.
        assertThatThrownBy(() -> SchemaVerifier.verifyTable(JOURNAL, "PRIMARY KEY (pid, sn, written_at)",
                Map.of("pid", "text", "sn", "bigint", "event", "jsonb")))
                .isInstanceOf(SchemaBootException.class)
                .hasMessageContaining("divergent primary key")
                .hasMessageContaining("things_journal");
    }

    @Test
    public void missingPkRefusesToBoot() {
        assertThatThrownBy(() -> SchemaVerifier.verifyTable(JOURNAL, null,
                Map.of("pid", "text", "sn", "bigint", "event", "jsonb")))
                .isInstanceOf(SchemaBootException.class)
                .hasMessageContaining("divergent primary key");
    }

    @Test
    public void missingColumnRefusesToBoot() {
        assertThatThrownBy(() -> SchemaVerifier.verifyTable(JOURNAL, "PRIMARY KEY (pid, sn)",
                Map.of("pid", "text", "sn", "bigint")))
                .isInstanceOf(SchemaBootException.class)
                .hasMessageContaining("missing expected column")
                .hasMessageContaining("event");
    }

    @Test
    public void divergentColumnTypeRefusesToBoot() {
        assertThatThrownBy(() -> SchemaVerifier.verifyTable(JOURNAL, "PRIMARY KEY (pid, sn)",
                Map.of("pid", "text", "sn", "bigint", "event", "bytea")))
                .isInstanceOf(SchemaBootException.class)
                .hasMessageContaining("data_type")
                .hasMessageContaining("event");
    }

    @Test
    public void matchingCollationPasses() {
        assertThatCode(() -> SchemaVerifier.verifyTable(JOURNAL, "PRIMARY KEY (pid, sn)",
                Map.of("pid", "text", "sn", "bigint", "event", "jsonb"),
                Map.of("pid", "C")))
                .doesNotThrowAnyException();
    }

    @Test
    public void nullCollationOnPidRefusesToBoot() {
        // information_schema.columns reports collation_name = NULL for a column with no explicit collation, i.e. one
        // that uses the database default rather than COLLATE "C"; that is drift and must refuse to boot.
        final Map<String, String> collationsWithNullPid = new HashMap<>();
        collationsWithNullPid.put("pid", null);
        assertThatThrownBy(() -> SchemaVerifier.verifyTable(JOURNAL, "PRIMARY KEY (pid, sn)",
                Map.of("pid", "text", "sn", "bigint", "event", "jsonb"),
                collationsWithNullPid))
                .isInstanceOf(SchemaBootException.class)
                .hasMessageContaining("collation_name")
                .hasMessageContaining("things_journal")
                .hasMessageContaining("pid");
    }

    @Test
    public void absentCollationEntryOnPidRefusesToBoot() {
        // A pre-existing table whose pid column lacks COLLATE "C" shows up as no collation entry at all; data_type
        // still reports "text", so only the collation check can catch this drift.
        assertThatThrownBy(() -> SchemaVerifier.verifyTable(JOURNAL, "PRIMARY KEY (pid, sn)",
                Map.of("pid", "text", "sn", "bigint", "event", "jsonb"),
                Map.of()))
                .isInstanceOf(SchemaBootException.class)
                .hasMessageContaining("collation_name")
                .hasMessageContaining("pid");
    }

    @Test
    public void divergentCollationOnPidRefusesToBoot() {
        assertThatThrownBy(() -> SchemaVerifier.verifyTable(JOURNAL, "PRIMARY KEY (pid, sn)",
                Map.of("pid", "text", "sn", "bigint", "event", "jsonb"),
                Map.of("pid", "en_US")))
                .isInstanceOf(SchemaBootException.class)
                .hasMessageContaining("collation_name")
                .hasMessageContaining("en_US")
                .hasMessageContaining("pid");
    }

    @Test
    public void threeArgVerifyTableSkipsCollationCheck() {
        // The legacy 3-arg overload only checks PK + data types, so a pid column with no collation info still passes;
        // collation drift is caught only by the 4-arg overload that the bootstrap supplies collation_name to.
        assertThatCode(() -> SchemaVerifier.verifyTable(JOURNAL, "PRIMARY KEY (pid, sn)",
                Map.of("pid", "text", "sn", "bigint", "event", "jsonb")))
                .doesNotThrowAnyException();
    }

    @Test
    public void canonicalContractsRequireCollatedPidOnEveryTableThatHasOne() {
        // Every canonical table with a pid — journal, journal_seq, and snaps — must mark pid as a COLLATE "C" column;
        // the one table without a pid (the v5 encryption-migration progress table, keyed by id) collates nothing.
        assertThat(PostgresSchema.tableContracts())
                .filteredOn(c -> c.columns().containsKey("pid"))
                .hasSize(15)
                .allSatisfy(c -> assertThat(c.collatedColumns()).containsExactly("pid"));
        assertThat(PostgresSchema.tableContracts())
                .filteredOn(c -> !c.columns().containsKey("pid"))
                .extracting(TableContract::tableName)
                .containsExactly(PostgresSchema.ENCRYPTION_MIGRATION_TABLE);
        assertThat(PostgresSchema.tableContracts())
                .filteredOn(c -> !c.columns().containsKey("pid"))
                .allSatisfy(c -> assertThat(c.collatedColumns()).isEmpty());
    }

    @Test
    public void verifyAllTablesWithCollationsRefusesOnPidCollationDrift() {
        final var contracts = PostgresSchema.tableContracts();
        final Map<String, String> pks = new HashMap<>();
        final Map<String, Map<String, String>> columns = new HashMap<>();
        final Map<String, Map<String, String>> collations = new HashMap<>();
        for (final TableContract c : contracts) {
            pks.put(c.tableName(), c.primaryKeyDef());
            columns.put(c.tableName(), c.columns());
            final Map<String, String> tableCollations = new HashMap<>();
            tableCollations.put("pid", "C");
            collations.put(c.tableName(), tableCollations);
        }
        // Drop the collation on a single snaps table to simulate a pre-existing untouched table.
        collations.get("things_snaps").put("pid", null);

        assertThatThrownBy(() -> SchemaVerifier.verifyAllTables(contracts, pks, columns, collations))
                .isInstanceOf(SchemaBootException.class)
                .hasMessageContaining("things_snaps")
                .hasMessageContaining("collation_name");
    }

    @Test
    public void verifyAllTablesCoversEveryCanonicalContract() {
        // The canonical contracts must include every entity's three tables.
        assertThat(PostgresSchema.tableContracts())
                .extracting(TableContract::tableName)
                .contains("things_journal", "things_journal_seq", "things_snaps",
                        "policies_journal", "connections_journal", "wot_snaps");
    }

}
