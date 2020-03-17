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
package org.eclipse.ditto.services.gateway.security.authentication;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.headers.DittoHeaders;

/**
 * Default implementation of {@link AbstractAuthenticationResult}.
 */
@NotThreadSafe
public final class DefaultAuthenticationResult extends AbstractAuthenticationResult<AuthorizationContext> {

    private DefaultAuthenticationResult(final DittoHeaders dittoHeaders,
            @Nullable final AuthorizationContext authorizationContext,
            @Nullable final Throwable reasonOfFailure) {

        super(dittoHeaders, authorizationContext, reasonOfFailure);
    }

    /**
     * Initializes a successful authentication result with a found {@link AuthorizationContext}.
     *
     * @param dittoHeaders the DittoHeaders of the succeeded authentication result.
     * @param authorizationContext the authorization context found by authentication.
     * @return a successfully completed authentication result containing the {@code given authorizationContext}.
     * @throws NullPointerException if {@code authorizationContext} is {@code null}.
     */
    public static AuthenticationResult successful(final DittoHeaders dittoHeaders,
            final AuthorizationContext authorizationContext) {
        return new DefaultAuthenticationResult(dittoHeaders,
                checkNotNull(authorizationContext, "AuthorizationContext"), null);
    }

    /**
     * Initializes a result of a failed authentication.
     *
     * @param dittoHeaders the DittoHeaders of the failed authentication result.
     * @param reasonOfFailure the reason of the authentication failure.
     * @return a failed authentication result containing the {@code given reasonOfFailure}.
     * @throws NullPointerException if {@code reasonOfFailure} is {@code null}.
     */
    public static AuthenticationResult failed(final DittoHeaders dittoHeaders, final Throwable reasonOfFailure) {
        return new DefaultAuthenticationResult(dittoHeaders,null,
                checkNotNull(reasonOfFailure, "reason of failure"));
    }

}
