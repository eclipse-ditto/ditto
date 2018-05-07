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
        final String valueString = replaceRepeatingWildcards(getValue().toString());

        if ("*".equals(valueString)) {
            return ".*";
        }

        String escapedString = escapeStringWithoutLeadingOrTrailingWildcard(valueString);
        escapedString = escapedString.replaceAll("\\*", "\\\\E.*\\\\Q");
        escapedString = escapedString.replaceAll("\\?", "\\\\E.\\\\Q"); // escape Char wild cards for ?
        if (!valueString.startsWith("*")) {
            escapedString = "^" + escapedString;
        }
        if (!valueString.endsWith("*")) {
            escapedString = escapedString + "$";
        }
        return escapedString;
    }

    private String replaceRepeatingWildcards(final String value) {
        return value.replaceAll("\\*{2,}", "*");
    }

    private String escapeStringWithoutLeadingOrTrailingWildcard(final String valueString) {
        String stringToEscape = valueString;
        if (valueString.startsWith("*")) {
            stringToEscape = stringToEscape.substring(1);
        }
        if (valueString.endsWith("*")) {
            final int endIndex = stringToEscape.length() - 1;
            if (endIndex > 0) {
                stringToEscape = stringToEscape.substring(0, endIndex);
            }
        }
        return Pattern.compile(Pattern.quote(stringToEscape)).toString();
    }

    @Override
    public <T> T accept(final PredicateVisitor<T> visitor) {
        return visitor.visitLike(convertToRegexSyntaxAndGetOption());
    }
}
