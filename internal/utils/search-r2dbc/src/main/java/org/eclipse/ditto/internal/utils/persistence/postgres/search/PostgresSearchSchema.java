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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.PostgresSchemaDescriptor;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.TableContract;

/**
 * The canonical, programmatic DDL definition of the PostgreSQL <em>things-search</em> schema (plan §3.2). It is the
 * single source of truth for both the bootstrap (executed verbatim by the generic {@code PostgresSchemaManager}) and the
 * {@code SchemaChecksum} (computed over the concatenated, whitespace-normalised statement text), so the checksum can
 * never drift away from what is actually executed.
 * <p>
 * The schema is a <strong>hybrid JSONB document + flattened side-table</strong> design (plan D1/D3):
 * </p>
 * <ul>
 *     <li>{@code search_things} — one row per thing: the enforced {@code thing} JSONB payload (the truth used for
 *     result rendering and auth-tree rechecks), the {@code global_read} subject array, the grant/revoke auth trees
 *     ({@code policy_auth} thing-level, {@code features_auth} per-feature), plus the system columns
 *     (namespace/revision/policy/deleteAt/…).</li>
 *     <li>{@code search_flat} — the flattened {@code (thing_id, path, wpath, ord, typed value)} side table over the
 *     {@code thing} leaves, carrying the exact + {@code /features/*} wildcard addressable forms and the cross-type
 *     {@code type_rank}; composite B-trees serve eq/range/exists/sort and a {@code pg_trgm} GIN serves like/ilike.</li>
 *     <li>{@code search_sync} — the background-sync bookmark (Mongo {@code MongoTimestampPersistence} replacement).</li>
 * </ul>
 * <p>
 * {@code search_things} and {@code search_flat} additionally get {@code ALTER TABLE … SET
 * (autovacuum_vacuum_scale_factor = 0.01)}, in the same bootstrap transaction, like the event-sourcing tables.
 * </p>
 * <p>
 * Implements the backend-agnostic {@link PostgresSchemaDescriptor} (via {@link #descriptor()}) so the generic
 * {@code PostgresSchemaManager} — which owns the shared {@code schema_version} table and the cluster-wide advisory lock —
 * can bootstrap it exactly like the event-sourcing {@code PostgresSchema}, without any hard dependency on this class.
 * The {@code CREATE EXTENSION IF NOT EXISTS pg_trgm} statement is element 0 of {@link #ddlStatements()}; the DDL role
 * must be permitted to create it (it is a trusted extension since PG&nbsp;13, so {@code CREATE} on the database
 * suffices) — {@code PostgresSearchPersistenceProvider#bootstrapSchema()} turns a permission failure into an
 * actionable error naming the prerequisite.
 * </p>
 * <p>
 * <strong>Idempotency deviation from the plan §3.2 illustrative SQL:</strong> every {@code CREATE TABLE}/{@code CREATE
 * INDEX} here additionally carries {@code IF NOT EXISTS} (the plan sketch omits it, as it targets a fresh
 * database). This matches the event-sourcing {@code PostgresSchema} pattern and is required so a re-bootstrap of
 * an already-provisioned database is a no-op rather than an error; the live-catalog verifier still catches a divergent
 * pre-existing table that {@code IF NOT EXISTS} would silently keep.
 * </p>
 * <p>
 * The operator-configured per-path indexes ({@code ditto.postgresql.search.path-indexes}) are deliberately NOT part of
 * this DDL: they are built with {@code CREATE INDEX CONCURRENTLY} (which cannot run in the bootstrap transaction) by
 * {@code PathIndexMaintenance} after a successful bootstrap, so the path list never changes the checksum; nor is the
 * system index {@code sf_multi} (search schema v3), which the same maintenance builds the same way.
 * </p>
 */
@Immutable
public final class PostgresSearchSchema {

    /** Logical component identifier stored in {@code schema_version} — distinct from the persistence backend's row. */
    public static final String COMPONENT = "ditto-postgres-search";

    /**
     * Bumped on any DDL change (the checksum covers every statement), per the schema-evolution policy; each bump also
     * declares {@link #MIN_COMPATIBLE_VERSION} and adds a literal test fixture of the version it replaces (see
     * {@code SearchSchemaChecksumPreservationTest}).
     * v2: {@code autovacuum_vacuum_scale_factor = 0.01} on {@code search_things} and {@code search_flat}.
     * v3: the system index {@code sf_multi} ({@link
     * org.eclipse.ditto.internal.utils.persistence.postgres.search.pathindex.SystemSearchIndex#MULTI_VALUED}). It is
     * built with {@code CREATE INDEX CONCURRENTLY} by {@code PathIndexMaintenance} after the bootstrap, not by this DDL
     * (a plain {@code CREATE INDEX} in the bootstrap transaction would block writes for the whole build on an existing
     * table), so the v3 DDL and checksum equal v2's.
     */
    public static final int VERSION = 3;

    /**
     * The oldest code schema version that can run against the {@link #VERSION} schema (stored as
     * {@code schema_version.min_compatible_version}); see {@code PostgresSchemaDescriptor#minCompatibleVersion()} for
     * the bump rules. Still 1: v2 only adds reloptions, which v1 code runs against unchanged; v3 only adds an index
     * older code ignores.
     */
    public static final int MIN_COMPATIBLE_VERSION = 1;

    /**
     * The per-table {@code autovacuum_vacuum_scale_factor} of {@code search_things} and {@code search_flat} (server
     * default 0.2), as on the event-sourcing tables: autovacuum starts once about 1&nbsp;% of the rows are dead instead
     * of 20&nbsp;%. Every thing update leaves dead rows in both tables (its replaced {@code search_things} row and its
     * changed {@code search_flat} rows); at 20&nbsp;%, a {@code search_flat} of tens of millions of rows collects
     * millions of dead rows before autovacuum even starts. The TOAST table of {@code search_things} (the JSONB
     * documents) inherits the value. Vacuum cost limit and delay stay at the server's settings.
     */
    private static final String AUTOVACUUM_VACUUM_SCALE_FACTOR = "0.01";

    /**
     * The byte-ordered collation ({@code information_schema.columns.collation_name = 'C'}) that every string column
     * participating in Mongo-parity ordering/comparison must declare (thing_id / namespace / path / wpath / val_text).
     * BSON compares strings as UTF-8 code units; the database default collation would order {@code 'a' < 'B'} while
     * {@code "C"}/Mongo order {@code 'B' < 'a'} — affecting every string range predicate and every string sort.
     */
    public static final String COLLATION = "C";

    /**
     * The libc collation of the {@code sf_trgm} trigram index expression {@code (val_text COLLATE "C.utf8")}. A
     * {@code like}/{@code ilike} clause must match under exactly this collation for the planner to use the index.
     */
    public static final String TRIGRAM_INDEX_COLLATION = "C.utf8";

    private static final Map<String, String> SEARCH_THINGS_COLUMNS = orderedColumns(
            "thing_id", "text",
            "namespace", "text",
            "revision", "bigint",
            "policy_id", "text",
            "policy_rev", "bigint",
            "referenced_policies", "jsonb",
            "global_read", "ARRAY",
            "thing", "jsonb",
            "policy_auth", "jsonb",
            "features_auth", "jsonb",
            "t_modified", "timestamp with time zone",
            "delete_at", "timestamp with time zone",
            "updated_at", "timestamp with time zone");

    private static final Set<String> SEARCH_THINGS_COLLATED = Set.of("thing_id", "namespace");

    private static final Map<String, String> SEARCH_FLAT_COLUMNS = orderedColumns(
            "thing_id", "text",
            "path", "text",
            "wpath", "text",
            "f_id", "text",
            "ord", "integer",
            "type_rank", "smallint",
            "val_bool", "boolean",
            "val_num", "numeric",
            "val_text", "text");

    private static final Set<String> SEARCH_FLAT_COLLATED = Set.of("thing_id", "path", "wpath", "val_text");

    private static final Map<String, String> SEARCH_SYNC_COLUMNS = orderedColumns(
            "id", "text",
            "ts", "timestamp with time zone",
            "tag", "text");

    private PostgresSearchSchema() {
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
            return PostgresSearchSchema.ddlStatements();
        }

        @Override
        public List<TableContract> tableContracts() {
            return PostgresSearchSchema.tableContracts();
        }
    };

    /**
     * @return this component's own ordered list of DDL statements (the {@code pg_trgm} extension, the two search tables
     * with their indexes, the background-sync bookmark table, then the autovacuum reloptions of the two search
     * tables), <strong>excluding</strong> the shared {@code schema_version} table (owned by
     * {@code PostgresSchemaManager}). Order is stable so the checksum is deterministic.
     */
    public static List<String> ddlStatements() {
        final List<String> statements = new ArrayList<>();

        // pg_trgm is required by the sf_trgm trigram index (like/ilike). Trusted extension (PG >= 13): CREATE on the DB
        // suffices; a permission failure is turned into an actionable error by the provider's bootstrapSchema().
        statements.add("CREATE EXTENSION IF NOT EXISTS pg_trgm");

        statements.add(createSearchThingsTable());
        statements.add("CREATE INDEX IF NOT EXISTS st_namespace ON search_things (namespace, thing_id)");
        statements.add("CREATE INDEX IF NOT EXISTS st_global_read ON search_things USING gin (global_read)");
        statements.add("CREATE INDEX IF NOT EXISTS st_policy ON search_things (policy_id, policy_rev)");
        statements.add("CREATE INDEX IF NOT EXISTS st_referenced_pols ON search_things "
                + "USING gin (referenced_policies jsonb_path_ops)");
        statements.add("CREATE INDEX IF NOT EXISTS st_delete_at ON search_things (delete_at) "
                + "WHERE delete_at IS NOT NULL");
        statements.add("CREATE INDEX IF NOT EXISTS st_modified ON search_things (t_modified)");

        statements.add(createSearchFlatTable());
        statements.add("CREATE INDEX IF NOT EXISTS sf_num ON search_flat (wpath, val_num) WHERE val_num IS NOT NULL");
        statements.add("CREATE INDEX IF NOT EXISTS sf_text ON search_flat (wpath, val_text) WHERE val_text IS NOT NULL");
        statements.add("CREATE INDEX IF NOT EXISTS sf_bool ON search_flat (wpath, val_bool) WHERE val_bool IS NOT NULL");
        statements.add("CREATE INDEX IF NOT EXISTS sf_exists ON search_flat (wpath)");
        // Trigram GIN on (val_text COLLATE "C.utf8"): the collation override restores non-ASCII case folding for ILIKE
        // (Mongo $options:"i" parity) while eq/range/sort keep the plain COLLATE "C" column + sf_text (plan §3.2).
        statements.add("CREATE INDEX IF NOT EXISTS sf_trgm ON search_flat "
                + "USING gin ((val_text COLLATE \"" + TRIGRAM_INDEX_COLLATION + "\") gin_trgm_ops) "
                + "WHERE val_text IS NOT NULL");

        statements.add(createSearchSyncTable());

        // v2: autovacuum tuning of the two write-heavy tables. Appended after every v1 statement, so the v1 DDL stays
        // a prefix of this list. Catalog-only (no table rewrite); search_sync (one bookmark row) keeps the defaults.
        statements.add(autovacuum("search_things"));
        statements.add(autovacuum("search_flat"));

        return List.copyOf(statements);
    }

    /**
     * @return the live-catalog verification contracts (table name → PK definition + column {@code data_type} map +
     * {@code COLLATE "C"} columns) for the three search tables. The bootstrap asserts each against
     * {@code pg_get_constraintdef} + {@code information_schema.columns}, refusing to boot on any divergence that
     * {@code CREATE TABLE IF NOT EXISTS} would silently keep.
     */
    public static List<TableContract> tableContracts() {
        final List<TableContract> contracts = new ArrayList<>();
        contracts.add(new TableContract("search_things", "PRIMARY KEY (thing_id)",
                SEARCH_THINGS_COLUMNS, SEARCH_THINGS_COLLATED));
        contracts.add(new TableContract("search_flat", "PRIMARY KEY (thing_id, path, wpath, ord)",
                SEARCH_FLAT_COLUMNS, SEARCH_FLAT_COLLATED));
        contracts.add(new TableContract("search_sync", "PRIMARY KEY (id)",
                SEARCH_SYNC_COLUMNS, Set.of()));
        return List.copyOf(contracts);
    }

    private static String createSearchThingsTable() {
        return "CREATE TABLE IF NOT EXISTS search_things ("
                + "thing_id TEXT COLLATE \"C\" PRIMARY KEY, "
                + "namespace TEXT COLLATE \"C\" NOT NULL, "
                + "revision BIGINT NOT NULL, "
                + "policy_id TEXT, "
                + "policy_rev BIGINT, "
                + "referenced_policies JSONB, "
                + "global_read TEXT[] NOT NULL DEFAULT '{}', "
                + "thing JSONB NOT NULL, "
                + "policy_auth JSONB, "
                + "features_auth JSONB, "
                + "t_modified TIMESTAMPTZ, "
                + "delete_at TIMESTAMPTZ, "
                + "updated_at TIMESTAMPTZ NOT NULL DEFAULT now()"
                + ")";
    }

    private static String createSearchFlatTable() {
        return "CREATE TABLE IF NOT EXISTS search_flat ("
                + "thing_id TEXT COLLATE \"C\" NOT NULL REFERENCES search_things (thing_id) ON DELETE CASCADE, "
                + "path TEXT COLLATE \"C\" NOT NULL, "
                + "wpath TEXT COLLATE \"C\" NOT NULL, "
                + "f_id TEXT, "
                + "ord INTEGER NOT NULL DEFAULT 0, "
                + "type_rank SMALLINT NOT NULL, "
                + "val_bool BOOLEAN, "
                + "val_num NUMERIC, "
                + "val_text TEXT COLLATE \"C\", "
                + "PRIMARY KEY (thing_id, path, wpath, ord)"
                + ")";
    }

    private static String createSearchSyncTable() {
        return "CREATE TABLE IF NOT EXISTS search_sync ("
                + "id TEXT PRIMARY KEY, "
                + "ts TIMESTAMPTZ NOT NULL, "
                + "tag TEXT"
                + ")";
    }

    private static String autovacuum(final String table) {
        return "ALTER TABLE " + table + " SET (autovacuum_vacuum_scale_factor = " + AUTOVACUUM_VACUUM_SCALE_FACTOR
                + ")";
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
