/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.placeholders.filter;

import java.util.regex.Pattern;

/**
 * Keeps the value if both passed parameters are equal to each other.
 */
final class LikeFunction implements FilterFunction {

    @Override
    public String getName() {
        return "like";
    }

    @Override
    public boolean apply(final String... parameters) {
        if (parameters.length != 2) {
            return false;
        }
        final String toMatch = parameters[0];
        final String patternString = toJavaRegex(parameters[1]);
        return toMatch.matches(patternString);
    }

    private static String toJavaRegex(final String patternString) {
        return Pattern.quote(patternString)
                .replaceAll("\\*", "\\\\E.*\\\\Q")
                .replaceAll("\\?", "\\\\E.\\\\Q")
                .replaceAll("\\|", "\\\\E|\\\\Q");
    }

}
