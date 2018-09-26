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
package org.eclipse.ditto.services.gateway.endpoints.directives.auth.jwt;

import static akka.http.javadsl.server.Directives.extractRequestContext;
import static akka.http.javadsl.server.Directives.onSuccess;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;
import static org.eclipse.ditto.services.gateway.endpoints.utils.HttpUtils.containsAuthorizationForPrefix;
import static org.eclipse.ditto.services.gateway.endpoints.utils.HttpUtils.getRequestHeader;

import java.security.PublicKey;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.function.Function;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationModelFactory;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.services.gateway.endpoints.directives.auth.AuthenticationProvider;
import org.eclipse.ditto.services.gateway.endpoints.utils.DirectivesLoggingUtils;
import org.eclipse.ditto.services.gateway.security.HttpHeader;
import org.eclipse.ditto.services.gateway.security.jwt.ImmutableJsonWebToken;
import org.eclipse.ditto.services.gateway.security.jwt.JsonWebToken;
import org.eclipse.ditto.services.utils.jwt.JjwtDeserializer;
import org.eclipse.ditto.services.utils.metrics.instruments.timer.StartedTimer;
import org.eclipse.ditto.services.utils.tracing.TraceUtils;
import org.eclipse.ditto.services.utils.tracing.TracingTags;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayAuthenticationFailedException;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayAuthenticationProviderUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.http.javadsl.server.RequestContext;
import akka.http.javadsl.server.Route;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;

/**
 * Implementation of {@link AuthenticationProvider} handling JWT authentication.
 */
public final class JwtAuthenticationDirective implements AuthenticationProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(JwtAuthenticationDirective.class);

    private static final String AUTHORIZATION_JWT = "Bearer";

    private static final String AUTHENTICATION_TYPE = "JWT";

    private static final String TRACE_FILTER_AUTH_JWT = "filter_auth_jwt";

    private final PublicKeyProvider publicKeyProvider;
    private final AuthorizationSubjectsProvider authorizationSubjectsProvider;

    /**
     * Constructs a new {@link JwtAuthenticationDirective}.
     *
     * @param publicKeyProvider the provider for public keys.
     * @param authorizationSubjectsProvider a provider for authorization subjects of a jwt.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public JwtAuthenticationDirective(final PublicKeyProvider publicKeyProvider,
            final AuthorizationSubjectsProvider authorizationSubjectsProvider) {
        this.publicKeyProvider = checkNotNull(publicKeyProvider);
        this.authorizationSubjectsProvider = checkNotNull(authorizationSubjectsProvider);
    }

    @Override
    public boolean isApplicable(final RequestContext context) {
        return containsAuthorizationForPrefix(context, AUTHORIZATION_JWT);
    }

    @Override
    public Route unauthorized(final String correlationId) {
        throw buildMissingJwtException(correlationId);
    }


    @Override
    public Route authenticate(final String correlationId, final Function<AuthorizationContext, Route> inner) {
        return extractRequestContext(
                requestContext -> DirectivesLoggingUtils.enhanceLogWithCorrelationId(correlationId, () -> {
                    final Optional<String> authorization =
                            getRequestHeader(requestContext, HttpHeader.AUTHORIZATION.toString().toLowerCase());

                    final JsonWebToken jwt = authorization.map(ImmutableJsonWebToken::fromAuthorizationString)
                            .orElseThrow(() -> buildMissingJwtException(correlationId));

                    final StartedTimer timer = TraceUtils
                            .newAuthFilterTimer(AUTHENTICATION_TYPE, requestContext.getRequest())
                            .build();

                    return onSuccess(() -> publicKeyProvider.getPublicKey(jwt.getIssuer(), jwt.getKeyId())
                            .thenApply(publicKeyOpt ->
                                    DirectivesLoggingUtils.enhanceLogWithCorrelationId(correlationId,
                                            () -> {
                                                final PublicKey publicKey = publicKeyOpt
                                                        .orElseThrow(
                                                                () -> buildJwtUnauthorizedException(correlationId));

                                                validateToken(jwt, publicKey, correlationId);

                                                final List<AuthorizationSubject> authSubjects =
                                                        authorizationSubjectsProvider
                                                                .getAuthorizationSubjects(jwt);

                                                final AuthorizationContext authContext =
                                                        AuthorizationModelFactory.newAuthContext(authSubjects);

                                                timer.tag(TracingTags.AUTH_SUCCESS, true)
                                                        .stop();

                                                return authContext;
                                            })
                            ).exceptionally(t -> {
                                final Throwable rootCause = (t instanceof CompletionException) ? t.getCause() : t;
                                if (rootCause instanceof GatewayAuthenticationFailedException) {
                                    timer.tag(TracingTags.AUTH_SUCCESS, false)
                                            .stop();


                                    final DittoRuntimeException e = (DittoRuntimeException) rootCause;
                                    LOGGER.debug("JWT authentication failed.", e);
                                    throw e;
                                } else {
                                    timer.tag(TracingTags.AUTH_SUCCESS, false)
                                            .tag(TracingTags.AUTH_ERROR, true)
                                            .stop();

                                    LOGGER.warn("Unexpected error during JWT authentication.", rootCause);
                                    throw buildAuthenticationProviderUnavailableException(correlationId,
                                            rootCause);
                                }
                            }), inner);
                }));
    }

    private static DittoRuntimeException buildMissingJwtException(final String correlationId) {
        return GatewayAuthenticationFailedException
                .newBuilder("The JWT was missing.")
                .dittoHeaders(DittoHeaders.newBuilder().correlationId(correlationId).build())
                .build();
    }

    private void validateToken(final JsonWebToken authorizationToken, final PublicKey publicKey,
            final String correlationId) {

        try {
            Jwts.parser().deserializeJsonWith(JjwtDeserializer.getInstance())
                    .setSigningKey(publicKey)
                    .parse(authorizationToken.getToken());
        } catch (final ExpiredJwtException | MalformedJwtException | SignatureException | IllegalArgumentException e) {
            LOGGER.info("Got Exception '{}' during parsing JWT: {}", e.getClass().getSimpleName(), e.getMessage(),
                    e);
            throw buildJwtUnauthorizedException(correlationId);
        }
    }

    private static DittoRuntimeException buildJwtUnauthorizedException(final String correlationId) {

        return GatewayAuthenticationFailedException.newBuilder("The JWT could not be verified")
                .description("Check if your token is not expired and set the token accordingly.")
                .dittoHeaders(DittoHeaders.newBuilder().correlationId(correlationId).build())
                .build();
    }

    private static DittoRuntimeException buildAuthenticationProviderUnavailableException(final String correlationId,
            final Throwable cause) {
        return GatewayAuthenticationProviderUnavailableException
                .newBuilder()
                .dittoHeaders(DittoHeaders.newBuilder().correlationId(correlationId).build())
                .cause(cause)
                .build();
    }
}
