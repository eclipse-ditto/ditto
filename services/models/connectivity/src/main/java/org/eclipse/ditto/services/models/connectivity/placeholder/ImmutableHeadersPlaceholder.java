/*
 *  Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v2.0
 *  which accompanies this distribution, and is available at
 *  https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 *  SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.models.connectivity.placeholder;

import java.util.Map;
import java.util.Optional;

import org.eclipse.ditto.model.base.common.ConditionChecker;


/**
 * Placeholder implementation that replaces {@code header:*} from a headers map of {@code Map<String, String>}.
 */
final class ImmutableHeadersPlaceholder implements HeadersPlaceholder {

    private static final String PREFIX = "header";
    static final ImmutableHeadersPlaceholder INSTANCE = new ImmutableHeadersPlaceholder();

    private ImmutableHeadersPlaceholder() {
    }

    @Override
    public String getPrefix() {
        return PREFIX;
    }

    @Override
    public boolean supports(final String name) {
        return true;
    }

    @Override
    public Optional<String> apply(final Map<String, String> headers, final String header) {
        ConditionChecker.argumentNotEmpty(header, "header");
        ConditionChecker.argumentNotNull(headers, "headers");
        return Optional.ofNullable(headers.get(header));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[]";
    }
}
