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
 * The persistence schema as version 2 shipped it, i.e. what a not-yet-upgraded pod runs during a rolling upgrade to
 * v3.
 * <p>
 * The DDL statements and table contracts are written out literally, independent of {@link PostgresSchema}, so a later
 * change there cannot flow into "v2". {@code SchemaChecksumPreservationTest} pins this descriptor's checksum to
 * {@link #CHECKSUM}, the value every v2 database stores.
 * </p>
 * <p>
 * <strong>Rule for schema bumps:</strong> every bump of {@link PostgresSchema#VERSION} adds a literal fixture like this
 * one for the version it replaces ({@code PostgresSchemaV3} did so at the bump to v4), pins its checksum in
 * {@code SchemaChecksumPreservationTest}, and uses it in the upgrade and mixed-version tests of
 * {@code PostgresSchemaManagerIT}.
 * </p>
 */
final class PostgresSchemaV2 {

    /** The {@code schema_version.checksum} every v2 persistence database stores. */
    static final String CHECKSUM = "7f72550a37b22382e580fc288c54d26ff81b3184a1eaea11cdd3775dc323a509";

    private static final List<String> ENTITIES = List.of("things", "policies", "connections", "wot");

    /** The v2 DDL, verbatim. */
    static final List<String> DDL_STATEMENTS = ENTITIES.stream()
            .flatMap(e -> Stream.of(
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
                    "CREATE INDEX IF NOT EXISTS " + e + "_snaps_lifecycle_idx ON " + e
                            + "_snaps (lifecycle) WHERE lifecycle = 'DELETED'",
                    "ALTER TABLE " + e + "_journal SET (autovacuum_vacuum_scale_factor = 0.01)",
                    "ALTER TABLE " + e + "_journal_seq SET (autovacuum_vacuum_scale_factor = 0.01)",
                    "ALTER TABLE " + e + "_snaps SET (autovacuum_vacuum_scale_factor = 0.01)"))
            .toList();

    private static final Map<String, String> JOURNAL_COLUMNS = Map.of("pid", "text", "sn", "bigint",
            "seq", "bigint", "manifest", "text", "tags", "ARRAY", "event", "jsonb",
            "written_at", "timestamp with time zone");
    private static final Map<String, String> JOURNAL_SEQ_COLUMNS = Map.of("pid", "text", "highest_sn", "bigint",
            "deleted_to", "bigint");
    private static final Map<String, String> SNAPS_COLUMNS = Map.of("pid", "text", "sn", "bigint",
            "snapshot", "jsonb", "lifecycle", "text", "written_at", "timestamp with time zone");

    /** The v2 table contracts, verbatim. */
    static final List<TableContract> TABLE_CONTRACTS = ENTITIES.stream()
            .flatMap(e -> Stream.of(
                    new TableContract(e + "_journal", "PRIMARY KEY (pid, sn)", JOURNAL_COLUMNS, Set.of("pid")),
                    new TableContract(e + "_journal_seq", "PRIMARY KEY (pid)", JOURNAL_SEQ_COLUMNS, Set.of("pid")),
                    new TableContract(e + "_snaps", "PRIMARY KEY (pid, sn, written_at)", SNAPS_COLUMNS,
                            Set.of("pid"))))
            .toList();

    private static final PostgresSchemaDescriptor DESCRIPTOR = new PostgresSchemaDescriptor() {
        @Override
        public String component() {
            return PostgresSchema.COMPONENT;
        }

        @Override
        public int version() {
            return 2;
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

    private PostgresSchemaV2() {
        throw new AssertionError("no instances");
    }

    static PostgresSchemaDescriptor descriptor() {
        return DESCRIPTOR;
    }
}
