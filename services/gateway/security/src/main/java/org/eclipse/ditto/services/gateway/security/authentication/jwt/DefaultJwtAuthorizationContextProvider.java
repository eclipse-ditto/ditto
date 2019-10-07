/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.gateway.security.authentication.jwt;

import java.util.List;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationModelFactory;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.jwt.JsonWebToken;

/**
 * Default implementation of {@link JwtAuthorizationContextProvider}.
 */
@Immutable
public final class DefaultJwtAuthorizationContextProvider implements JwtAuthorizationContextProvider {

    private final JwtAuthorizationSubjectsProvider authorizationSubjectsProvider;

    private DefaultJwtAuthorizationContextProvider(final JwtAuthorizationSubjectsProvider authorizationSubjectsProvider) {
        this.authorizationSubjectsProvider = authorizationSubjectsProvider;
    }

    /**
     * Creates a new instance of the default JWT context provider with the given authorization subjects provider.
     *
     * @param authorizationSubjectsProvider used to extract authorization subjects from each {@link JsonWebToken JWT}
     * passed to {@link #getAuthorizationContext(JsonWebToken)}.
     * @return the created instance.
     */
    public static DefaultJwtAuthorizationContextProvider getInstance(
            final JwtAuthorizationSubjectsProvider authorizationSubjectsProvider) {

        return new DefaultJwtAuthorizationContextProvider(authorizationSubjectsProvider);
    }

    /**
     * Extracts an {@link AuthorizationContext authorization context} out of a given
     * {@link JsonWebToken JSON web token}.
     *
     * @param jwt the JSON web token that contains the information to be extracted into an authorization context.
     * @return the authorization context based on the given JSON web token.
     * @throws NullPointerException if {@code jwt} is {@code null}.
     */
    @Override
    public AuthorizationContext getAuthorizationContext(final JsonWebToken jwt) {
        final List<AuthorizationSubject> authSubjects = authorizationSubjectsProvider.getAuthorizationSubjects(jwt);
        return AuthorizationModelFactory.newAuthContext(authSubjects);
    }

}
