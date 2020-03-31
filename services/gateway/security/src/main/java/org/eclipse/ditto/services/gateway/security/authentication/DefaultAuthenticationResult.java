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

import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.headers.DittoHeaders;

/**
 * Default implementation of an authentication result that bears an {@link AuthorizationContext}.
 */
@NotThreadSafe
public final class DefaultAuthenticationResult implements AuthenticationResult {

    private final DittoHeaders dittoHeaders;
    @Nullable private final AuthorizationContext authorizationContext;
    @Nullable private final Throwable reasonOfFailure;

    private DefaultAuthenticationResult(final DittoHeaders dittoHeaders,
            @Nullable final AuthorizationContext authorizationContext, @Nullable final Throwable reasonOfFailure) {

        checkNotNull(dittoHeaders, "dittoHeaders");
        if (null != authorizationContext) {
            // merge present authorizationContext into dittoHeaders:
            this.dittoHeaders = dittoHeaders.toBuilder()
                    .authorizationContext(authorizationContext)
                    .build();
        } else {
            this.dittoHeaders = dittoHeaders;
        }
        this.authorizationContext = authorizationContext;
        this.reasonOfFailure = reasonOfFailure;
    }

    /**
     * Initializes a successful authentication result with a found {@link AuthorizationContext}.
     *
     * @param dittoHeaders the DittoHeaders of the succeeded authentication result.
     * @param authorizationContext the authorization context found by authentication.
     * @return a successfully completed authentication result containing the {@code given authorizationContext}.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static AuthenticationResult successful(final DittoHeaders dittoHeaders,
            final AuthorizationContext authorizationContext) {

        return new DefaultAuthenticationResult(dittoHeaders,
                checkNotNull(authorizationContext, "authorizationContext"), null);
    }

    /**
     * Initializes a result of a failed authentication.
     *
     * @param dittoHeaders the DittoHeaders of the failed authentication result.
     * @param reasonOfFailure the reason of the authentication failure.
     * @return a failed authentication result containing the {@code given reasonOfFailure}.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static AuthenticationResult failed(final DittoHeaders dittoHeaders, final Throwable reasonOfFailure) {
        return new DefaultAuthenticationResult(dittoHeaders, null, checkNotNull(reasonOfFailure, "reasonOfFailure"));
    }

    @Override
    public boolean isSuccess() {
        return null != authorizationContext;
    }

    @Override
    public AuthorizationContext getAuthorizationContext() {
        if (null == authorizationContext) {
            if (reasonOfFailure instanceof RuntimeException) {
                throw (RuntimeException) reasonOfFailure;
            }
            throw new IllegalStateException(reasonOfFailure);
        }

        return authorizationContext;
    }

    @Override
    public DittoHeaders getDittoHeaders() {
        return dittoHeaders;
    }

    @Override
    public Throwable getReasonOfFailure() {
        if (null == reasonOfFailure) {
            throw new IllegalStateException("Authentication was successful!");
        }

        return reasonOfFailure;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultAuthenticationResult that = (DefaultAuthenticationResult) o;
        return Objects.equals(dittoHeaders, that.dittoHeaders) &&
                Objects.equals(authorizationContext, that.authorizationContext) &&
                Objects.equals(reasonOfFailure, that.reasonOfFailure);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dittoHeaders, authorizationContext, reasonOfFailure);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "dittoHeaders=" + dittoHeaders +
                ", authorizationContext=" + authorizationContext +
                ", reasonOfFailure=" + reasonOfFailure +
                "]";
    }

}
