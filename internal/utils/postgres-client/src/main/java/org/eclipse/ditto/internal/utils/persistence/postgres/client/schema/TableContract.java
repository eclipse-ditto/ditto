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

import java.util.Map;
import java.util.Set;

import javax.annotation.concurrent.Immutable;

/**
 * The live-catalog contract for a single table: its expected primary-key definition (as returned by
 * {@code pg_get_constraintdef}), its expected column → {@code data_type} map, and the columns required to carry the
 * byte-ordered {@code COLLATE "C"} collation (which {@code data_type} alone cannot express, since every text column
 * reports {@code text}).
 * <p>
 * Shared by every Postgres-backed component: each supplies its own contracts through
 * {@link PostgresSchemaDescriptor#tableContracts()}, and {@link SchemaVerifier} checks the live catalog against them.
 * </p>
 */
@Immutable
public record TableContract(String tableName, String primaryKeyDef, Map<String, String> columns,
        Set<String> collatedColumns) {

    public TableContract {
        columns = Map.copyOf(columns);
        collatedColumns = Set.copyOf(collatedColumns);
    }
}
