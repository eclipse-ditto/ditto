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

import java.util.Optional;
import java.util.OptionalInt;

import org.junit.Test;

/**
 * Unit tests for the decision logic of the PostgreSQL server-version check: parsing {@code server_version_num}, the
 * human-readable version, and the warning below the oldest tested version (16).
 */
public final class PostgresServerVersionTest {

    @Test
    public void parsesServerVersionNum() {
        assertThat(PostgresServerVersion.parse("160004")).isEqualTo(OptionalInt.of(160004));
        assertThat(PostgresServerVersion.parse(" 180000 ")).isEqualTo(OptionalInt.of(180000));
        assertThat(PostgresServerVersion.parse("90624")).isEqualTo(OptionalInt.of(90624));
    }

    @Test
    public void rejectsValuesThatAreNoServerVersionNum() {
        assertThat(PostgresServerVersion.parse(null)).isEmpty();
        assertThat(PostgresServerVersion.parse("")).isEmpty();
        assertThat(PostgresServerVersion.parse("16.4")).isEmpty();
        assertThat(PostgresServerVersion.parse("PostgreSQL 16")).isEmpty();
        assertThat(PostgresServerVersion.parse("-160000")).isEmpty();
        assertThat(PostgresServerVersion.parse("0")).isEmpty();
        assertThat(PostgresServerVersion.parse("99999999999")).isEmpty();
    }

    @Test
    public void displaysMajorMinorFromVersionTenOn() {
        assertThat(PostgresServerVersion.display(160004)).isEqualTo("16.4");
        assertThat(PostgresServerVersion.display(180000)).isEqualTo("18.0");
        assertThat(PostgresServerVersion.display(150013)).isEqualTo("15.13");
        assertThat(PostgresServerVersion.display(100000)).isEqualTo("10.0");
    }

    @Test
    public void displaysMajorMinorPatchBeforeVersionTen() {
        assertThat(PostgresServerVersion.display(90624)).isEqualTo("9.6.24");
        assertThat(PostgresServerVersion.display(90500)).isEqualTo("9.5.0");
    }

    @Test
    public void noWarningFromSixteenOn() {
        assertThat(PostgresServerVersion.warning("things", 160000)).isEmpty();
        assertThat(PostgresServerVersion.warning("things", 160004)).isEmpty();
        assertThat(PostgresServerVersion.warning("things", 170006)).isEmpty();
        assertThat(PostgresServerVersion.warning("search", 180000)).isEmpty();
        assertThat(PostgresServerVersion.warning("search", 190000)).isEmpty();
    }

    @Test
    public void warnsBelowSixteenNamingTheRealRequirements() {
        final Optional<String> warning = PostgresServerVersion.warning("things", 150013);

        assertThat(warning).hasValueSatisfying(message -> assertThat(message)
                .contains("15.13")
                .contains("server_version_num 150013")
                .contains("component 'things'")
                .contains("older than 16")
                .contains("tested")
                .contains("persistence backend needs PostgreSQL 10 or newer")
                .contains("search backend needs PostgreSQL 13 or newer")
                .contains("pg_trgm")
                .contains("not refused"));
    }

    @Test
    public void warnsJustBelowSixteenAndForVeryOldServers() {
        assertThat(PostgresServerVersion.warning("search", 159999)).isPresent();
        assertThat(PostgresServerVersion.warning("search", 130000)).isPresent();
        assertThat(PostgresServerVersion.warning("things", 100000)).isPresent();
        assertThat(PostgresServerVersion.warning("things", 90624))
                .hasValueSatisfying(message -> assertThat(message).contains("9.6.24"));
    }

    @Test
    public void theMinimumTestedVersionIsSixteen() {
        assertThat(PostgresServerVersion.MINIMUM_TESTED_VERSION_NUM).isEqualTo(160000);
        assertThat(PostgresServerVersion.SHOW_SERVER_VERSION_NUM).isEqualTo("SHOW server_version_num");
    }

}
