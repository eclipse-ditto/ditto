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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.PostgresSchemaManager;
import org.junit.Test;

/**
 * Pins the {@code ditto-postgres-search} schema checksums. Deployed databases store the checksum of the version that
 * bootstrapped them, and a same-version mismatch refuses to boot, so the checksum may only move together with a
 * deliberate {@link PostgresSearchSchema#VERSION} bump.
 * <p>
 * History: v1 = {@link PostgresSearchSchemaV1#CHECKSUM}; v2 appended the autovacuum reloptions of
 * {@code search_things} and {@code search_flat}; v3 = v2's DDL; its change, {@code sf_multi}, is built outside the DDL.
 * Every bump adds a literal fixture of the version it replaces (see {@link PostgresSearchSchemaV1}) and pins its
 * checksum here, next to the new current one.
 * </p>
 */
public final class SearchSchemaChecksumPreservationTest {

    /** The checksum of the current search schema version ({@link PostgresSearchSchema#VERSION} 3, DDL as v2). */
    private static final String CURRENT_SEARCH_CHECKSUM =
            "c51c0ddb092c925e6b2b1413aed8ae8e5c7826edcb3772af669832761fc21169";

    @Test
    public void searchChecksumMatchesThePinnedValueForTheCurrentSchemaVersion() {
        assertThat(PostgresSearchSchema.VERSION).isEqualTo(3);
        assertThat(PostgresSchemaManager.checksum(PostgresSearchSchema.descriptor()))
                .as("search DDL must not drift without a deliberate PostgresSearchSchema.VERSION bump")
                .isEqualTo(CURRENT_SEARCH_CHECKSUM);
    }

    @Test
    public void frozenV1DescriptorReproducesTheChecksumV1DatabasesStore() {
        assertThat(PostgresSchemaManager.checksum(PostgresSearchSchemaV1.descriptor()))
                .as("the v1 fixture must be exactly the DDL v1 shipped")
                .isEqualTo(PostgresSearchSchemaV1.CHECKSUM);
    }

    @Test
    public void frozenV2DescriptorReproducesTheChecksumV2DatabasesStore() {
        assertThat(PostgresSchemaManager.checksum(PostgresSearchSchemaV2.descriptor()))
                .as("the v2 fixture must be exactly the DDL v2 shipped")
                .isEqualTo(PostgresSearchSchemaV2.CHECKSUM);
    }

    @Test
    public void v2IsTheV1DdlFollowedByTheAutovacuumReloptionsWithTheV1TableContracts() {
        final List<String> v1 = PostgresSearchSchemaV1.DDL_STATEMENTS;
        final List<String> v2 = PostgresSearchSchemaV2.DDL_STATEMENTS;

        assertThat(v2.subList(0, v1.size())).as("v2 keeps every v1 statement unchanged, in order").isEqualTo(v1);
        assertThat(v2.subList(v1.size(), v2.size())).containsExactly(
                "ALTER TABLE search_things SET (autovacuum_vacuum_scale_factor = 0.01)",
                "ALTER TABLE search_flat SET (autovacuum_vacuum_scale_factor = 0.01)");
        assertThat(PostgresSearchSchemaV2.TABLE_CONTRACTS).as("v2 changes no table contract")
                .isEqualTo(PostgresSearchSchemaV1.TABLE_CONTRACTS);
    }

    @Test
    public void v3KeepsTheV2DdlAndTableContractsBecauseItsIndexIsBuiltOutsideTheDdl() {
        assertThat(PostgresSearchSchema.ddlStatements()).isEqualTo(PostgresSearchSchemaV2.DDL_STATEMENTS);
        assertThat(PostgresSearchSchema.tableContracts()).isEqualTo(PostgresSearchSchemaV2.TABLE_CONTRACTS);
        assertThat(PostgresSearchSchema.MIN_COMPATIBLE_VERSION).isEqualTo(1);
    }

}
