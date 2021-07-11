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
package org.eclipse.ditto.gateway.service.security.authentication.jwt;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.auth.AuthorizationModelFactory;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.jwt.model.JsonWebToken;

/**
 * Default implementation of {@link JwtAuthenticationResultProvider}.
 */
@Immutable
public final class DefaultJwtAuthenticationResultProvider implements JwtAuthenticationResultProvider {

    private final JwtAuthorizationSubjectsProvider authSubjectsProvider;

    private DefaultJwtAuthenticationResultProvider(final JwtAuthorizationSubjectsProvider authSubjectsProvider) {
        this.authSubjectsProvider = authSubjectsProvider;
    }

    /**
     * Creates a new instance of the default JWT context provider with the given authorization subjects provider.
     *
     * @param authorizationSubjectsProvider used to extract authorization subjects from each {@link JsonWebToken JWT}
     * passed to {@link #getAuthenticationResult(JsonWebToken, DittoHeaders)}.
     * @return the created instance.
     * @throws NullPointerException if {@code authorizationSubjectsProvider} is {@code null}.
     */
    public static DefaultJwtAuthenticationResultProvider of(
            final JwtAuthorizationSubjectsProvider authorizationSubjectsProvider) {

        return new DefaultJwtAuthenticationResultProvider(
                checkNotNull(authorizationSubjectsProvider, "authorizationSubjectsProvider"));
    }

    @Override
    public CompletionStage<JwtAuthenticationResult> getAuthenticationResult(final JsonWebToken jwt,
            final DittoHeaders dittoHeaders) {
        final List<AuthorizationSubject> authSubjects = authSubjectsProvider.getAuthorizationSubjects(jwt);
        return CompletableFuture.completedStage(JwtAuthenticationResult.successful(dittoHeaders,
                AuthorizationModelFactory.newAuthContext(DittoAuthorizationContextType.JWT, authSubjects),
                jwt));
    }

}
