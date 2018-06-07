/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.thingsearch.querymodel.criteria;


import java.util.regex.Pattern;

import org.eclipse.ditto.services.thingsearch.querymodel.criteria.visitors.PredicateVisitor;

/**
 * Like predicate.
 */
public class LikePredicateImpl extends AbstractSinglePredicate {

    public LikePredicateImpl(final Object value) {
        super(value);
    }

    private String convertToRegexSyntaxAndGetOption() {
        // simplify expression by replacing repeating wildcard with a single one
        final String valueString = replaceRepeatingWildcards(getValue().toString());
        // shortcut for single * wildcard
        if ("*".equals(valueString)) {
            return ".*";
        }
        // remove leading or trailing wildcard because queries like /^a/ are much faster than /^a.*$/ or /^a.*/
        // from mongodb docs:
        // "Additionally, while /^a/, /^a.*/, and /^a.*$/ match equivalent strings, they have different performance
        // characteristics. All of these expressions use an index if an appropriate index exists;
        // however, /^a.*/, and /^a.*$/ are slower. /^a/ can stop scanning after matching the prefix."
        final String valueWithoutLeadingOrTrailingWildcard = removeLeadingOrTrailingWildcard(valueString);
        // first escape the whole string
        String escapedString = Pattern.compile(Pattern.quote(valueWithoutLeadingOrTrailingWildcard)).toString();
        // then enable allowed wildcards (* and ?) again
        escapedString = escapedString.replaceAll("\\*", "\\\\E.*\\\\Q");
        escapedString = escapedString.replaceAll("\\?", "\\\\E.\\\\Q"); // escape Char wild cards for ?

        // prepend ^ if is a prefix match (no * at the beginning of the string, much faster)
        if (!valueString.startsWith("*")) {
            escapedString = "^" + escapedString;
        }
        // append $ if is a postfix match  (no * at the end of the string, much faster)
        if (!valueString.endsWith("*")) {
            escapedString = escapedString + "$";
        }
        return escapedString;
    }

    private String replaceRepeatingWildcards(final String value) {
        return value.replaceAll("\\*{2,}", "*");
    }

    private String removeLeadingOrTrailingWildcard(final String valueString) {
        String valueWithoutLeadingOrTrailingWildcard = valueString;
        if (valueString.startsWith("*")) {
            valueWithoutLeadingOrTrailingWildcard = valueWithoutLeadingOrTrailingWildcard.substring(1);
        }
        if (valueString.endsWith("*")) {
            final int endIndex = valueWithoutLeadingOrTrailingWildcard.length() - 1;
            if (endIndex > 0) {
                valueWithoutLeadingOrTrailingWildcard = valueWithoutLeadingOrTrailingWildcard.substring(0, endIndex);
            }
        }
        return valueWithoutLeadingOrTrailingWildcard;
    }

    @Override
    public <T> T accept(final PredicateVisitor<T> visitor) {
        return visitor.visitLike(convertToRegexSyntaxAndGetOption());
    }
}
