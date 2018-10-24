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
package org.eclipse.ditto.services.models.connectivity.placeholder;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.common.ConditionChecker;

/**
 * Placeholder implementation that replaces {@code header:*} from a headers map of {@code Map<String, String>}.
 */
@Immutable
final class ImmutableHeadersPlaceholder implements HeadersPlaceholder {

    /**
     * Singleton instance of the ImmutableHeadersPlaceholder.
     */
    static final ImmutableHeadersPlaceholder INSTANCE = new ImmutableHeadersPlaceholder();

    private static final String PREFIX = "header";

    private ImmutableHeadersPlaceholder() {
    }

    @Override
    public String getPrefix() {
        return PREFIX;
    }

    @Override
    public List<String> getSupportedNames() {
        return Collections.emptyList();
    }

    @Override
    public boolean supports(final String name) {
        return true;
    }

    @Override
    public Optional<String> apply(final Map<String, String> headers, final String headerKey) {
        ConditionChecker.argumentNotEmpty(headerKey, "headerKey");
        ConditionChecker.argumentNotNull(headers, "headers");
        return Optional.ofNullable(headers.get(headerKey));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[]";
    }
}
