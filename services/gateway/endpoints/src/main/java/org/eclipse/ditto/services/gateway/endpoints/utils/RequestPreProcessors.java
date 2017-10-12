/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.gateway.endpoints.utils;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;

/**
 * Containing static HTTP request manipulating methods which preprocess for example the body.
 */
public final class RequestPreProcessors {

    private static final String PLACEHOLDER_REQUEST_SUBJECTID = "${request.subjectId}";

    private RequestPreProcessors() {
        throw new AssertionError();
    }

    /**
     * Replaces all placeholders occurring in the passed {@code stringToSubstitutePlaceholdersIn} with values from the
     * passed {@code dittoHeaders}. <p> Currently these are: <ul> <li>{@value PLACEHOLDER_REQUEST_SUBJECTID}: replaced
     * by the first authorization subject contained in the {@link AuthorizationContext} of the passed {@code
     * dittoHeaders}</li> </ul>
     */
    public static String replacePlaceholders(final String stringToSubstitutePlaceholdersIn,
            final DittoHeaders dittoHeaders) {

        if (stringToSubstitutePlaceholdersIn.contains(PLACEHOLDER_REQUEST_SUBJECTID)) {
            final AuthorizationContext authorizationContext = dittoHeaders.getAuthorizationContext();
            final String replacedThisSubjectId = authorizationContext.getFirstAuthorizationSubject()
                    .map(AuthorizationSubject::getId)
                    .orElse("unknown");
            return stringToSubstitutePlaceholdersIn.replace(PLACEHOLDER_REQUEST_SUBJECTID, replacedThisSubjectId);
        } else {
            return stringToSubstitutePlaceholdersIn;
        }
    }

}
