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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.PostgresSchemaManager;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.SchemaChecksum;
import org.junit.Test;

/**
 * Safety net for the persisted schema checksum of the {@code ditto-postgres-persistence} component.
 * <p>
 * The persisted {@code schema_version.checksum} of the component is a SHA-256 over a statement list whose element 0 is
 * the {@code schema_version} table DDL. That DDL is the shared, manager-owned
 * {@link PostgresSchemaManager#SCHEMA_VERSION_DDL} (in {@code internal/utils/postgres-client}), not part of
 * {@code PostgresSchema}; the manager prepends it to the descriptor's own statements
 * ({@link PostgresSchemaManager#effectiveDdl}).
 * </p>
 * <p>
 * This test PINS the exact effective-DDL checksum literal: a bootstrapped database stores this value, and a service
 * whose code computes another one at the same version refuses to boot. If this test fails, the effective DDL has
 * changed — that MUST be a deliberate, versioned schema change, never an accidental side effect of code motion.
 * </p>
 * <p>
 * A schema change bumps {@link PostgresSchema#VERSION}, declares {@link PostgresSchema#MIN_COMPATIBLE_VERSION}, moves
 * the pin, and adds a literal fixture of the version it replaces (its DDL, table contracts and checksum) with a test
 * here that the fixture reproduces that checksum, so that the upgrade from it stays testable.
 * {@code SchemaVerifier.verifyStoredVersion} treats a stored version older than the code's as an upgrade (run the DDL,
 * then advance the row), so deployed databases upgrade rather than refuse to boot.
 * </p>
 */
public final class SchemaChecksumPreservationTest {

    /**
     * The {@code ditto-postgres-persistence} component checksum for the CURRENT schema version, computed over the
     * effective DDL list whose element 0 is the {@code schema_version} table. Only a deliberate
     * {@link PostgresSchema#VERSION} bump may change it.
     */
    private static final String CURRENT_PERSISTENCE_CHECKSUM =
            "f31d2d4299e7eb0624a682061563dc14444581c9da0f51866b5e496f43d1b2f8";

    @Test
    public void persistenceChecksumMatchesThePinnedValueForTheCurrentSchemaVersion() {
        assertThat(PostgresSchema.VERSION).as("bump the pin together with the version").isEqualTo(1);
        assertThat(PostgresSchemaManager.checksum(PostgresSchema.descriptor()))
                .as("effective DDL must not change without a deliberate PostgresSchema.VERSION bump")
                .isEqualTo(CURRENT_PERSISTENCE_CHECKSUM);
    }

    @Test
    public void effectiveDdlPrependsTheSharedSchemaVersionTableToTheComponentStatements() {
        final List<String> effective = PostgresSchemaManager.effectiveDdl(PostgresSchema.descriptor());

        // First statement is the shared, manager-owned schema_version table.
        assertThat(effective.get(0)).isEqualTo(PostgresSchemaManager.SCHEMA_VERSION_DDL);

        // The remainder is exactly the component's own statements — i.e. the effective list is the
        // "[schema_version, ...componentDdl]" ordering the persisted checksum is computed over.
        final List<String> reconstructed = new ArrayList<>();
        reconstructed.add(PostgresSchemaManager.SCHEMA_VERSION_DDL);
        reconstructed.addAll(PostgresSchema.ddlStatements());
        assertThat(effective).isEqualTo(reconstructed);

        // And the pinned checksum equals a direct SHA-256 over that reconstructed list.
        assertThat(SchemaChecksum.compute(reconstructed)).isEqualTo(CURRENT_PERSISTENCE_CHECKSUM);
    }
}
