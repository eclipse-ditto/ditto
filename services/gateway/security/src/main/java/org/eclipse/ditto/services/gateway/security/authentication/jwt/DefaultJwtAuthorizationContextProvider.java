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
package org.eclipse.ditto.services.gateway.security.authentication.jwt;

import java.util.List;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationModelFactory;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;

/**
 * Default implementation of {@link JwtAuthorizationContextProvider}.
 */
@Immutable
public final class DefaultJwtAuthorizationContextProvider implements JwtAuthorizationContextProvider {

    private final AuthorizationSubjectsProvider authorizationSubjectsProvider;

    private DefaultJwtAuthorizationContextProvider(final AuthorizationSubjectsProvider authorizationSubjectsProvider) {
        this.authorizationSubjectsProvider = authorizationSubjectsProvider;
    }

    /**
     * Creates a new instance of {@link DefaultJwtAuthorizationContextProvider} with the given authorization subjects
     * provider.
     *
     * @param authorizationSubjectsProvider used to extract authorization subjects from each {@link JsonWebToken JWT}
     * passed to {@link #getAuthorizationContext(JsonWebToken)}.
     * @return the created instance.
     */
    public static DefaultJwtAuthorizationContextProvider getInstance(
            final AuthorizationSubjectsProvider authorizationSubjectsProvider) {
        return new DefaultJwtAuthorizationContextProvider(authorizationSubjectsProvider);
    }

    /**
     * Extracts an {@link AuthorizationContext authorization context} out of a given
     * {@link JsonWebToken json web token}.
     *
     * @param jwt the json web token that contains the information to be extracted into an authorization context.
     * @return the authorization context based on the given json web token.
     */
    @Override
    public AuthorizationContext getAuthorizationContext(final JsonWebToken jwt) {
        final List<AuthorizationSubject> authSubjects = authorizationSubjectsProvider.getAuthorizationSubjects(jwt);
        return AuthorizationModelFactory.newAuthContext(authSubjects);
    }
}
