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

import org.eclipse.ditto.model.base.auth.AuthorizationModelFactory;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.jwt.JsonWebToken;
import org.eclipse.ditto.services.gateway.security.authentication.AuthenticationResult;
import org.eclipse.ditto.services.gateway.security.authentication.DefaultAuthenticationResult;

/**
 * Default implementation of {@link JwtAuthenticationResultProvider}.
 */
@Immutable
public final class DefaultJwtAuthenticationResultProvider implements JwtAuthenticationResultProvider {

    private final JwtAuthorizationSubjectsProvider authorizationSubjectsProvider;

    private DefaultJwtAuthenticationResultProvider(final JwtAuthorizationSubjectsProvider authorizationSubjectsProvider) {
        this.authorizationSubjectsProvider = authorizationSubjectsProvider;
    }

    /**
     * Creates a new instance of the default JWT context provider with the given authorization subjects provider.
     *
     * @param authorizationSubjectsProvider used to extract authorization subjects from each {@link JsonWebToken JWT}
     * passed to {@link #getAuthenticationResult(JsonWebToken,DittoHeaders)}.
     * @return the created instance.
     */
    public static DefaultJwtAuthenticationResultProvider of(
            final JwtAuthorizationSubjectsProvider authorizationSubjectsProvider) {

        return new DefaultJwtAuthenticationResultProvider(authorizationSubjectsProvider);
    }

    @Override
    public AuthenticationResult getAuthenticationResult(final JsonWebToken jwt, final DittoHeaders dittoHeaders) {
        final List<AuthorizationSubject> authSubjects = authorizationSubjectsProvider.getAuthorizationSubjects(jwt);
        return DefaultAuthenticationResult.successful(dittoHeaders,
                AuthorizationModelFactory.newAuthContext(DittoAuthorizationContextType.JWT, authSubjects)
        );
    }

}
