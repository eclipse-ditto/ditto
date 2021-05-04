/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

import java.util.Optional;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.jwt.model.JsonWebToken;
import org.eclipse.ditto.gateway.service.security.authentication.AuthenticationResult;

/**
 * The result of a JWT authentication.
 */
public interface JwtAuthenticationResult extends AuthenticationResult {

    /**
     * Retrieve the parsed json web token, or an empty optional if parsing failed.
     * Guaranteed to be nonempty if the authentication result is successful.
     *
     * @return the JWT.
     */
    Optional<JsonWebToken> getJwt();

    /**
     * Initializes a successful authentication result with a JWT.
     *
     * @param dittoHeaders the DittoHeaders of the succeeded authentication result.
     * @param authorizationContext the authorization context found by authentication.
     * @param jwt the parsed jwt.
     * @return a successfully completed authentication result containing the {@code given authorizationContext}.
     * @throws NullPointerException if any argument is {@code null}.
     */
    static JwtAuthenticationResult successful(final DittoHeaders dittoHeaders,
            final AuthorizationContext authorizationContext,
            final JsonWebToken jwt) {

        return new DefaultJwtAuthenticationResult(dittoHeaders,
                checkNotNull(authorizationContext, "authorizationContext"),
                null,
                checkNotNull(jwt, "jwt"));
    }

    /**
     * Initializes a result of a failed JWT authentication.
     *
     * @param dittoHeaders the DittoHeaders of the failed authentication result.
     * @param reasonOfFailure the reason of the authentication failure.
     * @return a failed authentication result containing the {@code given reasonOfFailure}.
     * @throws NullPointerException if any argument is {@code null}.
     */
    static JwtAuthenticationResult failed(final DittoHeaders dittoHeaders, final Throwable reasonOfFailure) {
        return new DefaultJwtAuthenticationResult(dittoHeaders, null, checkNotNull(reasonOfFailure, "reasonOfFailure"),
                null);
    }

}
