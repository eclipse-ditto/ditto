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

import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.jwt.model.JsonWebToken;
import org.eclipse.ditto.gateway.service.security.authentication.AbstractAuthenticationResult;

/**
 * Implementation of {@link JwtAuthenticationResult}.
 */
final class DefaultJwtAuthenticationResult extends AbstractAuthenticationResult implements JwtAuthenticationResult {

    @Nullable
    private final JsonWebToken jwt;

    DefaultJwtAuthenticationResult(final DittoHeaders dittoHeaders,
            @Nullable final AuthorizationContext authorizationContext,
            @Nullable final Throwable reasonOfFailure,
            @Nullable final JsonWebToken jwt) {
        super(dittoHeaders, authorizationContext, reasonOfFailure);
        this.jwt = jwt;
    }

    @Override
    public Optional<JsonWebToken> getJwt() {
        return Optional.ofNullable(jwt);
    }

    @Override
    public boolean equals(final Object that) {
        return super.equals(that) && Objects.equals(jwt, ((DefaultJwtAuthenticationResult) that).jwt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), jwt);
    }
}
