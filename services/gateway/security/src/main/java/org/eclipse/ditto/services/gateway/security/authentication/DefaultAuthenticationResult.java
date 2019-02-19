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
package org.eclipse.ditto.services.gateway.security.authentication;

import static java.util.Objects.requireNonNull;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;

/**
 * Default implementation of {@link AbstractAuthenticationResult}.
 */
@Immutable
public final class DefaultAuthenticationResult extends AbstractAuthenticationResult<AuthorizationContext> {

    private DefaultAuthenticationResult(
            final AuthorizationContext authorizationContext, final Throwable reasonOfFailure) {
        super(authorizationContext, reasonOfFailure);
    }

    /**
     * Initializes a successful authentication result with a found {@link AuthorizationContext}.
     *
     * @param authorizationContext The authorization context found by authentication.
     * @return a successfully completed {@link DefaultAuthenticationResult} containing the
     * {@code given authorizationContext}.
     */
    public static DefaultAuthenticationResult successful(final AuthorizationContext authorizationContext) {
        return new DefaultAuthenticationResult(requireNonNull(authorizationContext), null);
    }

    /**
     * Initializes a result of a failed authentication.
     *
     * @param reasonOfFailure The reason of the authentication failure.
     * @return a failed {@link DefaultAuthenticationResult} containing the {@code given reasonOfFailure}.
     */
    public static DefaultAuthenticationResult failed(final Throwable reasonOfFailure) {
        return new DefaultAuthenticationResult(null, requireNonNull(reasonOfFailure));
    }
}
