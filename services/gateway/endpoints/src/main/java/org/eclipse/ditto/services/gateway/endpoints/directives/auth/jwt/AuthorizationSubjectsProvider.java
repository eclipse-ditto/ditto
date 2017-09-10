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
package org.eclipse.ditto.services.gateway.endpoints.directives.auth.jwt;

import static org.eclipse.ditto.model.base.common.ConditionChecker.argumentNotNull;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.policies.SubjectIssuer;
import org.eclipse.ditto.services.gateway.security.jwt.JsonWebToken;

/**
 * A provider for {@link AuthorizationSubject}s contained in a {@link JsonWebToken}.
 */
@Immutable
public final class AuthorizationSubjectsProvider {

    private final List<AuthorizationSubject> authorizationSubjects;

    private AuthorizationSubjectsProvider(final List<AuthorizationSubject> authorizationSubjectsWithPrefixes) {
        this.authorizationSubjects = Collections.unmodifiableList(authorizationSubjectsWithPrefixes);
    }

    /**
     * Returns a new {@code AuthorizationSubjectsProvider} for the given {@code issuer} and {@code jsonWebToken}.
     *
     * @param issuer the issuer.
     * @param jsonWebToken the token.
     * @return the AuthorizationSubjectsProvider.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static AuthorizationSubjectsProvider of(final SubjectIssuer issuer, final JsonWebToken jsonWebToken) {
        argumentNotNull(issuer);
        argumentNotNull(jsonWebToken);

        final List<AuthorizationSubject> authorizationSubjects = jsonWebToken.getAuthorizationSubjects();
        return new AuthorizationSubjectsProvider(authorizationSubjects);
    }

    /**
     * Returns the AuthorizationSubjects with prefixes for the SubjectIssuer.
     *
     * @return the subjects.
     */
    List<AuthorizationSubject> getAuthorizationSubjects() {
        return authorizationSubjects;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AuthorizationSubjectsProvider that = (AuthorizationSubjectsProvider) o;
        return Objects.equals(authorizationSubjects, that.authorizationSubjects);
    }

    @Override
    public int hashCode() {
        return Objects.hash(authorizationSubjects);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" + "authorizationSubjects=" + authorizationSubjects + ']';
    }

}
