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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.PostgresSchemaDescriptor;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.TableContract;

/**
 * The canonical, programmatic DDL definition of the PostgreSQL persistence schema.
 * <p>
 * Every statement is idempotent ({@code CREATE … IF NOT EXISTS}, {@code DROP INDEX IF EXISTS},
 * {@code ALTER TABLE … SET}). The per-entity tables (one set each for {@code things},
 * {@code policies}, {@code connections}, {@code wot}, and a fifth set {@code connection_remember} for connectivity's
 * cluster-sharding remember-entities store) have these shapes:
 * </p>
 * <ul>
 *     <li>{@code <e>_journal} — {@code pid TEXT COLLATE "C"}, {@code sn BIGINT}, {@code seq BIGINT GENERATED ALWAYS AS
 *     IDENTITY} (the monotonic offset used by EventsByTag queries), {@code manifest TEXT}, {@code tags TEXT[]},
 *     {@code event JSONB}, {@code written_at}; {@code PRIMARY KEY (pid, sn)} + {@code GIN(tags)}.</li>
 *     <li>{@code <e>_journal_seq} — an explicit high-water-mark table ({@code pid TEXT COLLATE "C"} PK,
 *     {@code highest_sn}, {@code deleted_to}) that tracks the highest persisted sequence number per entity
 *     independently of the journal rows, so the highest sequence number survives even after journal entries are
 *     deleted.</li>
 *     <li>{@code <e>_snaps} — {@code pid TEXT COLLATE "C"}, {@code sn}, {@code snapshot JSONB}, {@code lifecycle},
 *     {@code written_at}; {@code PRIMARY KEY (pid, sn, written_at)} (so a re-snapshot at the same sequence number is
 *     allowed) and no other index: the primary key serves every snapshot access path (v2 dropped v1's
 *     {@code (pid, sn DESC)} index), and {@code DELETED} snapshots are filtered in the application after the
 *     newest-per-pid pick, which no index on {@code lifecycle} could serve (v3 dropped that partial index).</li>
 * </ul>
 * <p>
 * A single {@code schema_version(component, version, checksum, min_compatible_version)} table (owned by
 * {@code PostgresSchemaManager}) backs the refuse-to-boot guard. Each table additionally gets autovacuum storage
 * parameters ({@code ALTER TABLE … SET (…)}) issued in the same bootstrap transaction: every table is vacuumed after
 * 1 % dead rows, and the journal and snapshot tables, which cleanup prunes, also get a larger vacuum cost budget.
 * </p>
 * <p>
 * The {@link #ddlStatements()} list holds this component's own tables/indexes (the shared {@code schema_version} table
 * is owned by {@code PostgresSchemaManager} and prepended by it); together they are the single source of truth for both
 * the bootstrap (executed verbatim) and the {@code SchemaChecksum} (computed over the concatenated, whitespace-normalised
 * statement text), so the checksum can never drift away from what is actually executed.
 * </p>
 * <p>
 * Implements the backend-agnostic {@link PostgresSchemaDescriptor} (via {@link #descriptor()}) so the generic
 * {@code PostgresSchemaManager} can bootstrap it without any hard dependency on this concrete class.
 * </p>
 */
@Immutable
public final class PostgresSchema {

    /** Logical component identifier stored in {@code schema_version}. */
    public static final String COMPONENT = "ditto-postgres-persistence";

    /**
     * Bumped on any DDL change (the checksum covers every statement), per the schema-evolution policy; each bump also
     * declares {@link #MIN_COMPATIBLE_VERSION} and adds a literal test fixture of the version it replaces (see
     * {@code SchemaChecksumPreservationTest}).
     * v2: v1's {@code (pid, sn DESC)} snaps index dropped — redundant under the loose-index-scan queries.
     * v3: the unused partial snaps index on {@code lifecycle = 'DELETED'} dropped; the journal and snapshot tables get
     * a larger autovacuum cost budget.
     * v4: the fifth table set {@code connection_remember_*} appended (connectivity's remember-entities store, which
     * MongoDB keeps in its own {@code connection_remember_*} collections); the first 36 statements are v3's verbatim.
     */
    public static final int VERSION = 4;

    /**
     * The oldest code schema version that can run against the {@link #VERSION} schema (stored as
     * {@code schema_version.min_compatible_version}); see {@code PostgresSchemaDescriptor#minCompatibleVersion()} for
     * the bump rules. v2 is the first version that declares one; it is 2 because v1 code predates the compatibility
     * check. v3 keeps 2: an index drop and changed storage parameters leave v2 code working against the v3 schema. v4
     * keeps 2: appended tables leave v2 and v3 code working against the v4 schema (they never touch them).
     */
    public static final int MIN_COMPATIBLE_VERSION = 2;

    /** The table-set prefixes of the four entity types — the only sets a read journal may bind to. */
    public static final List<String> ENTITY_TABLE_SETS = List.of("things", "policies", "connections", "wot");

    /**
     * The table set of connectivity's cluster-sharding remember-entities store (since v4): Pekko envelopes under
     * {@code /sharding/…} persistence ids, never Ditto events — no read journal, ping or cleanup binds to it.
     */
    public static final String REMEMBER_TABLE_SET = "connection_remember";

    /**
     * The prefixes that get a full journal/journal_seq/snaps table set: {@link #ENTITY_TABLE_SETS}, then (since v4)
     * {@link #REMEMBER_TABLE_SET}. Append only: the order is the DDL order, and the checksum of every shipped version
     * covers its prefix of this list.
     */
    public static final List<String> ENTITIES = List.of("things", "policies", "connections", "wot",
            REMEMBER_TABLE_SET);

    /**
     * Autovacuum storage parameters of the tables cleanup deletes from ({@code <e>_journal}, {@code <e>_snaps}):
     * vacuum after 1 % dead rows (PostgreSQL's default is 20 %), with a cost budget of 2000 per
     * {@code autovacuum_vacuum_cost_delay} (the server default is 200), so that the vacuum after a mass prune is not
     * held back by the cost throttle. A table with its own cost limit is left out of PostgreSQL's cost balancing
     * between autovacuum workers: each worker on such a table uses this budget in full. Only parameters that
     * PostgreSQL 10, the persistence backend's minimum, knows.
     */
    private static final String PRUNED_TABLE_RELOPTIONS =
            "autovacuum_vacuum_scale_factor = 0.01, autovacuum_vacuum_cost_limit = 2000";

    /** The high-water-mark table ({@code <e>_journal_seq}) is updated in place and never pruned: trigger only. */
    private static final String JOURNAL_SEQ_RELOPTIONS = "autovacuum_vacuum_scale_factor = 0.01";

    /**
     * The byte-ordered collation that every persistence-id column must declare so that {@code pid > $1 ORDER BY pid}
     * cursor pagination and primary-key ordering are stable across locales. PostgreSQL reports this collation as
     * {@code information_schema.columns.collation_name = 'C'}.
     */
    public static final String PID_COLLATION = "C";

    /**
     * The columns that must carry {@link #PID_COLLATION}. {@code information_schema.columns.data_type} reports
     * {@code text} regardless of collation, so the collation cannot be inferred from the data-type contract; this set
     * tells the live-catalog verifier which columns to additionally check {@code collation_name} on.
     */
    private static final Set<String> COLLATED_PID_COLUMNS = Set.of("pid");

    /**
     * Per-table {@code information_schema.columns} contract used by the live-catalog verification: column name →
     * expected {@code data_type}. {@code CREATE TABLE IF NOT EXISTS} silently keeps a divergent pre-existing table,
     * so the checksum alone cannot catch drift on first boot — this catalog contract does.
     */
    private static final Map<String, String> JOURNAL_COLUMNS = orderedColumns(
            "pid", "text",
            "sn", "bigint",
            "seq", "bigint",
            "manifest", "text",
            "tags", "ARRAY",
            "event", "jsonb",
            "written_at", "timestamp with time zone");

    private static final Map<String, String> JOURNAL_SEQ_COLUMNS = orderedColumns(
            "pid", "text",
            "highest_sn", "bigint",
            "deleted_to", "bigint");

    private static final Map<String, String> SNAPS_COLUMNS = orderedColumns(
            "pid", "text",
            "sn", "bigint",
            "snapshot", "jsonb",
            "lifecycle", "text",
            "written_at", "timestamp with time zone");

    private PostgresSchema() {
        throw new AssertionError();
    }

    /**
     * @return this component as a backend-agnostic {@link PostgresSchemaDescriptor}, for the generic
     * {@code PostgresSchemaManager}.
     */
    public static PostgresSchemaDescriptor descriptor() {
        return DESCRIPTOR;
    }

    private static final PostgresSchemaDescriptor DESCRIPTOR = new PostgresSchemaDescriptor() {
        @Override
        public String component() {
            return COMPONENT;
        }

        @Override
        public int version() {
            return VERSION;
        }

        @Override
        public int minCompatibleVersion() {
            return MIN_COMPATIBLE_VERSION;
        }

        @Override
        public List<String> ddlStatements() {
            return PostgresSchema.ddlStatements();
        }

        @Override
        public List<TableContract> tableContracts() {
            return PostgresSchema.tableContracts();
        }
    };

    /**
     * @return this component's own ordered list of DDL statements (CREATE TABLE / CREATE INDEX /
     * DROP INDEX IF EXISTS / ALTER TABLE … autovacuum), <strong>excluding</strong> the shared {@code schema_version}
     * table (owned by {@code PostgresSchemaManager}). Order is stable so the checksum is deterministic.
     */
    public static List<String> ddlStatements() {
        final List<String> statements = new ArrayList<>();
        for (final String entity : ENTITIES) {
            statements.add(createJournalTable(entity));
            statements.add(createJournalTagsIndex(entity));
            statements.add(createJournalSeqTable(entity));
            statements.add(createSnapsTable(entity));
            statements.add(dropSnapsPidSnIndex(entity));
            statements.add(dropSnapsLifecycleIndex(entity));
            // autovacuum tuning — transactional ALTER in the SAME bootstrap txn.
            statements.add(autovacuum(entity + "_journal", PRUNED_TABLE_RELOPTIONS));
            statements.add(autovacuum(entity + "_journal_seq", JOURNAL_SEQ_RELOPTIONS));
            statements.add(autovacuum(entity + "_snaps", PRUNED_TABLE_RELOPTIONS));
        }
        return List.copyOf(statements);
    }

    /**
     * @return the live-catalog verification contract: table name → ({@code primary key definition}, {@code column →
     * data_type}). The bootstrap asserts each against {@code pg_get_constraintdef} + {@code information_schema.columns}.
     */
    public static List<TableContract> tableContracts() {
        final List<TableContract> contracts = new ArrayList<>();
        for (final String entity : ENTITIES) {
            contracts.add(new TableContract(entity + "_journal", "PRIMARY KEY (pid, sn)", JOURNAL_COLUMNS,
                    COLLATED_PID_COLUMNS));
            contracts.add(new TableContract(entity + "_journal_seq", "PRIMARY KEY (pid)", JOURNAL_SEQ_COLUMNS,
                    COLLATED_PID_COLUMNS));
            contracts.add(new TableContract(entity + "_snaps", "PRIMARY KEY (pid, sn, written_at)", SNAPS_COLUMNS,
                    COLLATED_PID_COLUMNS));
        }
        return List.copyOf(contracts);
    }

    private static String createJournalTable(final String entity) {
        return "CREATE TABLE IF NOT EXISTS " + entity + "_journal ("
                + "pid TEXT NOT NULL COLLATE \"C\", "
                + "sn BIGINT NOT NULL, "
                + "seq BIGINT GENERATED ALWAYS AS IDENTITY, "
                + "manifest TEXT NOT NULL, "
                + "tags TEXT[] NOT NULL DEFAULT '{}', "
                + "event JSONB NOT NULL, "
                + "written_at TIMESTAMPTZ NOT NULL DEFAULT now(), "
                + "PRIMARY KEY (pid, sn)"
                + ")";
    }

    private static String createJournalTagsIndex(final String entity) {
        return "CREATE INDEX IF NOT EXISTS " + entity + "_journal_tags_idx ON " + entity
                + "_journal USING GIN (tags)";
    }

    private static String createJournalSeqTable(final String entity) {
        return "CREATE TABLE IF NOT EXISTS " + entity + "_journal_seq ("
                + "pid TEXT COLLATE \"C\" PRIMARY KEY, "
                + "highest_sn BIGINT NOT NULL, "
                + "deleted_to BIGINT NOT NULL DEFAULT 0"
                + ")";
    }

    private static String createSnapsTable(final String entity) {
        return "CREATE TABLE IF NOT EXISTS " + entity + "_snaps ("
                + "pid TEXT NOT NULL COLLATE \"C\", "
                + "sn BIGINT NOT NULL, "
                + "snapshot JSONB NOT NULL, "
                + "lifecycle TEXT, "
                + "written_at TIMESTAMPTZ NOT NULL, "
                + "PRIMARY KEY (pid, sn, written_at)"
                + ")";
    }

    private static String dropSnapsPidSnIndex(final String entity) {
        // v2 upgrade: the v1 (pid, sn DESC) index never satisfied the newest-per-pid sort (it lacks the
        // written_at tiebreak column) and the loose-index-scan rewrite left no query that needs it — the
        // PK (pid, sn, written_at) serves every snaps access path, including per-pid backward scans.
        return "DROP INDEX IF EXISTS " + entity + "_snaps_pid_sn_desc_idx";
    }

    private static String dropSnapsLifecycleIndex(final String entity) {
        // v3 upgrade: the partial index on lifecycle = 'DELETED' served no query. DELETED snapshots are filtered in the
        // application after the newest-per-pid pick ("exclude the pid if its newest snapshot is DELETED"), which no
        // index on lifecycle can serve, and cleanup reads with includeDeleted = true.
        return "DROP INDEX IF EXISTS " + entity + "_snaps_lifecycle_idx";
    }

    private static String autovacuum(final String table, final String reloptions) {
        return "ALTER TABLE " + table + " SET (" + reloptions + ")";
    }

    private static Map<String, String> orderedColumns(final String... nameTypePairs) {
        if (nameTypePairs.length % 2 != 0) {
            throw new IllegalArgumentException("Expecting name/type pairs");
        }
        final Map<String, String> columns = new LinkedHashMap<>();
        for (int i = 0; i < nameTypePairs.length; i += 2) {
            columns.put(nameTypePairs[i], nameTypePairs[i + 1]);
        }
        return Map.copyOf(columns);
    }

}
