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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Computes the {@code schema_version} checksum over the canonical DDL.
 * <p>
 * The checksum is a SHA-256 hex digest of the DDL statements, each whitespace-normalised (runs of whitespace collapsed
 * to a single space, trimmed) and joined with {@code ";"}. Normalisation makes the checksum insensitive to cosmetic
 * formatting but sensitive to any structural change — a renamed column, a changed PK, a dropped index, an altered
 * autovacuum factor — so a code-vs-stored downgrade/drift refuses to boot.
 * </p>
 */
public final class SchemaChecksum {

    private SchemaChecksum() {
        throw new AssertionError();
    }

    /**
     * Computes the checksum of the given DDL statements.
     *
     * @param statements the DDL statements, in canonical order.
     * @return the lowercase SHA-256 hex digest.
     */
    public static String compute(final List<String> statements) {
        final String canonical = statements.stream()
                .map(SchemaChecksum::normalize)
                .collect(Collectors.joining(";"));
        return sha256Hex(canonical);
    }

    private static String normalize(final String statement) {
        return statement.replaceAll("\\s+", " ").trim();
    }

    private static String sha256Hex(final String input) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            final StringBuilder hex = new StringBuilder(hash.length * 2);
            for (final byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (final NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the JLS to be present on every JVM.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

}
