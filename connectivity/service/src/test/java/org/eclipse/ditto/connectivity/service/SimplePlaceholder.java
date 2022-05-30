/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service;

import java.util.Collections;
import java.util.List;

import org.eclipse.ditto.placeholders.Placeholder;
import org.eclipse.ditto.utils.jsr305.annotations.AllParametersAndReturnValuesAreNonnullByDefault;

/**
 * Simple placeholder for test purposes.
 */
@AllParametersAndReturnValuesAreNonnullByDefault
class SimplePlaceholder implements Placeholder<String> {

    static final SimplePlaceholder INSTANCE = new SimplePlaceholder();

    private static final String PLACEHOLDER = "placeholder";

    private SimplePlaceholder() {
    }

    @Override
    public String getPrefix() {
        return "test";
    }

    @Override
    public List<String> getSupportedNames() {
        return Collections.singletonList(PLACEHOLDER);
    }

    @Override
    public boolean supports(final String name) {
        return PLACEHOLDER.equalsIgnoreCase(name);
    }

    @Override
    public List<String> resolveValues(final String placeholderSource, final String name) {
        return supports(name) ? Collections.singletonList(placeholderSource) : Collections.emptyList();
    }
}
