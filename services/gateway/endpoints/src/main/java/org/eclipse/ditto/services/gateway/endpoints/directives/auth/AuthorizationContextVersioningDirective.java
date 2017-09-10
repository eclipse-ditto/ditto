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
package org.eclipse.ditto.services.gateway.endpoints.directives.auth;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationModelFactory;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.services.gateway.endpoints.utils.DirectivesLoggingUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.http.javadsl.server.Route;

/**
 * Custom Akka HTTP Directive which adjusts a given {@link AuthorizationContext} with prefixed subjects for the
 * requested API version.
 */
public final class AuthorizationContextVersioningDirective {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthorizationContextVersioningDirective.class);

    private AuthorizationContextVersioningDirective() {
        // no op
    }

    /**
     * Adapts an {@link AuthorizationContext} with prefixed subjects for use in the requested API version.
     *
     * @param correlationId the correlationId (used for logging)
     * @param apiVersion the API version from the URI (e.g. from "/api/1" it would be "1")
     * @param authContextWithPrefixedSubjects an {@link AuthorizationContext} with prefixed subjects
     * @param inner the inner Route to wrap with the mapped {@link AuthorizationContext}
     * @return the new Route the mapped authentication {@link AuthorizationContext}
     */
    public static Route mapAuthorizationContext(final String correlationId, final int apiVersion,
            final AuthorizationContext authContextWithPrefixedSubjects,
            final Function<AuthorizationContext, Route> inner) {
        return DirectivesLoggingUtils.enhanceLogWithCorrelationId(correlationId, () -> {
            LOGGER.debug("Original authorization context: {}", authContextWithPrefixedSubjects);

            final AuthorizationContext mappedAuthorizationContext =
                    mapAuthorizationContext(authContextWithPrefixedSubjects, apiVersion);
            LOGGER.debug("Mapped authorization context: {}", mappedAuthorizationContext);

            return inner.apply(mappedAuthorizationContext);
        });
    }

    private static AuthorizationContext mapAuthorizationContext(
            final AuthorizationContext authenticationContextWithPrefixedSubjects, final int version) {
        final List<AuthorizationSubject> subjects = new ArrayList<>();
        final List<AuthorizationSubject> subjectsWithPrefix = authenticationContextWithPrefixedSubjects
                .getAuthorizationSubjects();
        final List<AuthorizationSubject> subjectsWithoutPrefix =
                subjectsWithPrefix.stream().map(AuthorizationSubject::getId)
                        .map(SubjectId::newInstance)
                        .map(SubjectId::getSubject)
                        .map(AuthorizationModelFactory::newAuthSubject)
                        .collect(Collectors.toList());

        if (version == 1) {
            /* while we still support authorization subjects in "old" V1, we must enhance the List of
               AuthorizationSubjects by PREPENDING AuthorizationSubjects without prefix - because the first
               AuthorizationSubject is used to create a default ACL
            */

            subjects.addAll(subjectsWithoutPrefix);
            subjects.addAll(subjectsWithPrefix);
        } else {
            /* for V2 and above, we must enhance the List of
               AuthorizationSubjects by APPENDING AuthorizationSubjects without prefix - because the first
               AuthorizationSubject is used to create a default ACL
            */

            subjects.addAll(subjectsWithPrefix);
            subjects.addAll(subjectsWithoutPrefix);
        }

        return AuthorizationModelFactory.newAuthContext(subjects);
    }

}
