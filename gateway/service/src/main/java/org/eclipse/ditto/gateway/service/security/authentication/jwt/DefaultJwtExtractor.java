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

import java.util.Optional;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.jwt.model.ImmutableJsonWebToken;
import org.eclipse.ditto.jwt.model.JsonWebToken;
import org.eclipse.ditto.gateway.service.security.HttpHeader;
import org.eclipse.ditto.gateway.service.security.utils.HttpUtils;

import akka.http.javadsl.server.RequestContext;

/**
 * Default implementation of {@link JwtExtractor}
 * extracting the JWT from the Authorization header.
 */
final class DefaultJwtExtractor implements JwtExtractor {

    private static final String AUTHORIZATION_JWT = "Bearer";
    private static final DefaultJwtExtractor INSTANCE = new DefaultJwtExtractor();

    private DefaultJwtExtractor() {
    }

    public static JwtExtractor getInstance() {
        return INSTANCE;
    }

    @Override
    public Optional<JsonWebToken> apply(final RequestContext requestContext, final DittoHeaders dittoHeaders) {
        return HttpUtils.getRequestHeader(requestContext, HttpHeader.AUTHORIZATION.toString())
                .map(ImmutableJsonWebToken::fromAuthorization);
    }

    @Override
    public boolean isApplicable(final RequestContext requestContext) {
        return HttpUtils.containsAuthorizationForPrefix(requestContext, AUTHORIZATION_JWT);
    }

    @Override
    public String getErrorDescription() {
        return "Please provide a valid JWT in the authorization header prefixed with 'Bearer '.";
    }
}
