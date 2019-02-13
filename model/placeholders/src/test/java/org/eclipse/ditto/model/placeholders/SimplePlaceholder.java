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
package org.eclipse.ditto.model.placeholders;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Simple placeholder for test purposes.
 */
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
    public Optional<String> resolve(final String placeholderSource, final String name) {
        return supports(name) ? Optional.of(placeholderSource) : Optional.empty();
    }
}
