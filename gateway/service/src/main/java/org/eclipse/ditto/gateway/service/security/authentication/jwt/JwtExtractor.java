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
import java.util.function.BiFunction;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.gateway.api.GatewayAuthenticationFailedException;
import org.eclipse.ditto.jwt.model.JsonWebToken;

import akka.http.javadsl.server.RequestContext;

/**
 * Extract a JsonWebToken from the given RequestContext or returns an empty Optional if no token is present.
 */
public interface JwtExtractor extends BiFunction<RequestContext, DittoHeaders, Optional<JsonWebToken>> {

    /**
     * Indicates whether the given {@link RequestContext request context} contains the required information to
     * extract a JWT.
     *
     * @param requestContext the current request context
     * @return {@code true} if the request context contains the required information
     */
    boolean isApplicable(RequestContext requestContext);

    /**
     * Builds an exception with detailed description where the token information was expected.
     *
     * @param dittoHeaders ditto headers of the current request
     * @return the built exception
     */
    default Exception buildMissingJwtException(final DittoHeaders dittoHeaders) {
        return GatewayAuthenticationFailedException
                .newBuilder("The JWT was missing.")
                .description(getErrorDescription())
                .dittoHeaders(dittoHeaders)
                .build();
    }

    /**
     * @return detail information about the error and where the token information was expected.
     */
    String getErrorDescription();
}
