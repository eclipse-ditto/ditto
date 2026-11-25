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

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.PostgresSchemaDescriptor;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.TableContract;

/**
 * The search schema as version 1 shipped it, i.e. what a not-yet-upgraded pod runs during a rolling upgrade to v2.
 * <p>
 * The DDL statements and table contracts are written out literally, independent of {@link PostgresSearchSchema}, so a
 * later change there cannot flow into "v1". {@code SearchSchemaChecksumPreservationTest} pins this descriptor's
 * checksum to {@link #CHECKSUM}, the value every v1 database stores.
 * </p>
 * <p>
 * <strong>Rule for schema bumps:</strong> every bump of {@link PostgresSearchSchema#VERSION} adds a literal fixture
 * like this one for the version it replaces ({@link PostgresSearchSchemaV2} was added at the bump to v3; next:
 * {@code PostgresSearchSchemaV3} at the bump to v4), pins its checksum in {@code SearchSchemaChecksumPreservationTest},
 * and uses it in the upgrade and mixed-version tests.
 * </p>
 */
final class PostgresSearchSchemaV1 {

    /** The {@code schema_version.checksum} every v1 search database stores. */
    static final String CHECKSUM = "8c980d0c49517847c2acaad3443735c525b8353dd25f48d7d7c5e2068e5ce3ce";

    /** The v1 DDL, verbatim: the pg_trgm extension, search_things + 6 indexes, search_flat + 5 indexes, search_sync. */
    static final List<String> DDL_STATEMENTS = List.of(
            "CREATE EXTENSION IF NOT EXISTS pg_trgm",
            "CREATE TABLE IF NOT EXISTS search_things ("
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
                    + ")",
            "CREATE INDEX IF NOT EXISTS st_namespace ON search_things (namespace, thing_id)",
            "CREATE INDEX IF NOT EXISTS st_global_read ON search_things USING gin (global_read)",
            "CREATE INDEX IF NOT EXISTS st_policy ON search_things (policy_id, policy_rev)",
            "CREATE INDEX IF NOT EXISTS st_referenced_pols ON search_things USING gin (referenced_policies "
                    + "jsonb_path_ops)",
            "CREATE INDEX IF NOT EXISTS st_delete_at ON search_things (delete_at) WHERE delete_at IS NOT NULL",
            "CREATE INDEX IF NOT EXISTS st_modified ON search_things (t_modified)",
            "CREATE TABLE IF NOT EXISTS search_flat ("
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
                    + ")",
            "CREATE INDEX IF NOT EXISTS sf_num ON search_flat (wpath, val_num) WHERE val_num IS NOT NULL",
            "CREATE INDEX IF NOT EXISTS sf_text ON search_flat (wpath, val_text) WHERE val_text IS NOT NULL",
            "CREATE INDEX IF NOT EXISTS sf_bool ON search_flat (wpath, val_bool) WHERE val_bool IS NOT NULL",
            "CREATE INDEX IF NOT EXISTS sf_exists ON search_flat (wpath)",
            "CREATE INDEX IF NOT EXISTS sf_trgm ON search_flat USING gin ((val_text COLLATE \"C.utf8\") gin_trgm_ops) "
                    + "WHERE val_text IS NOT NULL",
            "CREATE TABLE IF NOT EXISTS search_sync (id TEXT PRIMARY KEY, ts TIMESTAMPTZ NOT NULL, tag TEXT)");

    /** The v1 live-catalog contracts, verbatim. */
    static final List<TableContract> TABLE_CONTRACTS = List.of(
            new TableContract("search_things", "PRIMARY KEY (thing_id)", Map.ofEntries(
                    Map.entry("thing_id", "text"),
                    Map.entry("namespace", "text"),
                    Map.entry("revision", "bigint"),
                    Map.entry("policy_id", "text"),
                    Map.entry("policy_rev", "bigint"),
                    Map.entry("referenced_policies", "jsonb"),
                    Map.entry("global_read", "ARRAY"),
                    Map.entry("thing", "jsonb"),
                    Map.entry("policy_auth", "jsonb"),
                    Map.entry("features_auth", "jsonb"),
                    Map.entry("t_modified", "timestamp with time zone"),
                    Map.entry("delete_at", "timestamp with time zone"),
                    Map.entry("updated_at", "timestamp with time zone")),
                    Set.of("thing_id", "namespace")),
            new TableContract("search_flat", "PRIMARY KEY (thing_id, path, wpath, ord)", Map.of(
                    "thing_id", "text",
                    "path", "text",
                    "wpath", "text",
                    "f_id", "text",
                    "ord", "integer",
                    "type_rank", "smallint",
                    "val_bool", "boolean",
                    "val_num", "numeric",
                    "val_text", "text"),
                    Set.of("thing_id", "path", "wpath", "val_text")),
            new TableContract("search_sync", "PRIMARY KEY (id)", Map.of(
                    "id", "text",
                    "ts", "timestamp with time zone",
                    "tag", "text"),
                    Set.of()));

    private static final PostgresSchemaDescriptor DESCRIPTOR = new PostgresSchemaDescriptor() {
        @Override
        public String component() {
            return PostgresSearchSchema.COMPONENT;
        }

        @Override
        public int version() {
            return 1;
        }

        @Override
        public int minCompatibleVersion() {
            return 1;
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

    private PostgresSearchSchemaV1() {
        throw new AssertionError();
    }

    /**
     * @return the v1 search schema descriptor.
     */
    static PostgresSchemaDescriptor descriptor() {
        return DESCRIPTOR;
    }

}
