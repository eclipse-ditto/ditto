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
package org.eclipse.ditto.placeholders;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.ConditionChecker;

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
    public List<String> resolveValues(final Map<String, String> headers, final String headerKey) {
        ConditionChecker.argumentNotEmpty(headerKey, "headerKey");
        ConditionChecker.argumentNotNull(headers, "headers");
        return Optional.ofNullable(headers.get(headerKey))
                .map(Collections::singletonList)
                .orElseGet(Collections::emptyList);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[]";
    }
}
