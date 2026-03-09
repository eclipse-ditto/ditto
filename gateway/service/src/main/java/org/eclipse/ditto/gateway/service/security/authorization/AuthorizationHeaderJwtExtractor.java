/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.gateway.service.security.authorization;

import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.jwt.model.ImmutableJsonWebToken;
import org.eclipse.ditto.jwt.model.JsonWebToken;
import org.eclipse.ditto.jwt.model.JwtInvalidException;
import org.eclipse.ditto.utils.jsr305.annotations.AllValuesAreNonnullByDefault;

/**
 * Utility for extracting a JWT from an HTTP Authorization header value.
 */
@Immutable
@AllValuesAreNonnullByDefault
public final class AuthorizationHeaderJwtExtractor {

    private static final String BEARER_PREFIX = "Bearer ";

    private AuthorizationHeaderJwtExtractor() {
        throw new AssertionError();
    }

    /**
     * Extracts a {@link JsonWebToken} from an Authorization header value if it is a bearer token.
     *
     * @param authorizationHeaderValue the raw Authorization header value.
     * @return an Optional containing the extracted JWT, or empty if no bearer token is present.
     */
    public static Optional<JsonWebToken> extractJwt(@Nullable final String authorizationHeaderValue) {
        if (authorizationHeaderValue == null || !startsWithBearerPrefix(authorizationHeaderValue)) {
            return Optional.empty();
        }
        try {
            return Optional.of(ImmutableJsonWebToken.fromAuthorization(authorizationHeaderValue));
        } catch (final JwtInvalidException e) {
            return Optional.empty();
        }
    }

    private static boolean startsWithBearerPrefix(final String authorizationHeaderValue) {
        return authorizationHeaderValue.length() > BEARER_PREFIX.length() &&
                authorizationHeaderValue.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length());
    }

}
