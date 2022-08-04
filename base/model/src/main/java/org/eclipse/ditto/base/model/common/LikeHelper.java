/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.model.common;

import java.util.regex.Pattern;

/**
 * A helper to create "like" patterns.
 *
 * @since 2.3.0
 */
public final class LikeHelper {

    private static final String LEADING_WILDCARD = "\\Q\\E.*";
    private static final String TRAILING_WILDCARD = ".*\\Q\\E";

    private LikeHelper() {
    }

    /**
     * Convert a wildcard expression into a regular expression.
     *
     * The wildcard cards supported are:
     *
     * <dl>
     *     <dt><code>*</code></dt><dd>Matching any number of any character</dd>
     *     <dt><code>?</code></dt><dd>Matches any single character</dd>
     * </dl>
     *
     * @param expression The wildcard expression to convert.
     * @return The regular expression, which can be compiled with {@link Pattern#compile(String)}.
     */
    public static String convertToRegexSyntax(final String expression) {
        if (expression == null) {
            return null;
        }

        // simplify expression by replacing repeating wildcard with a single one
        final String valueString = replaceRepeatingWildcards(expression);
        // shortcut for single * wildcard
        if ("*".equals(valueString)) {
            return ".*";
        }

        // first escape the whole string
        String escapedString = Pattern.compile(Pattern.quote(valueString)).toString();
        // then enable allowed wildcards (* and ?) again
        escapedString = escapedString.replaceAll("\\*", "\\\\E.*\\\\Q");
        escapedString = escapedString.replaceAll("\\?", "\\\\E.\\\\Q"); // escape Char wild cards for ?

        // prepend ^ if is a prefix match (no * at the beginning of the string, much faster)
        if (!valueString.startsWith(LEADING_WILDCARD)) {
            escapedString = "^" + escapedString;
        }
        // append $ if is a postfix match  (no * at the end of the string, much faster)
        if (!valueString.endsWith(TRAILING_WILDCARD)) {
            escapedString = escapedString + "$";
        }
        return escapedString;
    }

    private static String replaceRepeatingWildcards(final String value) {
        return value.replaceAll("\\*{2,}", "*");
    }

}
