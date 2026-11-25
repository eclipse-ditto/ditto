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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Names the tables and indexes a component's DDL creates, for the same-version fast path of
 * {@link PostgresSchemaManager}: a boot or self-heal whose stored {@code schema_version} row matches the code skips the
 * component DDL only if every relation named here exists.
 * <p>
 * Only two statement forms are read: {@code CREATE TABLE IF NOT EXISTS <name> (…} and
 * {@code CREATE INDEX IF NOT EXISTS <name> ON …}, where the name is a plain lower-case identifier. Any other statement
 * that creates something (except {@code CREATE EXTENSION IF NOT EXISTS <name>}) makes the DDL unnameable; the manager
 * then runs the DDL at every start, as it did before the fast path existed. Statements that change existing objects
 * ({@code ALTER TABLE … SET (…)}, {@code DROP INDEX IF EXISTS …}) name nothing.
 * </p>
 */
final class SchemaDdlRelations {

    /** The index form the fast path reads; the name is group 1. */
    static final Pattern CREATE_INDEX = Pattern.compile("^CREATE INDEX IF NOT EXISTS (\\S+) ON ");

    /** The table form the fast path reads; the name is group 1. */
    static final Pattern CREATE_TABLE = Pattern.compile("^CREATE TABLE IF NOT EXISTS (\\S+) \\(");

    private static final Pattern CREATE_EXTENSION = Pattern.compile("^CREATE EXTENSION IF NOT EXISTS \\S+$");
    private static final Pattern ANY_CREATE = Pattern.compile("^\\s*CREATE\\s", Pattern.CASE_INSENSITIVE);

    /** A name PostgreSQL stores exactly as written (unquoted, lower case, at most 63 bytes). */
    private static final Pattern PLAIN_NAME = Pattern.compile("[a-z_][a-z0-9_]{0,62}");

    private SchemaDdlRelations() {
        throw new AssertionError();
    }

    /**
     * @param ddlStatements a component's own DDL ({@link PostgresSchemaDescriptor#ddlStatements()}).
     * @return the names of the tables and indexes the statements create, in statement order, or an empty
     * {@code Optional} when a statement creates something whose name cannot be read.
     */
    static Optional<List<String>> of(final List<String> ddlStatements) {
        final List<String> names = new ArrayList<>();
        for (final String statement : ddlStatements) {
            final Matcher index = CREATE_INDEX.matcher(statement);
            final Matcher table = CREATE_TABLE.matcher(statement);
            final String name;
            if (index.find()) {
                name = index.group(1);
            } else if (table.find()) {
                name = table.group(1);
            } else if (CREATE_EXTENSION.matcher(statement).matches() || !ANY_CREATE.matcher(statement).find()) {
                continue;
            } else {
                return Optional.empty();
            }
            if (!PLAIN_NAME.matcher(name).matches()) {
                return Optional.empty();
            }
            names.add(name);
        }
        return Optional.of(List.copyOf(names));
    }

}
