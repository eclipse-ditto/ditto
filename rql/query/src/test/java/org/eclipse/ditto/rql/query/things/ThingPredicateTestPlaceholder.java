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
package org.eclipse.ditto.rql.query.things;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.eclipse.ditto.placeholders.Placeholder;
import org.eclipse.ditto.utils.jsr305.annotations.AllParametersAndReturnValuesAreNonnullByDefault;

@AllParametersAndReturnValuesAreNonnullByDefault
final class ThingPredicateTestPlaceholder implements Placeholder<String> {

    private static final String UPPER = "upper";
    private static final String LOWER = "lower";

    @Override
    public String getPrefix() {
        return "test";
    }

    @Override
    public List<String> getSupportedNames() {
        return Arrays.asList(UPPER, LOWER);
    }

    @Override
    public boolean supports(final String name) {
        return name.equals(UPPER) || name.equals(LOWER);
    }

    @Override
    public List<String> resolveValues(final String placeholderSource, final String name) {
        if (LOWER.equals(name)) {
            return Collections.singletonList(placeholderSource.toLowerCase());
        } else {
            return Collections.singletonList(placeholderSource.toUpperCase());
        }
    }

}
