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
package org.eclipse.ditto.internal.utils.persistence.postgres.client.monitoring;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

/**
 * Contract test for the engine-neutral {@link PostgresMetrics} family: asserts the tag keys, the
 * {@code engine}/{@code status} values, and the closed {@code op_kind} value domain — and that it does NOT reuse the
 * Mongo {@code _mongodb} naming.
 */
public final class PostgresMetricsTest {

    @Test
    public void metricNameIsNeutralNotMongoSuffixed() {
        assertThat(PostgresMetrics.COMMAND_DURATION).isEqualTo("ditto_persistence_command_duration");
        assertThat(PostgresMetrics.COMMAND_DURATION).doesNotContain("_mongodb");
    }

    @Test
    public void tagKeysAreEngineOpKindStatus() {
        assertThat(PostgresMetrics.TAG_ENGINE).isEqualTo("engine");
        assertThat(PostgresMetrics.TAG_OP_KIND).isEqualTo("op_kind");
        assertThat(PostgresMetrics.TAG_STATUS).isEqualTo("status");
    }

    @Test
    public void engineTagValueIsPostgres() {
        assertThat(PostgresMetrics.ENGINE_POSTGRES).isEqualTo("postgres");
    }

    @Test
    public void statusDomainIsSuccessAndError() {
        assertThat(PostgresMetrics.STATUS_DOMAIN).containsExactlyInAnyOrder("success", "error");
    }

    @Test
    public void opKindDomainIsTheClosedNeutralSet() {
        assertThat(PostgresMetrics.OP_KIND_DOMAIN)
                .containsExactlyInAnyOrder("select", "insert", "update", "delete", "ddl", "other");
    }

    @Test
    public void classifyMapsLeadingKeywordIntoTheDomain() {
        assertThat(PostgresMetrics.classifyOpKind("SELECT 1")).isEqualTo("select");
        assertThat(PostgresMetrics.classifyOpKind("  with x as (select 1) select * from x")).isEqualTo("select");
        assertThat(PostgresMetrics.classifyOpKind("INSERT INTO t VALUES (1)")).isEqualTo("insert");
        assertThat(PostgresMetrics.classifyOpKind("update t set x=1")).isEqualTo("update");
        assertThat(PostgresMetrics.classifyOpKind("DELETE FROM t")).isEqualTo("delete");
        assertThat(PostgresMetrics.classifyOpKind("CREATE TABLE t ()")).isEqualTo("ddl");
        assertThat(PostgresMetrics.classifyOpKind("ALTER TABLE t SET ()")).isEqualTo("ddl");
        assertThat(PostgresMetrics.classifyOpKind("VACUUM")).isEqualTo("other");
        assertThat(PostgresMetrics.classifyOpKind(null)).isEqualTo("other");
        assertThat(PostgresMetrics.classifyOpKind("   ")).isEqualTo("other");
    }

    @Test
    public void everyClassifiedValueIsWithinTheDomain() {
        for (final String sql : new String[] {"select 1", "insert", "update", "delete", "create", "drop", "x", null}) {
            assertThat(PostgresMetrics.OP_KIND_DOMAIN).contains(PostgresMetrics.classifyOpKind(sql));
        }
    }

}
