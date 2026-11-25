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

import java.util.Optional;
import java.util.OptionalInt;

import javax.annotation.Nullable;

/**
 * Decision logic of the PostgreSQL server-version check that {@link PostgresSchemaManager} runs at every bootstrap.
 * <p>
 * PostgreSQL 16 is the oldest version Ditto is tested with (16, 17 and 18). An older server only gets a warning, never a
 * refused boot: the persistence backend needs nothing newer than PostgreSQL 10, and the search backend needs
 * PostgreSQL 13, from which on {@code pg_trgm} is a trusted extension that the DDL role may create without superuser.
 * </p>
 */
final class PostgresServerVersion {

    /** Reports the server version as one integer, e.g. {@code 160004} for 16.4 and {@code 90624} for 9.6.24. */
    static final String SHOW_SERVER_VERSION_NUM = "SHOW server_version_num";

    /** {@code server_version_num} of PostgreSQL 16.0, the oldest version Ditto is tested with. */
    static final int MINIMUM_TESTED_VERSION_NUM = 160000;

    /** From PostgreSQL 10 on, {@code server_version_num} is {@code major * 10000 + minor}. */
    private static final int TWO_PART_VERSIONS_FROM = 100000;

    private PostgresServerVersion() {
        throw new AssertionError();
    }

    /**
     * @param value the text {@code SHOW server_version_num} returned.
     * @return the version number, or empty if the value is not a positive integer.
     */
    static OptionalInt parse(@Nullable final String value) {
        if (value == null) {
            return OptionalInt.empty();
        }
        final String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.length() > 9 || !trimmed.chars().allMatch(Character::isDigit)) {
            return OptionalInt.empty();
        }
        final int versionNum = Integer.parseInt(trimmed);
        return versionNum > 0 ? OptionalInt.of(versionNum) : OptionalInt.empty();
    }

    /**
     * @param versionNum a {@code server_version_num}.
     * @return the version as PostgreSQL prints it, e.g. {@code 16.4} or {@code 9.6.24}.
     */
    static String display(final int versionNum) {
        final int major = versionNum / 10000;
        if (versionNum >= TWO_PART_VERSIONS_FROM) {
            return major + "." + versionNum % 10000;
        }
        return major + "." + versionNum / 100 % 100 + "." + versionNum % 100;
    }

    /**
     * @param component the component whose schema is bootstrapped.
     * @param versionNum the server's {@code server_version_num}.
     * @return the warning to log when the server is older than PostgreSQL 16, else empty.
     */
    static Optional<String> warning(final String component, final int versionNum) {
        if (versionNum >= MINIMUM_TESTED_VERSION_NUM) {
            return Optional.empty();
        }
        return Optional.of("PostgreSQL server version " + display(versionNum) + " (server_version_num " + versionNum
                + ") is older than 16, the oldest version Ditto is tested with (tested: 16, 17, 18). The boot of "
                + "component '" + component + "' is not refused: the persistence backend needs PostgreSQL 10 or newer "
                + "and the search backend needs PostgreSQL 13 or newer (pg_trgm must be a trusted extension). Upgrade "
                + "to PostgreSQL 16 or newer.");
    }

}
