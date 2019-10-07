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
package org.eclipse.ditto.services.gateway.security.authentication.jwt;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;
import static org.eclipse.ditto.services.gateway.security.utils.HttpUtils.getRequestHeader;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.jwt.ImmutableJsonWebToken;
import org.eclipse.ditto.model.jwt.JsonWebToken;
import org.eclipse.ditto.services.gateway.security.HttpHeader;
import org.eclipse.ditto.services.gateway.security.authentication.DefaultAuthenticationResult;
import org.eclipse.ditto.services.gateway.security.authentication.TimeMeasuringAuthenticationProvider;
import org.eclipse.ditto.services.gateway.security.utils.HttpUtils;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayAuthenticationFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.http.javadsl.server.RequestContext;

/**
 * Handles authentication by JWT.
 */
@NotThreadSafe
public final class JwtAuthenticationProvider extends TimeMeasuringAuthenticationProvider<DefaultAuthenticationResult> {

    private static final Logger LOGGER = LoggerFactory.getLogger(JwtAuthenticationProvider.class);
    private static final String AUTHENTICATION_TYPE = "JWT";
    private static final String AUTHORIZATION_JWT = "Bearer";

    private final JwtAuthorizationContextProvider jwtAuthorizationContextProvider;
    private final JwtValidator jwtValidator;

    private JwtAuthenticationProvider(final JwtValidator jwtValidator,
            final JwtAuthorizationContextProvider jwtAuthorizationContextProvider) {

        this.jwtAuthorizationContextProvider =
                checkNotNull(jwtAuthorizationContextProvider, "JwtAuthorizationContextProvider");
        this.jwtValidator = checkNotNull(jwtValidator, "JWT Validator");
    }

    /**
     * Creates a new instance of the JWT authentication provider.
     *
     * @param jwtValidator the .
     * @param jwtAuthorizationContextProvider builds the authorization context based on the JWT.
     * @return the created instance.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static JwtAuthenticationProvider getInstance(final JwtValidator jwtValidator,
            final JwtAuthorizationContextProvider jwtAuthorizationContextProvider) {

        return new JwtAuthenticationProvider(jwtValidator, jwtAuthorizationContextProvider);
    }

    /**
     * Indicates whether this authentication provider is applicable for the given
     * {@link RequestContext request context}.
     *
     * @param requestContext the request context that should be authenticated.
     * @return {@code true} if the request contains an authorization header with a value starting with "Bearer ",
     * {@code false} if not.
     */
    @Override
    public boolean isApplicable(final RequestContext requestContext) {
        return HttpUtils.containsAuthorizationForPrefix(requestContext, AUTHORIZATION_JWT);
    }

    /**
     * Authenticates the given {@link RequestContext request context}.
     *
     * @param requestContext the request context to authenticate.
     * @param correlationId the correlation ID of the request.
     * @return the authentication result.
     */
    @Override
    protected DefaultAuthenticationResult tryToAuthenticate(final RequestContext requestContext,
            final CharSequence correlationId) {

        final Optional<JsonWebToken> jwtOptional = extractJwtFromRequest(requestContext);
        if (!jwtOptional.isPresent()) {
            LOGGER.debug("JWT is missing.");
            return DefaultAuthenticationResult.failed(buildMissingJwtException(correlationId));
        }

        final CompletableFuture<DefaultAuthenticationResult> authenticationResultFuture =
                getAuthorizationContext(jwtOptional.get(), correlationId)
                        .thenApply(DefaultAuthenticationResult::successful)
                        .exceptionally(throwable -> toFailedAuthenticationResult(throwable, correlationId));

        return waitForResult(authenticationResultFuture, correlationId);
    }

    private static Optional<JsonWebToken> extractJwtFromRequest(final RequestContext requestContext) {
        return getRequestHeader(requestContext, HttpHeader.AUTHORIZATION.toString())
                .map(ImmutableJsonWebToken::fromAuthorizationString);
    }

    @SuppressWarnings("ConstantConditions")
    private CompletableFuture<AuthorizationContext> getAuthorizationContext(final JsonWebToken jwt,
            final CharSequence correlationId) {

        return jwtValidator.validate(jwt)
                .thenApply(validationResult -> {
                    LogUtil.enhanceLogWithCorrelationId(correlationId);
                    if (!validationResult.isValid()) {
                        final Throwable reasonForInvalidity = validationResult.getReasonForInvalidity();
                        LOGGER.debug("The JWT is invalid.", reasonForInvalidity);
                        throw buildJwtUnauthorizedException(correlationId, reasonForInvalidity);
                    }

                    final AuthorizationContext authorizationContext = tryToGetAuthorizationContext(jwt, correlationId);
                    LOGGER.info("Completed JWT authentication successfully.");
                    return authorizationContext;
                });
    }

    private AuthorizationContext tryToGetAuthorizationContext(final JsonWebToken jwt,
            final CharSequence correlationId) {

        try {
            return jwtAuthorizationContextProvider.getAuthorizationContext(jwt);
        } catch (final Exception e) {
            throw buildJwtUnauthorizedException(correlationId, e);
        }
    }

    /**
     * Creates failed authentication result with a
     * {@link org.eclipse.ditto.services.gateway.security.authentication.AuthenticationResult#getReasonOfFailure() reason of failure}
     * based on the given throwable.
     *
     * @param throwable the throwable that caused a failure.
     * @param correlationId
     * @return a failed authentication result holding the extracted reason of failure.
     */
    @Override
    protected DefaultAuthenticationResult toFailedAuthenticationResult(final Throwable throwable,
            final CharSequence correlationId) {

        LOGGER.debug("JWT Authentication failed.", throwable);
        return DefaultAuthenticationResult.failed(toDittoRuntimeException(throwable, correlationId));
    }

    @Override
    public String getType() {
        return AUTHENTICATION_TYPE;
    }

    private static DittoRuntimeException buildMissingJwtException(final CharSequence correlationId) {
        return GatewayAuthenticationFailedException
                .newBuilder("The JWT was missing.")
                .description("Please provide a valid JWT in the authorization header prefixed with 'Bearer '")
                .dittoHeaders(DittoHeaders.newBuilder().correlationId(correlationId).build())
                .build();
    }

    private static DittoRuntimeException buildJwtUnauthorizedException(final CharSequence correlationId,
            final Throwable cause) {

        return GatewayAuthenticationFailedException.newBuilder("The JWT could not be verified.")
                .description(cause.getMessage())
                .dittoHeaders(DittoHeaders.newBuilder().correlationId(correlationId).build())
                .cause(cause)
                .build();
    }

}
