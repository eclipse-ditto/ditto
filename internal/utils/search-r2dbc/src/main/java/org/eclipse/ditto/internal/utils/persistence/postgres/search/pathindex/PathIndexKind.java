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
package org.eclipse.ditto.internal.utils.persistence.postgres.search.pathindex;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.ditto.internal.utils.persistence.postgres.search.PostgresSearchSchema;

/**
 * The kinds of operator-configured per-path {@code search_flat} indexes. Every kind is a partial index restricted to
 * one {@code wpath}; the kind decides the access method, the indexed expression and the rest of the predicate.
 */
public enum PathIndexKind {

    /**
     * A {@code pg_trgm} GIN index for {@code like}/{@code ilike} on one path: the same indexed expression as the
     * table-wide {@code sf_trgm} ({@code (val_text COLLATE "C.utf8") gin_trgm_ops}), so every clause the translator
     * matches against {@code sf_trgm} also matches this index, restricted to {@code wpath = '<p>'}.
     */
    TRIGRAM("trigram", "trgm") {
        @Override
        String definition(final String wpathLiteral) {
            return "USING gin ((val_text COLLATE \"" + PostgresSearchSchema.TRIGRAM_INDEX_COLLATION
                    + "\") gin_trgm_ops) WHERE wpath = " + wpathLiteral + " AND val_text IS NOT NULL";
        }
    };

    private final String configValue;
    private final String nameToken;

    PathIndexKind(final String configValue, final String nameToken) {
        this.configValue = configValue;
        this.nameToken = nameToken;
    }

    /**
     * @param wpathLiteral the {@code wpath} as an escaped SQL string literal.
     * @return the part of the {@code CREATE INDEX} statement after {@code ON search_flat}: access method, indexed
     * expression and partial-index predicate.
     */
    abstract String definition(String wpathLiteral);

    /**
     * @return the value that selects this kind in the configuration.
     */
    public String getConfigValue() {
        return configValue;
    }

    /**
     * @return the short token this kind contributes to its index names.
     */
    String nameToken() {
        return nameToken;
    }

    /**
     * @param value a configured kind.
     * @return the kind with exactly this config value, if any.
     */
    public static Optional<PathIndexKind> fromConfigValue(final String value) {
        return Arrays.stream(values()).filter(kind -> kind.configValue.equals(value)).findFirst();
    }

    /**
     * @return the accepted config values, for error messages.
     */
    static String configValues() {
        return Arrays.stream(values()).map(PathIndexKind::getConfigValue).collect(Collectors.joining(", "));
    }

}
