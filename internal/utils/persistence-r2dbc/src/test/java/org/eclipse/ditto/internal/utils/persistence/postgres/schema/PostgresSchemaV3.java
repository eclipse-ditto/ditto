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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.PostgresSchemaDescriptor;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.TableContract;

/**
 * The persistence schema as version 3 shipped it, i.e. what a not-yet-upgraded pod runs during a rolling upgrade to
 * v4.
 * <p>
 * The DDL statements and table contracts are written out literally, independent of {@link PostgresSchema}, so a later
 * change there cannot flow into "v3". {@code SchemaChecksumPreservationTest} pins this descriptor's checksum to
 * {@link #CHECKSUM}, the value every v3 database stores.
 * </p>
 * <p>
 * <strong>Rule for schema bumps:</strong> every bump of {@link PostgresSchema#VERSION} adds a literal fixture like this
 * one for the version it replaces (next: {@code PostgresSchemaV4} at the bump to v5), pins its checksum in
 * {@code SchemaChecksumPreservationTest}, and uses it in the upgrade and mixed-version tests of
 * {@code PostgresSchemaManagerIT}.
 * </p>
 */
final class PostgresSchemaV3 {

    /** The {@code schema_version.checksum} every v3 persistence database stores. */
    static final String CHECKSUM = "1c74caf668bfd35ca03813be60fb40f7a12f53aebe89d850247ca1cf260fffea";

    /** The four table sets v3 knows; v4 appends {@code connection_remember}. */
    static final List<String> ENTITIES = List.of("things", "policies", "connections", "wot");

    /** The v3 DDL, verbatim. */
    static final List<String> DDL_STATEMENTS = ENTITIES.stream()
            .flatMap(PostgresSchemaV3::ddlFor)
            .toList();

    private static final Map<String, String> JOURNAL_COLUMNS = Map.of("pid", "text", "sn", "bigint",
            "seq", "bigint", "manifest", "text", "tags", "ARRAY", "event", "jsonb",
            "written_at", "timestamp with time zone");
    private static final Map<String, String> JOURNAL_SEQ_COLUMNS = Map.of("pid", "text", "highest_sn", "bigint",
            "deleted_to", "bigint");
    private static final Map<String, String> SNAPS_COLUMNS = Map.of("pid", "text", "sn", "bigint",
            "snapshot", "jsonb", "lifecycle", "text", "written_at", "timestamp with time zone");

    /** The v3 table contracts, verbatim. */
    static final List<TableContract> TABLE_CONTRACTS = ENTITIES.stream()
            .flatMap(PostgresSchemaV3::contractsFor)
            .toList();

    private static final PostgresSchemaDescriptor DESCRIPTOR = new PostgresSchemaDescriptor() {
        @Override
        public String component() {
            return PostgresSchema.COMPONENT;
        }

        @Override
        public int version() {
            return 3;
        }

        @Override
        public int minCompatibleVersion() {
            return 2;
        }

        @Override
        public List<String> ddlStatements() {
            return DDL_STATEMENTS;
        }

        @Override
        public List<TableContract> tableContracts() {
            return TABLE_CONTRACTS;
        }
    };

    private PostgresSchemaV3() {
        throw new AssertionError("no instances");
    }

    static PostgresSchemaDescriptor descriptor() {
        return DESCRIPTOR;
    }

    /**
     * The nine v3 statements of one table set. v4 emits exactly these for its fifth set as well, so the upgrade test
     * reuses them.
     *
     * @param e the table prefix.
     * @return the statements, in DDL order.
     */
    static Stream<String> ddlFor(final String e) {
        return Stream.of(
                "CREATE TABLE IF NOT EXISTS " + e + "_journal (pid TEXT NOT NULL COLLATE \"C\", "
                        + "sn BIGINT NOT NULL, seq BIGINT GENERATED ALWAYS AS IDENTITY, manifest TEXT NOT NULL, "
                        + "tags TEXT[] NOT NULL DEFAULT '{}', event JSONB NOT NULL, "
                        + "written_at TIMESTAMPTZ NOT NULL DEFAULT now(), PRIMARY KEY (pid, sn))",
                "CREATE INDEX IF NOT EXISTS " + e + "_journal_tags_idx ON " + e + "_journal USING GIN (tags)",
                "CREATE TABLE IF NOT EXISTS " + e + "_journal_seq (pid TEXT COLLATE \"C\" PRIMARY KEY, "
                        + "highest_sn BIGINT NOT NULL, deleted_to BIGINT NOT NULL DEFAULT 0)",
                "CREATE TABLE IF NOT EXISTS " + e + "_snaps (pid TEXT NOT NULL COLLATE \"C\", "
                        + "sn BIGINT NOT NULL, snapshot JSONB NOT NULL, lifecycle TEXT, "
                        + "written_at TIMESTAMPTZ NOT NULL, PRIMARY KEY (pid, sn, written_at))",
                "DROP INDEX IF EXISTS " + e + "_snaps_pid_sn_desc_idx",
                "DROP INDEX IF EXISTS " + e + "_snaps_lifecycle_idx",
                "ALTER TABLE " + e + "_journal SET (autovacuum_vacuum_scale_factor = 0.01, "
                        + "autovacuum_vacuum_cost_limit = 2000)",
                "ALTER TABLE " + e + "_journal_seq SET (autovacuum_vacuum_scale_factor = 0.01)",
                "ALTER TABLE " + e + "_snaps SET (autovacuum_vacuum_scale_factor = 0.01, "
                        + "autovacuum_vacuum_cost_limit = 2000)");
    }

    /**
     * The three v3 table contracts of one table set (unchanged since v2; v4 adds a fifth set of the same shape).
     *
     * @param e the table prefix.
     * @return the contracts, in DDL order.
     */
    static Stream<TableContract> contractsFor(final String e) {
        return Stream.of(
                new TableContract(e + "_journal", "PRIMARY KEY (pid, sn)", JOURNAL_COLUMNS, Set.of("pid")),
                new TableContract(e + "_journal_seq", "PRIMARY KEY (pid)", JOURNAL_SEQ_COLUMNS, Set.of("pid")),
                new TableContract(e + "_snaps", "PRIMARY KEY (pid, sn, written_at)", SNAPS_COLUMNS, Set.of("pid")));
    }
}
