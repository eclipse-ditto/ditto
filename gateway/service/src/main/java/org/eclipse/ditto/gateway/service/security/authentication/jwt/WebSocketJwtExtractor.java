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

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.gateway.api.GatewayAuthenticationFailedException;
import org.eclipse.ditto.gateway.service.security.utils.HttpUtils;
import org.eclipse.ditto.jwt.model.ImmutableJsonWebToken;
import org.eclipse.ditto.jwt.model.JsonWebToken;

import akka.http.javadsl.server.RequestContext;

/**
 * Implementation of {@link JwtExtractor} extracting
 * the JWT from the {@code access_token} query parameter.
 */
final class WebSocketJwtExtractor implements JwtExtractor {

    private static final String ACCESS_TOKEN_PARAM = "access_token";
    private static final WebSocketJwtExtractor INSTANCE = new WebSocketJwtExtractor();

    private final JwtExtractor defaultExtractor = DefaultJwtExtractor.getInstance();

    private WebSocketJwtExtractor() {
    }

    /**
     * @return the singleton instance of WebSocketJwtExtractor
     */
    static JwtExtractor getInstance() {
        return INSTANCE;
    }

    @Override
    public Optional<JsonWebToken> apply(final RequestContext requestContext, final DittoHeaders dittoHeaders) {
        final Optional<JsonWebToken> jwtFromHeader = defaultExtractor.apply(requestContext, dittoHeaders);
        final Optional<JsonWebToken> jwtFromParameter = extractJwtFromParameter(requestContext);

        if (jwtFromHeader.isPresent() && jwtFromParameter.isPresent()) {
            throw buildMultipleJwtException(dittoHeaders);
        }

        return jwtFromHeader.or(() -> jwtFromParameter);
    }

    private Optional<JsonWebToken> extractJwtFromParameter(final RequestContext requestContext) {
        return requestContext
                .getRequest()
                .getUri()
                .query()
                .get(ACCESS_TOKEN_PARAM)
                .map(ImmutableJsonWebToken::fromToken);
    }

    @Override
    public boolean isApplicable(final RequestContext requestContext) {
        return defaultExtractor.isApplicable(requestContext) ||
                HttpUtils.containsQueryParameter(requestContext, ACCESS_TOKEN_PARAM);
    }

    @Override
    public String getErrorDescription() {
        return "Please provide a valid JWT in the 'Authorization' header prefixed with 'Bearer ' or as query parameter" +
                " 'access_token'.";
    }

    private DittoRuntimeException buildMultipleJwtException(final DittoHeaders dittoHeaders) {
        return GatewayAuthenticationFailedException
                .newBuilder("Multiple JWTs provided.")
                .description(
                        "A JWT was provided both as a header and as query parameter. Only a single JWT is expected.")
                .dittoHeaders(dittoHeaders)
                .build();
    }
}
