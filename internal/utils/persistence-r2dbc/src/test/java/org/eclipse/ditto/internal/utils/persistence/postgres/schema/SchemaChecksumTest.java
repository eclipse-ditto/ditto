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

import java.util.List;

import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.PostgresSchemaManager;
import org.eclipse.ditto.internal.utils.persistence.postgres.client.schema.SchemaChecksum;
import org.junit.Test;

/**
 * Unit tests for {@link SchemaChecksum} — the {@code schema_version} checksum computation.
 */
public final class SchemaChecksumTest {

    @Test
    public void checksumIsDeterministic() {
        assertThat(PostgresSchemaManager.checksum(PostgresSchema.descriptor())).isEqualTo(PostgresSchemaManager.checksum(PostgresSchema.descriptor()));
    }

    @Test
    public void checksumIsAHex64Sha256() {
        assertThat(PostgresSchemaManager.checksum(PostgresSchema.descriptor())).matches("[0-9a-f]{64}");
    }

    @Test
    public void checksumIsInsensitiveToCosmeticWhitespace() {
        final String a = SchemaChecksum.compute(List.of("CREATE TABLE foo (id INT)"));
        final String b = SchemaChecksum.compute(List.of("CREATE  TABLE\n  foo   (id INT)  "));
        assertThat(a).isEqualTo(b);
    }

    @Test
    public void checksumIsSensitiveToStructuralChange() {
        final String pkSn = SchemaChecksum.compute(List.of("CREATE TABLE t (pid TEXT, sn BIGINT, PRIMARY KEY (pid, sn))"));
        final String pkSnWrittenAt = SchemaChecksum.compute(
                List.of("CREATE TABLE t (pid TEXT, sn BIGINT, PRIMARY KEY (pid, sn, written_at))"));
        assertThat(pkSn).isNotEqualTo(pkSnWrittenAt);
    }

    @Test
    public void checksumIsSensitiveToStatementOrder() {
        final String ab = SchemaChecksum.compute(List.of("CREATE TABLE a (x INT)", "CREATE TABLE b (y INT)"));
        final String ba = SchemaChecksum.compute(List.of("CREATE TABLE b (y INT)", "CREATE TABLE a (x INT)"));
        assertThat(ab).isNotEqualTo(ba);
    }

    @Test
    public void checksumIsSensitiveToCollation() {
        // Adding COLLATE "C" to a pid column is a structural change that must move the checksum, so a pre-existing
        // deployment whose pid columns lacked the collation refuses to boot against the new code.
        final String withoutCollation = SchemaChecksum.compute(List.of("CREATE TABLE t (pid TEXT PRIMARY KEY)"));
        final String withCollation =
                SchemaChecksum.compute(List.of("CREATE TABLE t (pid TEXT COLLATE \"C\" PRIMARY KEY)"));
        assertThat(withoutCollation).isNotEqualTo(withCollation);
    }

    @Test
    public void currentChecksumReflectsCollatedPidColumns() {
        // The live canonical DDL declares COLLATE "C" on every pid column; recomputing the checksum over a copy of the
        // DDL with the collation stripped must differ from the current checksum — i.e. the collation is part of the
        // checksummed schema, not cosmetic.
        final String current = PostgresSchemaManager.checksum(PostgresSchema.descriptor());
        final List<String> stripped = PostgresSchemaManager.effectiveDdl(PostgresSchema.descriptor()).stream()
                .map(s -> s.replace(" COLLATE \"C\"", ""))
                .toList();
        assertThat(SchemaChecksum.compute(stripped)).isNotEqualTo(current);
    }

}
