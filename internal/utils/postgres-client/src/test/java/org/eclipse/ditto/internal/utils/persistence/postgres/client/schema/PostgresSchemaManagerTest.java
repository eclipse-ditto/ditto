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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.Test;
import org.reactivestreams.Publisher;

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryMetadata;

import reactor.core.publisher.Mono;

/**
 * Unit tests for the parts of {@link PostgresSchemaManager} that need no live PostgreSQL: the
 * {@code min_compatible_version} bookkeeping column stays OUT of the checksummed effective DDL (so no deployed checksum
 * moves), and a descriptor's {@code minCompatibleVersion()} must lie within {@code [1, version()]}.
 */
public final class PostgresSchemaManagerTest {

    /** Constructing a manager opens no connection; the factory is never used by these tests. */
    private static final ConnectionFactory UNUSED_FACTORY = new ConnectionFactory() {
        @Override
        public Publisher<? extends Connection> create() {
            return Mono.error(new UnsupportedOperationException("not used by this test"));
        }

        @Override
        public ConnectionFactoryMetadata getMetadata() {
            return () -> "unused";
        }
    };

    @Test
    public void compatibilityColumnDdlIsNotPartOfTheChecksummedEffectiveDdl() {
        final PostgresSchemaDescriptor descriptor = descriptor(2, 1);

        assertThat(PostgresSchemaManager.effectiveDdl(descriptor))
                .as("the manager-owned min_compatible_version ALTER must stay outside the checksummed DDL")
                .doesNotContain(PostgresSchemaManager.SCHEMA_VERSION_COMPATIBILITY_DDL)
                .noneMatch(statement -> statement.contains("min_compatible_version"));
        assertThat(PostgresSchemaManager.SCHEMA_VERSION_DDL).doesNotContain("min_compatible_version");
        assertThat(PostgresSchemaManager.SCHEMA_VERSION_COMPATIBILITY_DDL)
                .isEqualTo("ALTER TABLE schema_version ADD COLUMN IF NOT EXISTS min_compatible_version INT");
    }

    @Test
    public void checksumDoesNotDependOnMinCompatibleVersion() {
        // min_compatible_version is row metadata, not DDL: two descriptors differing only in it share one checksum.
        assertThat(PostgresSchemaManager.checksum(descriptor(3, 3)))
                .isEqualTo(PostgresSchemaManager.checksum(descriptor(3, 1)));
    }

    @Test
    public void acceptsMinCompatibleVersionWithinOneAndVersion() {
        assertThatCode(() -> PostgresSchemaManager.of(UNUSED_FACTORY, descriptor(3, 1)))
                .doesNotThrowAnyException();
        assertThatCode(() -> PostgresSchemaManager.of(UNUSED_FACTORY, descriptor(3, 3)))
                .doesNotThrowAnyException();
    }

    @Test
    public void rejectsMinCompatibleVersionAboveVersion() {
        assertThatThrownBy(() -> PostgresSchemaManager.of(UNUSED_FACTORY, descriptor(2, 3)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minCompatibleVersion");
    }

    @Test
    public void rejectsMinCompatibleVersionBelowOne() {
        assertThatThrownBy(() -> PostgresSchemaManager.of(UNUSED_FACTORY, descriptor(2, 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minCompatibleVersion");
    }

    private static PostgresSchemaDescriptor descriptor(final int version, final int minCompatibleVersion) {
        return new PostgresSchemaDescriptor() {
            @Override
            public String component() {
                return "manager-unit-test";
            }

            @Override
            public int version() {
                return version;
            }

            @Override
            public int minCompatibleVersion() {
                return minCompatibleVersion;
            }

            @Override
            public List<String> ddlStatements() {
                return List.of("CREATE TABLE IF NOT EXISTS manager_unit_test (id BIGINT PRIMARY KEY)");
            }

            @Override
            public List<TableContract> tableContracts() {
                return List.of();
            }
        };
    }

}
