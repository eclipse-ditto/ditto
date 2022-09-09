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
package org.eclipse.ditto.gateway.service.security.authentication;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.headers.DittoHeaders;

/**
 * Abstract implementation of an authentication result that bears an {@link org.eclipse.ditto.base.model.auth.AuthorizationContext}.
 */
@NotThreadSafe
public abstract class AbstractAuthenticationResult implements AuthenticationResult {

    private final DittoHeaders dittoHeaders;
    @Nullable private final AuthorizationContext authorizationContext;
    @Nullable private final Throwable reasonOfFailure;

    /**
     * Construct an authentication result.
     *
     * @param dittoHeaders the Ditto headers without authorization context.
     * @param authorizationContext the authorization context.
     * @param reasonOfFailure the reason for authentication failure or null if authentication is successful.
     */
    protected AbstractAuthenticationResult(final DittoHeaders dittoHeaders,
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

    @Override
    public boolean isSuccess() {
        return null != authorizationContext;
    }

    @Override
    public AuthorizationContext getAuthorizationContext() {
        if (null == authorizationContext) {
            if (reasonOfFailure instanceof RuntimeException runtimeException) {
                throw runtimeException;
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
        final AbstractAuthenticationResult that = (AbstractAuthenticationResult) o;
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
        return "dittoHeaders=" + dittoHeaders +
                ", authorizationContext=" + authorizationContext +
                ", reasonOfFailure=" + reasonOfFailure;
    }

}
