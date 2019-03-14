/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.gateway.security.authentication.jwt;

import static java.util.Objects.requireNonNull;
import static org.eclipse.ditto.services.gateway.security.utils.HttpUtils.getRequestHeader;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.services.gateway.security.HttpHeader;
import org.eclipse.ditto.services.gateway.security.authentication.DefaultAuthenticationResult;
import org.eclipse.ditto.services.gateway.security.authentication.TimeMeasuringAuthenticationProvider;
import org.eclipse.ditto.services.gateway.security.utils.HttpUtils;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayAuthenticationFailedException;
import org.eclipse.ditto.utils.jsr305.annotations.AllValuesAreNonnullByDefault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.http.javadsl.server.RequestContext;

/**
 * Handles authentication by JWT.
 */
@Immutable
@AllValuesAreNonnullByDefault
public final class JwtAuthenticationProvider extends TimeMeasuringAuthenticationProvider<DefaultAuthenticationResult> {

    private static final Logger LOGGER = LoggerFactory.getLogger(JwtAuthenticationProvider.class);
    private static final String AUTHENTICATION_TYPE = "JWT";
    private static final String AUTHORIZATION_JWT = "Bearer";

    private final PublicKeyProvider publicKeyProvider;
    private final JwtAuthorizationContextProvider jwtAuthorizationContextProvider;

    private JwtAuthenticationProvider(final PublicKeyProvider publicKeyProvider,
            final JwtAuthorizationContextProvider jwtAuthorizationContextProvider) {
        this.publicKeyProvider = publicKeyProvider;
        this.jwtAuthorizationContextProvider = jwtAuthorizationContextProvider;
    }

    /**
     * Creates a new instance of {@link JwtAuthenticationProvider}.
     *
     * @param publicKeyProvider the provider of public keys that are allowed to sign a JWT.
     * @param jwtAuthorizationContextProvider builds the authorization context based on the JWT.
     * @return The created instance of {@link JwtAuthenticationProvider}.
     */
    public static JwtAuthenticationProvider getInstance(final PublicKeyProvider publicKeyProvider,
            final JwtAuthorizationContextProvider jwtAuthorizationContextProvider) {
        return new JwtAuthenticationProvider(publicKeyProvider, jwtAuthorizationContextProvider);
    }

    /**
     * Indicates whether this authentication provider is applicable for the given
     * {@link RequestContext request context}.
     *
     * @param requestContext the request context that should be authenticated.
     * @return True if the request contains an authorization header with a value starting with "Bearer ". False if not.
     */
    @Override
    public boolean isApplicable(final RequestContext requestContext) {
        return HttpUtils.containsAuthorizationForPrefix(requestContext, AUTHORIZATION_JWT);
    }

    /**
     * Authenticates the given {@link RequestContext request context}.
     *
     * @param requestContext the request context to authenticate.
     * @param correlationId the correlation id of the request.
     * @return A future resolving to an authentication result.
     */
    @Override
    protected DefaultAuthenticationResult doExtractAuthentication(final RequestContext requestContext,
            final String correlationId) {

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

    private CompletableFuture<AuthorizationContext> getAuthorizationContext(final JsonWebToken jwt,
            final String correlationId) {
        return jwt.validate(publicKeyProvider)
                .thenApply(validationResult -> {
                    LogUtil.enhanceLogWithCorrelationId(correlationId);
                    return validationResult;
                })
                .thenApply(validationResult -> {
                    if (!validationResult.isValid()) {
                        final Throwable reasonForInvalidity = requireNonNull(validationResult.getReasonForInvalidity());
                        LOGGER.debug("The JWT is invalid.", reasonForInvalidity);
                        throw buildJwtUnauthorizedException(correlationId, reasonForInvalidity.getMessage());
                    }

                    final AuthorizationContext authorizationContext;

                    try {
                        authorizationContext = jwtAuthorizationContextProvider.getAuthorizationContext(jwt);
                    } catch (JwtAuthorizationContextProviderException e) {
                        LOGGER.debug("Could not extract the authorization context from JWT.", e);
                        throw buildJwtUnauthorizedException(correlationId, e.getMessage());
                    }

                    LOGGER.info("Completed JWT authentication successfully.");
                    return authorizationContext;
                });
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
    protected DefaultAuthenticationResult toFailedAuthenticationResult(final Throwable throwable,
            final String correlationId) {
        LOGGER.debug("JWT Authentication failed.", throwable);
        return DefaultAuthenticationResult.failed(toDittoRuntimeException(throwable, correlationId));
    }

    @Override
    public String getType() {
        return AUTHENTICATION_TYPE;
    }

    private Optional<JsonWebToken> extractJwtFromRequest(final RequestContext requestContext) {
        final Optional<String> authorization = getRequestHeader(requestContext, HttpHeader.AUTHORIZATION.toString());
        return authorization.map(ImmutableJsonWebToken::fromAuthorizationString);
    }

    private static DittoRuntimeException buildMissingJwtException(final String correlationId) {
        return GatewayAuthenticationFailedException
                .newBuilder("The JWT was missing.")
                .description("Please provide a valid JWT in the authorization header prefixed with 'Bearer '")
                .dittoHeaders(DittoHeaders.newBuilder().correlationId(correlationId).build())
                .build();
    }

    private static DittoRuntimeException buildJwtUnauthorizedException(final String correlationId,
            final String description) {

        return GatewayAuthenticationFailedException.newBuilder("The JWT could not be verified.")
                .description(description)
                .dittoHeaders(DittoHeaders.newBuilder().correlationId(correlationId).build())
                .build();
    }
}
