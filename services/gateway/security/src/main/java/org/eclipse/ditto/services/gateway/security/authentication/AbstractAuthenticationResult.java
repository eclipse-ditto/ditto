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

import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;

/**
 * Abstract implementation of an authentication result that results in an authorization context extending
 * {@link AuthorizationContext}.
 *
 * @param <C> the type of the authorization context that is the actual result of the authentication.
 */
@NotThreadSafe
public abstract class AbstractAuthenticationResult<C extends AuthorizationContext> implements AuthenticationResult {

    @Nullable private final C authorizationContext;
    @Nullable private final Throwable reasonOfFailure;

    protected AbstractAuthenticationResult(@Nullable final C authorizationContext,
            @Nullable final Throwable reasonOfFailure) {

        this.authorizationContext = authorizationContext;
        this.reasonOfFailure = reasonOfFailure;
    }

    @Override
    public boolean isSuccess() {
        return null != authorizationContext;
    }

    @Override
    public C getAuthorizationContext() {
        if (null == authorizationContext) {
            if (reasonOfFailure instanceof RuntimeException) {
                throw (RuntimeException) reasonOfFailure;
            }
            throw new IllegalStateException(reasonOfFailure);
        }

        return authorizationContext;
    }

    @Override
    public Throwable getReasonOfFailure() {
        if (null == reasonOfFailure) {
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
        return getClass().getSimpleName() + " [" +
                "authorizationContext=" + authorizationContext +
                ", reasonOfFailure=" + reasonOfFailure +
                ']';
    }

}
