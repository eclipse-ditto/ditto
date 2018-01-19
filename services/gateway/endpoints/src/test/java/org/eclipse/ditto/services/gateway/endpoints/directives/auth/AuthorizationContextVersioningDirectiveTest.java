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

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.services.gateway.endpoints.EndpointTestConstants.KNOWN_CORRELATION_ID;
import static org.eclipse.ditto.services.gateway.endpoints.directives.auth.AuthorizationContextVersioningDirective.mapAuthorizationContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationModelFactory;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.policies.SubjectId;
import org.eclipse.ditto.model.policies.SubjectIssuer;
import org.eclipse.ditto.services.gateway.endpoints.EndpointTestBase;
import org.junit.Test;

import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.server.Route;
import akka.http.javadsl.testkit.TestRoute;
import akka.http.javadsl.testkit.TestRouteResult;


/**
 * Tests {@link AuthorizationContextVersioningDirective}.
 */
public class AuthorizationContextVersioningDirectiveTest extends EndpointTestBase {

    private static final String PATH = "/";

    @Test
    public void subjectIdsWithoutPrefixArePrependedForV1() {
        final AuthorizationContext authContextWithPrefixedSubjects = createAuthContextWithPrefixedSubjects();
        final List<AuthorizationSubject> expectedAuthorizationSubjects = new ArrayList<>();
        expectedAuthorizationSubjects.addAll(createAuthSubjectsWithoutPrefixes());
        expectedAuthorizationSubjects.addAll(authContextWithPrefixedSubjects.getAuthorizationSubjects());
        final AuthorizationContext expectedAuthorizationContext =
                AuthorizationModelFactory.newAuthContext(expectedAuthorizationSubjects);
        assertMapping(1, authContextWithPrefixedSubjects, expectedAuthorizationContext);
    }

    @Test
    public void subjectIdsWithoutPrefixAreAppendedForV2() {
        final AuthorizationContext authContextWithPrefixedSubjects = createAuthContextWithPrefixedSubjects();
        final List<AuthorizationSubject> expectedAuthorizationSubjects = new ArrayList<>();
        expectedAuthorizationSubjects.addAll(authContextWithPrefixedSubjects.getAuthorizationSubjects());
        expectedAuthorizationSubjects.addAll(createAuthSubjectsWithoutPrefixes());
        final AuthorizationContext expectedAuthorizationContext =
                AuthorizationModelFactory.newAuthContext(expectedAuthorizationSubjects);
        assertMapping(2, authContextWithPrefixedSubjects, expectedAuthorizationContext);
    }

    private void assertMapping(final int apiVersion, final AuthorizationContext authContextWithPrefixedSubjects,
            final AuthorizationContext expectedAuthorizationContext) {
        final int expectedStatusCode = 200;
        final Route root = route(get(
                () -> complete(HttpResponse.create().withEntity(DEFAULT_DUMMY_ENTITY).withStatus(expectedStatusCode))));
        final AtomicReference<AuthorizationContext> mappedRef = new AtomicReference<>();
        final Route wrappedRoute = mapAuthorizationContext(KNOWN_CORRELATION_ID, apiVersion,
                authContextWithPrefixedSubjects,
                mappedAuthContext -> {
                    mappedRef.set(mappedAuthContext);
                    return root;
                });
        final TestRoute testRoute = testRoute(wrappedRoute);
        final TestRouteResult result = testRoute.run(HttpRequest.GET(PATH));

        assertThat(mappedRef.get()).isEqualTo(expectedAuthorizationContext);

        result.assertStatusCode(expectedStatusCode);
        result.assertEntity(DEFAULT_DUMMY_ENTITY);
    }

    private static List<AuthorizationSubject> createAuthSubjectsWithoutPrefixes() {
        return Collections.singletonList(
                AuthorizationSubject.newInstance(createTestSubjectIdWithoutIssuerPrefix(SubjectIssuer.GOOGLE)));
    }

    private static AuthorizationContext createAuthContextWithPrefixedSubjects() {
        final Iterable<AuthorizationSubject> authorizationSubjects = createPrefixedAuthSubjectsForAllIssuers();
        return AuthorizationModelFactory.newAuthContext(authorizationSubjects);
    }

    private static Iterable<AuthorizationSubject> createPrefixedAuthSubjectsForAllIssuers() {
        return Stream.of(SubjectIssuer.GOOGLE)
                .map(issuer -> SubjectId.newInstance(issuer, createTestSubjectIdWithoutIssuerPrefix(issuer)))
                .map(AuthorizationModelFactory::newAuthSubject)
                .collect(Collectors.toList());
    }

    private static String createTestSubjectIdWithoutIssuerPrefix(final SubjectIssuer issuer) {
        return "test-subject-" + issuer;
    }

}
