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

import java.util.List;

import org.junit.Test;

/**
 * Unit tests for {@link SchemaDdlRelations}: which statements name a relation for the same-version fast path, and
 * which make the DDL unnameable (the boot then always runs the DDL).
 */
public final class SchemaDdlRelationsTest {

    @Test
    public void namesTablesAndIndexesInStatementOrderAndSkipsEverythingElse() {
        assertThat(SchemaDdlRelations.of(List.of(
                "CREATE EXTENSION IF NOT EXISTS pg_trgm",
                "CREATE TABLE IF NOT EXISTS items (id TEXT COLLATE \"C\" PRIMARY KEY, val BIGINT NOT NULL)",
                "CREATE INDEX IF NOT EXISTS items_val_idx ON items (val)",
                "CREATE INDEX IF NOT EXISTS items_trgm ON items USING gin ((id COLLATE \"C.utf8\") gin_trgm_ops)",
                "DROP INDEX IF EXISTS items_old_idx",
                "ALTER TABLE items SET (autovacuum_vacuum_scale_factor = 0.01)")))
                .hasValue(List.of("items", "items_val_idx", "items_trgm"));
    }

    @Test
    public void noRelationIsAnEmptyListNotUnnameable() {
        assertThat(SchemaDdlRelations.of(List.of("ALTER TABLE items SET (fillfactor = 90)"))).hasValue(List.of());
    }

    @Test
    public void createStatementsInAnotherFormMakeTheDdlUnnameable() {
        for (final String statement : List.of(
                "CREATE UNIQUE INDEX IF NOT EXISTS items_val_idx ON items (val)",
                "CREATE INDEX items_val_idx ON items (val)",
                "CREATE INDEX CONCURRENTLY IF NOT EXISTS items_val_idx ON items (val)",
                "create index if not exists items_val_idx on items (val)",
                "CREATE INDEX IF NOT EXISTS \"Items_Val\" ON items (val)",
                "CREATE INDEX IF NOT EXISTS other.items_val_idx ON items (val)",
                "CREATE TABLE items (id BIGINT PRIMARY KEY)",
                "CREATE TABLE IF NOT EXISTS items(id BIGINT PRIMARY KEY)",
                "CREATE UNLOGGED TABLE IF NOT EXISTS items (id BIGINT PRIMARY KEY)",
                "CREATE SEQUENCE IF NOT EXISTS items_seq",
                "create extension if not exists pg_trgm")) {
            assertThat(SchemaDdlRelations.of(List.of("CREATE TABLE IF NOT EXISTS ok (id BIGINT PRIMARY KEY)",
                    statement)))
                    .as(statement)
                    .isEmpty();
        }
    }

    @Test
    public void nameLongerThanSixtyThreeCharactersIsUnnameable() {
        final String name = "i".repeat(64);
        assertThat(SchemaDdlRelations.of(List.of("CREATE INDEX IF NOT EXISTS " + name + " ON items (val)")))
                .isEmpty();
        assertThat(SchemaDdlRelations.of(List.of("CREATE INDEX IF NOT EXISTS " + name.substring(1)
                + " ON items (val)"))).hasValue(List.of(name.substring(1)));
    }

}
