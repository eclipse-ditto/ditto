/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.model.query.criteria;


import java.util.regex.Pattern;

import org.eclipse.ditto.model.query.criteria.visitors.PredicateVisitor;

/**
 * Like predicate.
 */
public class LikePredicateImpl extends AbstractSinglePredicate {

    private static final String LEADING_WILDCARD = "\\Q\\E.*";
    private static final String TRAILING_WILDCARD = ".*\\Q\\E";

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

    private String replaceRepeatingWildcards(final String value) {
        return value.replaceAll("\\*{2,}", "*");
    }

    @Override
    public <T> T accept(final PredicateVisitor<T> visitor) {
        return visitor.visitLike(convertToRegexSyntaxAndGetOption());
    }
}
