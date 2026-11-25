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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Pattern;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.persistence.postgres.search.query.FilterFieldWpath;
import org.eclipse.ditto.rql.query.expression.ThingsFieldExpressionFactory;

/**
 * One operator-configured per-path index on {@code search_flat}: a partial index restricted to the {@code wpath} that
 * a filter on the configured search field is translated to.
 * <p>
 * The index name is {@value #NAME_PREFIX}{@code <kind>_<hash>}, where {@code <hash>} is the first 16 hex digits of the
 * SHA-256 of the index definition (access method, expression and predicate including the path). It is therefore the
 * same on every replica and every start for the same path and kind, it fits PostgreSQL's 63-byte identifier limit for
 * any path, and a code change to a kind's definition yields a new name, so the old index is dropped and the new one
 * built. The {@value #NAME_PREFIX} prefix marks the indexes the search backend owns; no other index is ever dropped.
 * </p>
 */
@Immutable
public final class PathIndex {

    /**
     * The name prefix of every index this backend manages on {@code search_flat}.
     */
    public static final String NAME_PREFIX = "sf_path_";

    private static final String TABLE = "search_flat";
    private static final Pattern MANAGED_NAME = Pattern.compile(Pattern.quote(NAME_PREFIX) + "[a-z0-9_]+");

    private final String path;
    private final String wpath;
    private final PathIndexKind kind;
    private final String definition;
    private final String name;

    private PathIndex(final String path, final String wpath, final PathIndexKind kind) {
        this.path = path;
        this.wpath = wpath;
        this.kind = kind;
        definition = kind.definition(literal(wpath));
        name = NAME_PREFIX + kind.nameToken() + "_" + hash16(definition);
    }

    /**
     * Validates a configured search field and derives its index.
     *
     * @param path the configured search field, as in an RQL filter (e.g. {@code attributes/location/city}); a leading
     * slash is accepted.
     * @param kind the index kind.
     * @param fieldExpressionFactory the factory the search service parses RQL fields with (same simple field
     * mappings), so the path resolves exactly like a query on it.
     * @return the index.
     * @throws IllegalArgumentException if the path is blank, contains a control character, is not a search field, or
     * is a field without {@code search_flat} rows (e.g. {@code thingId}).
     */
    public static PathIndex of(final String path, final PathIndexKind kind,
            final ThingsFieldExpressionFactory fieldExpressionFactory) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(kind, "kind");
        if (path.isBlank()) {
            throw new IllegalArgumentException("The path of a search path index must not be blank.");
        }
        if (path.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("The path <" + path.replaceAll("\\p{Cntrl}", "?")
                    + "> of a search path index contains a control character.");
        }
        final String wpath;
        try {
            wpath = FilterFieldWpath.of(fieldExpressionFactory.filterBy(path));
        } catch (final RuntimeException e) {
            throw new IllegalArgumentException("The path <" + path + "> of a search path index is not a search "
                    + "field with search_flat rows (attributes/..., features/<id or *>/properties/..., "
                    + "features/<id or *>/desiredProperties/..., features/<id or *>/definition, or a field mapped "
                    + "to such a path): " + e.getMessage(), e);
        }
        return new PathIndex(path, wpath, kind);
    }

    /**
     * @return the path as configured.
     */
    public String path() {
        return path;
    }

    /**
     * @return the {@code search_flat.wpath} value the index is restricted to.
     */
    public String wpath() {
        return wpath;
    }

    /**
     * @return the index kind.
     */
    public PathIndexKind kind() {
        return kind;
    }

    /**
     * @return the deterministic index name.
     */
    public String name() {
        return name;
    }

    /**
     * @return the {@code CREATE INDEX CONCURRENTLY IF NOT EXISTS} statement that builds the index.
     */
    public String createStatement() {
        return "CREATE INDEX CONCURRENTLY IF NOT EXISTS " + name + " ON " + TABLE + " " + definition;
    }

    /**
     * @param indexName an index name.
     * @return whether the name carries the prefix of the indexes this backend manages.
     */
    public static boolean isManagedName(final String indexName) {
        return MANAGED_NAME.matcher(indexName).matches();
    }

    /**
     * @param indexName the name of a managed index.
     * @return the {@code DROP INDEX CONCURRENTLY IF EXISTS} statement for it.
     * @throws IllegalArgumentException if the name is not a managed index name.
     */
    public static String dropStatement(final String indexName) {
        if (!isManagedName(indexName)) {
            throw new IllegalArgumentException("Not a managed search path index name: <" + indexName + ">");
        }
        return "DROP INDEX CONCURRENTLY IF EXISTS " + indexName;
    }

    /**
     * Renders the value as an escape string literal ({@code E'...'}), which reads the same whatever
     * {@code standard_conforming_strings} is set to: a backslash is doubled, a quote is doubled.
     */
    private static String literal(final String value) {
        return "E'" + value.replace("\\", "\\\\").replace("'", "''") + "'";
    }

    /**
     * @return the first 16 hex digits of the SHA-256 of the UTF-8 bytes of the text.
     */
    static String hash16(final String text) {
        try {
            final byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final PathIndex that = (PathIndex) o;
        return name.equals(that.name) && path.equals(that.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, path);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "path=" + path +
                ", wpath=" + wpath +
                ", kind=" + kind.getConfigValue() +
                ", name=" + name +
                "]";
    }

}
