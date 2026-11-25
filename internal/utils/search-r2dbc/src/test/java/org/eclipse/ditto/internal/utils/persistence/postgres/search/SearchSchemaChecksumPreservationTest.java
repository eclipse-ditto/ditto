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

import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.PostgresSchemaManager;
import org.junit.Test;

/**
 * Pins the {@code ditto-postgres-search} schema checksum. A bootstrapped database stores the checksum of the version
 * that bootstrapped it, and a same-version mismatch refuses to boot, so the checksum may only move together with a
 * deliberate {@link PostgresSearchSchema#VERSION} bump.
 * <p>
 * Every bump declares {@link PostgresSearchSchema#MIN_COMPATIBLE_VERSION}, moves the pin, and adds a literal fixture
 * of the version it replaces (its DDL, table contracts and checksum) with a test here that the fixture reproduces
 * that checksum, so that the upgrade from it stays testable.
 * </p>
 */
public final class SearchSchemaChecksumPreservationTest {

    /** The checksum of the current search schema version ({@link PostgresSearchSchema#VERSION}). */
    private static final String CURRENT_SEARCH_CHECKSUM =
            "eb7d18490181637ca154cccd56ebaaee3a81de4ad59b5e43abbfa0bd73cfae23";

    @Test
    public void searchChecksumMatchesThePinnedValueForTheCurrentSchemaVersion() {
        assertThat(PostgresSearchSchema.VERSION).as("bump the pin together with the version").isEqualTo(1);
        assertThat(PostgresSchemaManager.checksum(PostgresSearchSchema.descriptor()))
                .as("search DDL must not change without a deliberate PostgresSearchSchema.VERSION bump")
                .isEqualTo(CURRENT_SEARCH_CHECKSUM);
    }

}
