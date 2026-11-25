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
package org.eclipse.ditto.internal.utils.persistence.postgres.search.query.ast;

import java.util.regex.Pattern;

/**
 * Guards the ONE class of SQL text the renderer emits unquoted besides the fixed grammar: identifiers (table aliases,
 * column names, function names, CTE names). Every such identifier in this AST originates from the schema's own fixed
 * vocabulary ({@code search_things}, {@code sf}, {@code val_num}, {@code jsonb_extract_path}, …), NEVER from a
 * user-supplied path — user paths and values are always binds. This validator is defence-in-depth: it rejects anything
 * that is not a bare unquoted SQL identifier, so even a mistaken caller cannot smuggle syntax (quotes, {@code ;},
 * whitespace, operators) into SQL text through an identifier slot.
 */
final class Identifiers {

    /** A bare, unquoted SQL identifier: a letter/underscore start, then letters/digits/underscores. */
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private Identifiers() {
        throw new AssertionError();
    }

    /**
     * @param identifier the candidate identifier.
     * @param role a short role name for the error message (e.g. {@code "column"}).
     * @return the identifier unchanged if it is a bare unquoted SQL identifier.
     * @throws IllegalArgumentException if it is not — this AST never quotes or escapes identifiers, so a non-identifier
     * would be an injection vector.
     */
    static String require(final String identifier, final String role) {
        if (identifier == null || !IDENTIFIER.matcher(identifier).matches()) {
            throw new IllegalArgumentException("Illegal SQL " + role + " <" + identifier + ">: only bare unquoted "
                    + "identifiers [A-Za-z_][A-Za-z0-9_]* are permitted — user paths and values must be bound as $n "
                    + "parameters, never rendered as identifiers.");
        }
        return identifier;
    }

}
