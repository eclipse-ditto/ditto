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

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;

/**
 * Abstract implementation of an authentication result that results in an authorization context extending
 * {@link AuthorizationContext}.
 *
 * @param <C> The concrete type of the authorization context that is the actual result of the authentication.
 */
@Immutable
public abstract class AbstractAuthenticationResult<C extends AuthorizationContext> implements AuthenticationResult {

    private final C authorizationContext;
    private final Throwable reasonOfFailure;

    protected AbstractAuthenticationResult(final C authorizationContext,
            final Throwable reasonOfFailure) {

        this.authorizationContext = authorizationContext;
        this.reasonOfFailure = reasonOfFailure;
    }

    /**
     * Indicates whether the authentication was successful or not.
     *
     * @return True if the authentication was successful and false if not.
     */
    public boolean isSuccess() {
        return authorizationContext != null;
    }

    /**
     * Gets a {@link org.eclipse.ditto.model.base.auth.AuthorizationContext} if the authentication
     * {@link #isSuccess() was successful}. Throws the reason of the authentication failure otherwise.
     */
    @Override
    public C getAuthorizationContext() {
        if (authorizationContext == null) {
            if (reasonOfFailure instanceof RuntimeException) {
                throw (RuntimeException) reasonOfFailure;
            } else {
                throw new IllegalStateException(reasonOfFailure);
            }
        }

        return authorizationContext;
    }

    /**
     * Gets the {@link Throwable} which was the reason of failure if the authentication {@link #isSuccess()} wasn't
     * successful. Throws {@link IllegalStateException} otherwise.
     */
    @Override
    public Throwable getReasonOfFailure() {
        if (reasonOfFailure == null) {
            throw new IllegalStateException("Authentication was successful!");
        }

        return reasonOfFailure;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AbstractAuthenticationResult that = (AbstractAuthenticationResult) o;
        return Objects.equals(authorizationContext, that.authorizationContext) &&
                Objects.equals(reasonOfFailure, that.reasonOfFailure);
    }

    @Override
    public int hashCode() {
        return Objects.hash(authorizationContext, reasonOfFailure);
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + " [" +
                "authorizationContext=" + authorizationContext +
                ", reasonOfFailure=" + reasonOfFailure +
                ']';
    }
}
