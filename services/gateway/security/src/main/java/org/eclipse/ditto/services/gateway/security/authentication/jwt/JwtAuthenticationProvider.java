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

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.model.base.auth.AuthorizationContextType;
import org.eclipse.ditto.model.base.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.jwt.ImmutableJsonWebToken;
import org.eclipse.ditto.model.jwt.JsonWebToken;
import org.eclipse.ditto.services.gateway.security.HttpHeader;
import org.eclipse.ditto.services.gateway.security.authentication.AuthenticationResult;
import org.eclipse.ditto.services.gateway.security.authentication.DefaultAuthenticationResult;
import org.eclipse.ditto.services.gateway.security.authentication.TimeMeasuringAuthenticationProvider;
import org.eclipse.ditto.services.gateway.security.utils.HttpUtils;
import org.eclipse.ditto.services.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.services.utils.akka.logging.ThreadSafeDittoLogger;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayAuthenticationFailedException;

import akka.http.javadsl.server.RequestContext;

/**
 * Handles authentication by JWT.
 */
@NotThreadSafe
public final class JwtAuthenticationProvider extends TimeMeasuringAuthenticationProvider<AuthenticationResult> {

    private static final String AUTHORIZATION_JWT = "Bearer";

    private static final ThreadSafeDittoLogger LOGGER =
            DittoLoggerFactory.getThreadSafeLogger(JwtAuthenticationProvider.class);

    private final JwtAuthenticationResultProvider jwtAuthResultProvider;
    private final JwtValidator jwtValidator;

    private JwtAuthenticationProvider(final JwtAuthenticationResultProvider jwtAuthenticationResultProvider,
            final JwtValidator jwtValidator) {

        super(LOGGER);
        jwtAuthResultProvider = checkNotNull(jwtAuthenticationResultProvider, "jwtAuthorizationContextProvider");
        this.jwtValidator = checkNotNull(jwtValidator, "jwtValidator");
    }

    /**
     * Creates a new instance of the JWT authentication provider.
     *
     * @param jwtValidator the .
     * @param jwtAuthenticationResultProvider builds the authorization context based on the JWT.
     * @return the created instance.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static JwtAuthenticationProvider newInstance(
            final JwtAuthenticationResultProvider jwtAuthenticationResultProvider, final JwtValidator jwtValidator) {

        return new JwtAuthenticationProvider(jwtAuthenticationResultProvider, jwtValidator);
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
     * @param dittoHeaders the (potentially not completely set) DittoHeaders of the request.
     * @return the authentication result.
     */
    @Override
    protected CompletableFuture<AuthenticationResult> tryToAuthenticate(final RequestContext requestContext,
            final DittoHeaders dittoHeaders) {

        final Optional<JsonWebToken> jwtOptional = extractJwtFromRequest(requestContext);
        if (jwtOptional.isEmpty()) {
            LOGGER.withCorrelationId(dittoHeaders).debug("JWT is missing.");
            return CompletableFuture.completedFuture(
                    DefaultAuthenticationResult.failed(dittoHeaders, buildMissingJwtException(dittoHeaders)));
        }

        final CompletableFuture<AuthenticationResult> authenticationResultFuture =
                getAuthenticationResult(jwtOptional.get(), dittoHeaders)
                        .exceptionally(throwable -> toFailedAuthenticationResult(throwable, dittoHeaders));

        return failOnTimeout(authenticationResultFuture, dittoHeaders);
    }

    private static Optional<JsonWebToken> extractJwtFromRequest(final RequestContext requestContext) {
        return HttpUtils.getRequestHeader(requestContext, HttpHeader.AUTHORIZATION.toString())
                .map(ImmutableJsonWebToken::fromAuthorization);
    }

    private static DittoRuntimeException buildMissingJwtException(final DittoHeaders dittoHeaders) {
        return GatewayAuthenticationFailedException
                .newBuilder("The JWT was missing.")
                .description("Please provide a valid JWT in the authorization header prefixed with 'Bearer '")
                .dittoHeaders(dittoHeaders)
                .build();
    }

    @SuppressWarnings("ConstantConditions")
    private CompletableFuture<AuthenticationResult> getAuthenticationResult(final JsonWebToken jwt,
            final DittoHeaders dittoHeaders) {

        return jwtValidator.validate(jwt)
                .thenApply(validationResult -> {
                    if (!validationResult.isValid()) {
                        final Throwable reasonForInvalidity = validationResult.getReasonForInvalidity();
                        LOGGER.withCorrelationId(dittoHeaders).debug("The JWT is invalid.", reasonForInvalidity);
                        final DittoRuntimeException reasonForFailure =
                                buildJwtUnauthorizedException(dittoHeaders, reasonForInvalidity);
                        return DefaultAuthenticationResult.failed(dittoHeaders, reasonForFailure);
                    }

                    final AuthenticationResult authenticationResult = tryToGetAuthenticationResult(jwt, dittoHeaders);
                    LOGGER.withCorrelationId(dittoHeaders).info("Completed JWT authentication successfully.");
                    return authenticationResult;
                });
    }

    private static DittoRuntimeException buildJwtUnauthorizedException(final DittoHeaders dittoHeaders,
            final Throwable cause) {

        return GatewayAuthenticationFailedException.newBuilder("The JWT could not be verified.")
                .description(cause.getMessage())
                .dittoHeaders(dittoHeaders)
                .cause(cause)
                .build();
    }

    private AuthenticationResult tryToGetAuthenticationResult(final JsonWebToken jwt, final DittoHeaders dittoHeaders) {
        try {
            return jwtAuthResultProvider.getAuthenticationResult(jwt, dittoHeaders);
        } catch (final Exception e) {
            throw buildJwtUnauthorizedException(dittoHeaders, e);
        }
    }

    /**
     * Creates failed authentication result with a
     * {@link org.eclipse.ditto.services.gateway.security.authentication.AuthenticationResult#getReasonOfFailure() reason of failure}
     * based on the given throwable.
     *
     * @param throwable the throwable that caused a failure.
     * @return a failed authentication result holding the extracted reason of failure.
     */
    @Override
    protected AuthenticationResult toFailedAuthenticationResult(final Throwable throwable,
            final DittoHeaders dittoHeaders) {

        LOGGER.withCorrelationId(dittoHeaders).debug("JWT Authentication failed.", throwable);
        return DefaultAuthenticationResult.failed(dittoHeaders, toDittoRuntimeException(throwable, dittoHeaders));
    }

    @Override
    public AuthorizationContextType getType(final RequestContext requestContext) {
        return DittoAuthorizationContextType.JWT;
    }

}
