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
        final String valueString = getValue().toString();
        String escapedString = Pattern.compile(Pattern.quote(valueString)).toString();
        escapedString = escapedString.replaceAll("\\*", "\\\\E.*\\\\Q");
        escapedString = escapedString.replaceAll("\\?", "\\\\E.\\\\Q"); // escape Char wild cards for ?
        escapedString = "^" + escapedString + "$"; // escape Start and End
        return escapedString;
    }

    @Override
    public <T> T accept(final PredicateVisitor<T> visitor) {
        return visitor.visitLike(convertToRegexSyntaxAndGetOption());
    }
}
