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

/**
 * Keeps the value if passed parameters is not empty.
 */
final class ExistsFunction implements FilterFunction {

    @Override
    public String getName() {
        return "exists";
    }

    @Override
    public boolean apply(final String... parameters) {
        if (parameters.length == 1) {
            return apply(parameters[0], true);
        } else if (parameters.length == 2) {
            final String toMatch = parameters[0];
            final boolean shouldExist = Boolean.parseBoolean(parameters[1]);
            return apply(toMatch, shouldExist);
        }
        return false;
    }

    private boolean apply(final String toMatch, final boolean shouldExist) {
        return shouldExist != toMatch.isEmpty();
    }

}
