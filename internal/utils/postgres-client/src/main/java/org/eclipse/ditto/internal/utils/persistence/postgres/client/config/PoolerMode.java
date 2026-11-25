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
package org.eclipse.ditto.internal.utils.persistence.postgres.client.config;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * The connection-pooler topology a Ditto deployment runs against.
 * <p>
 * This is purely <em>informational</em>: it never blocks boot. It only drives a soft startup cross-check against
 * {@code prepared-statement-cache-queries}. There is no {@code SHOW pool_mode} probe (that command does not exist in
 * PgBouncer).
 * </p>
 */
public enum PoolerMode {

    /**
     * No external pooler (or a session-pinning one): every session-level guarantee holds.
     */
    DIRECT("direct"),

    /**
     * PgBouncer (or equivalent) in {@code session} pooling mode: one server connection per client connection.
     */
    SESSION("session"),

    /**
     * PgBouncer (or equivalent) in {@code transaction} pooling mode: server connections are multiplexed across clients
     * between transactions. Safe only with unnamed prepared statements ({@code prepared-statement-cache-queries = 0})
     * or PgBouncer &ge; 1.21 with {@code max_prepared_statements > 0}.
     */
    TRANSACTION("transaction");

    private final String configValue;

    PoolerMode(final String configValue) {
        this.configValue = configValue;
    }

    /**
     * @return the lower-case HOCON value representing this mode.
     */
    public String getConfigValue() {
        return configValue;
    }

    /**
     * Resolves the {@code PoolerMode} for the given (case-insensitive) HOCON value.
     *
     * @param value the configured value, e.g. {@code "transaction"}.
     * @return the matching mode, if any.
     */
    public static Optional<PoolerMode> fromConfigValue(final String value) {
        if (value == null) {
            return Optional.empty();
        }
        final String normalized = value.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(mode -> mode.configValue.equals(normalized))
                .findFirst();
    }

}
