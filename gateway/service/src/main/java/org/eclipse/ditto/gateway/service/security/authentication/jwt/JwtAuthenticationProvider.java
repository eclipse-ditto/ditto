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
package org.eclipse.ditto.gateway.service.security.authentication.jwt;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.base.model.auth.AuthorizationContextType;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.gateway.api.GatewayAuthenticationFailedException;
import org.eclipse.ditto.gateway.service.security.authentication.AuthenticationResult;
import org.eclipse.ditto.gateway.service.security.authentication.DefaultAuthenticationResult;
import org.eclipse.ditto.gateway.service.security.authentication.TimeMeasuringAuthenticationProvider;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLogger;
import org.eclipse.ditto.jwt.model.JsonWebToken;

import akka.http.javadsl.server.RequestContext;

/**
 * Handles authentication by JWT.
 */
@NotThreadSafe
public final class JwtAuthenticationProvider extends TimeMeasuringAuthenticationProvider<AuthenticationResult> {

    private static final ThreadSafeDittoLogger LOGGER =
            DittoLoggerFactory.getThreadSafeLogger(JwtAuthenticationProvider.class);

    private final JwtAuthenticationResultProvider jwtAuthResultProvider;
    private final JwtValidator jwtValidator;
    private final JwtExtractor jwtExtractor;

    private JwtAuthenticationProvider(final JwtAuthenticationResultProvider jwtAuthenticationResultProvider,
            final JwtValidator jwtValidator, final JwtExtractor jwtExtractor) {

        super(LOGGER);
        jwtAuthResultProvider = checkNotNull(jwtAuthenticationResultProvider, "jwtAuthorizationContextProvider");
        this.jwtValidator = checkNotNull(jwtValidator, "jwtValidator");
        this.jwtExtractor = checkNotNull(jwtExtractor, "jwtExtractor");
    }

    /**
     * Creates a new instance of the JWT authentication provider that extracts the JWT from the Authorization header.
     *
     * @param jwtValidator the JWT validator
     * @param jwtAuthenticationResultProvider builds the authorization context based on the JWT.
     * @return the created instance.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static JwtAuthenticationProvider newInstance(
            final JwtAuthenticationResultProvider jwtAuthenticationResultProvider, final JwtValidator jwtValidator) {

        return new JwtAuthenticationProvider(jwtAuthenticationResultProvider, jwtValidator,
                DefaultJwtExtractor.getInstance());
    }

    /**
     * Creates a new instance of the JWT authentication provider that extracts the JWT also from the {@code access_token}
     * query parameter in addition to the Authorization header.
     *
     * @param jwtValidator the JWT validator
     * @param jwtAuthenticationResultProvider builds the authorization context based on the JWT.
     * @return the created instance.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static JwtAuthenticationProvider newWsInstance(
            final JwtAuthenticationResultProvider jwtAuthenticationResultProvider, final JwtValidator jwtValidator) {

        return new JwtAuthenticationProvider(jwtAuthenticationResultProvider, jwtValidator,
                WebSocketJwtExtractor.getInstance());
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
        return jwtExtractor.isApplicable(requestContext);
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

        final Optional<JsonWebToken> jwtOptional = jwtExtractor.apply(requestContext, dittoHeaders);
        if (jwtOptional.isEmpty()) {
            LOGGER.withCorrelationId(dittoHeaders).debug("JWT is missing.");
            return CompletableFuture.completedFuture(
                    DefaultAuthenticationResult.failed(dittoHeaders,
                            jwtExtractor.buildMissingJwtException(dittoHeaders)));
        }

        final CompletionStage<AuthenticationResult> authenticationResultFuture =
                getAuthenticationResult(jwtOptional.get(), dittoHeaders)
                        .exceptionally(throwable -> toFailedAuthenticationResult(throwable, dittoHeaders));

        return failOnTimeout(authenticationResultFuture, dittoHeaders);
    }

    @SuppressWarnings("ConstantConditions")
    private CompletionStage<AuthenticationResult> getAuthenticationResult(final JsonWebToken jwt,
            final DittoHeaders dittoHeaders) {

        return jwtValidator.validate(jwt)
                .thenCompose(validationResult -> {
                    if (!validationResult.isValid()) {
                        final Throwable reasonForInvalidity = validationResult.getReasonForInvalidity();
                        LOGGER.withCorrelationId(dittoHeaders).debug("The JWT is invalid.", reasonForInvalidity);
                        final DittoRuntimeException reasonForFailure =
                                buildJwtUnauthorizedException(dittoHeaders, reasonForInvalidity);
                        return CompletableFuture.completedStage(
                                DefaultAuthenticationResult.failed(dittoHeaders, reasonForFailure));
                    }
                    return tryToGetAuthenticationResult(jwt, dittoHeaders);
                })
                .thenApply(authenticationResult -> {
                    LOGGER.withCorrelationId(dittoHeaders).info("Completed JWT authentication successfully.");
                    return authenticationResult;
                });
    }

    private static DittoRuntimeException buildJwtUnauthorizedException(final DittoHeaders dittoHeaders,
            final Throwable error) {

        final var cause = error instanceof CompletionException ? error.getCause() : error;
        return GatewayAuthenticationFailedException.newBuilder("The JWT could not be verified.")
                .description(cause.getMessage())
                .dittoHeaders(dittoHeaders)
                .cause(cause)
                .build();
    }

    private CompletionStage<AuthenticationResult> tryToGetAuthenticationResult(final JsonWebToken jwt,
            final DittoHeaders dittoHeaders) {
        return jwtAuthResultProvider.getAuthenticationResult(jwt, dittoHeaders)
                .<CompletionStage<AuthenticationResult>>thenApply(CompletableFuture::completedStage)
                .exceptionally(e -> CompletableFuture.failedStage(buildJwtUnauthorizedException(dittoHeaders, e)))
                .thenCompose(Function.identity());
    }

    /**
     * Creates failed authentication result with a
     * {@link org.eclipse.ditto.gateway.service.security.authentication.AuthenticationResult#getReasonOfFailure() reason of failure}
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
