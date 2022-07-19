/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.service.signaltransformer.placeholdersubstitution;

import static java.util.Objects.requireNonNull;

import java.util.function.Function;

import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.headers.DittoHeaders;

/**
 * Replaces the placeholder {@link #REPLACER_NAME} with the first AuthorizationSubject in the headers'
 * AuthorizationContext.
 */
public final class SubjectIdReplacementDefinition implements Function<DittoHeaders, String> {

    /**
     * The name of the replacer.
     */
    public static final String REPLACER_NAME = "request:subjectId";

    /**
     * The legacy name of the replacer which still is supported.
     */
    public static final String LEGACY_REPLACER_NAME = "request.subjectId";

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
