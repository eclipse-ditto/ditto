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
package org.eclipse.ditto.edge.service.placeholders;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;

/**
 * Placeholder implementation that replaces {@code request} related things based on an {@link AuthorizationContext}.
 */
@Immutable
final class ImmutableRequestPlaceholder implements RequestPlaceholder {

    /**
     * Singleton instance of the ImmutableHeadersPlaceholder.
     */
    static final ImmutableRequestPlaceholder INSTANCE = new ImmutableRequestPlaceholder();

    private static final String PREFIX = "request";

    private static final List<String> SUPPORTED_NAMES = Collections.singletonList("subjectId");

    private ImmutableRequestPlaceholder() {
    }

    @Override
    public String getPrefix() {
        return PREFIX;
    }

    @Override
    public List<String> getSupportedNames() {
        return SUPPORTED_NAMES;
    }

    @Override
    public boolean supports(final String name) {
        return SUPPORTED_NAMES.contains(name);
    }

    @Override
    public List<String> resolveValues(@Nullable final AuthorizationContext authorizationContext,
            final String headerKey) {
        // precondition: supports(headerKey)
        return Optional.ofNullable(authorizationContext)
                .map(context -> context.getAuthorizationSubjects().stream()
                        .map(AuthorizationSubject::getId)
                        .toList())
                .orElseGet(Collections::emptyList);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[]";
    }
}
