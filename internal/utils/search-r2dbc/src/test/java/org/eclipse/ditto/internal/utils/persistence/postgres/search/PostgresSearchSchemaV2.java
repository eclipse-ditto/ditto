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
import java.util.stream.Stream;

import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.PostgresSchemaDescriptor;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.TableContract;

/**
 * The search schema as version 2 shipped it (frozen): v1's DDL followed by the two autovacuum reloptions, with v1's
 * table contracts. Search schema v3 keeps this DDL unchanged (its change, the system index {@code sf_multi}, is built
 * outside the DDL), so v2 and v3 databases store the same checksum. Rule for bumps: see {@link PostgresSearchSchemaV1}.
 */
final class PostgresSearchSchemaV2 {

    /** The {@code schema_version.checksum} every v2 (and v3) search database stores. */
    static final String CHECKSUM = "c51c0ddb092c925e6b2b1413aed8ae8e5c7826edcb3772af669832761fc21169";

    /** The v2 DDL, verbatim. */
    static final List<String> DDL_STATEMENTS = Stream.concat(PostgresSearchSchemaV1.DDL_STATEMENTS.stream(), Stream.of(
                    "ALTER TABLE search_things SET (autovacuum_vacuum_scale_factor = 0.01)",
                    "ALTER TABLE search_flat SET (autovacuum_vacuum_scale_factor = 0.01)"))
            .toList();

    /** The v2 table contracts: v1's, unchanged. */
    static final List<TableContract> TABLE_CONTRACTS = PostgresSearchSchemaV1.TABLE_CONTRACTS;

    private static final PostgresSchemaDescriptor DESCRIPTOR = new PostgresSchemaDescriptor() {
        @Override
        public String component() {
            return PostgresSearchSchema.COMPONENT;
        }

        @Override
        public int version() {
            return 2;
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

    private PostgresSearchSchemaV2() {
        throw new AssertionError("no instances");
    }

    /**
     * @return the v2 search schema descriptor.
     */
    static PostgresSchemaDescriptor descriptor() {
        return DESCRIPTOR;
    }

}
