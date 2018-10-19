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
package org.eclipse.ditto.services.concierge.enforcement.placeholders;

import static java.util.Objects.requireNonNull;

import java.util.function.Function;

import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;

/**
 * Replaces the placeholder {@link #REPLACER_NAME} with the first AuthorizationSubject in the headers'
 * AuthorizationContext.
 */
final class SubjectIdReplacementDefinition implements Function<DittoHeaders, String> {

    /**
     * The name of the replacer.
     */
    public static final String REPLACER_NAME = "request:subjectId";

    private static final SubjectIdReplacementDefinition INSTANCE = new SubjectIdReplacementDefinition();

    private SubjectIdReplacementDefinition() {
        // no-op
    }

    /**
     * Returns the single instance of this function.
     *
     * @return the instance.
     */
    public static SubjectIdReplacementDefinition getInstance() {
        return INSTANCE;
    }

    @Override
    public String apply(final DittoHeaders dittoHeaders) {
        requireNonNull(dittoHeaders);

        return dittoHeaders.getAuthorizationContext().getFirstAuthorizationSubject()
                .map(AuthorizationSubject::getId)
                .orElseThrow(() -> new IllegalStateException("AuthorizationContext must be available when this " +
                        "function is applied!"));
    }
}
