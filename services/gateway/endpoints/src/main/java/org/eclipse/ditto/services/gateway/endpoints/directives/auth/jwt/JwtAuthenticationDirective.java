/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.gateway.endpoints.directives.auth.jwt;

import static akka.http.javadsl.server.Directives.extractRequestContext;
import static akka.http.javadsl.server.Directives.onSuccess;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;
import static org.eclipse.ditto.services.gateway.endpoints.utils.HttpUtils.containsAuthorizationForPrefix;
import static org.eclipse.ditto.services.gateway.endpoints.utils.HttpUtils.getRequestHeader;

import java.security.PublicKey;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.auth.AuthorizationModelFactory;
import org.eclipse.ditto.services.utils.tracing.MutableKamonTimer;
import org.eclipse.ditto.services.utils.tracing.TraceInformation;
import org.eclipse.ditto.services.utils.tracing.TracingTags;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.services.gateway.endpoints.directives.auth.AuthenticationProvider;
import org.eclipse.ditto.services.gateway.endpoints.utils.DirectivesLoggingUtils;
import org.eclipse.ditto.services.utils.tracing.TraceUtils;
import org.eclipse.ditto.services.gateway.security.HttpHeader;
import org.eclipse.ditto.services.gateway.security.jwt.ImmutableJsonWebToken;
import org.eclipse.ditto.services.gateway.security.jwt.JsonWebToken;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayAuthenticationFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.dispatch.MessageDispatcher;
import akka.http.javadsl.server.RequestContext;
import akka.http.javadsl.server.Route;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureException;
import io.jsonwebtoken.impl.DefaultJwtParser;

/**
 * Implementation of {@link AuthenticationProvider} handling JWT authentication.
 */
public final class JwtAuthenticationDirective implements AuthenticationProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(JwtAuthenticationDirective.class);

    private static final String AUTHORIZATION_JWT = "Bearer";

    private static final String TRACE_FILTER_AUTH_JWT = "filter_auth_jwt";

    private final MessageDispatcher blockingDispatcher;
    private final PublicKeyProvider publicKeyProvider;
    private final AuthorizationSubjectsProvider authorizationSubjectsProvider;

    /**
     * Constructs a new {@link JwtAuthenticationDirective}.
     *
     * @param blockingDispatcher a {@link MessageDispatcher} used for blocking calls.
     * @param publicKeyProvider the provider for public keys.
     * @param authorizationSubjectsProvider a provider for authorization subjects of a jwt.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public JwtAuthenticationDirective(final MessageDispatcher blockingDispatcher,
            final PublicKeyProvider publicKeyProvider,
            final AuthorizationSubjectsProvider authorizationSubjectsProvider) {
        this.blockingDispatcher = checkNotNull(blockingDispatcher);
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

                    final TraceInformation traceInformation = TraceUtils.determineTraceInformation(
                            requestContext.getRequest().getUri().toRelative().path());

                    final MutableKamonTimer timer = MutableKamonTimer.build(TRACE_FILTER_AUTH_JWT,
                            traceInformation.getTags());

                    return onSuccess(() -> CompletableFuture
                            .supplyAsync(() -> DirectivesLoggingUtils.enhanceLogWithCorrelationId(correlationId,
                                    () -> publicKeyProvider.getPublicKey(jwt.getIssuer(), jwt.getKeyId())
                                            .orElseThrow(() -> buildJwtUnauthorizedException(correlationId, timer))),
                                    blockingDispatcher)
                            .thenApply(publicKey -> DirectivesLoggingUtils.enhanceLogWithCorrelationId(correlationId,
                                    () -> {
                                        validateToken(jwt, publicKey, correlationId, timer);

                                        final AuthorizationContext authContext =
                                                AuthorizationModelFactory.newAuthContext(
                                                        authorizationSubjectsProvider.getAuthorizationSubjects(jwt));

                                        timer.tag(TracingTags.AUTH_SUCCESS, Boolean.toString(true))
                                                .stop();

                                        return authContext;
                                    })), inner);
                }));
    }

    private static DittoRuntimeException buildMissingJwtException(final String correlationId) {
        return GatewayAuthenticationFailedException
                .newBuilder("The UNKNOWN was missing.")
                .dittoHeaders(DittoHeaders.newBuilder().correlationId(correlationId).build())
                .build();
    }

    private void validateToken(final JsonWebToken authorizationToken, final PublicKey publicKey,
            final String correlationId, final MutableKamonTimer timer) {
        final DefaultJwtParser defaultJwtParser = new DefaultJwtParser();

        try {
            defaultJwtParser.setSigningKey(publicKey).parse(authorizationToken.getToken());
        } catch (final ExpiredJwtException | MalformedJwtException | SignatureException | IllegalArgumentException e) {
            LOGGER.info("Got Exception '{}' during parsing UNKNOWN: {}", e.getClass().getSimpleName(), e.getMessage(),
                    e);
            throw buildJwtUnauthorizedException(correlationId, timer);
        }
    }

    private static DittoRuntimeException buildJwtUnauthorizedException(final String correlationId,
            final MutableKamonTimer timer) {
        timer.tag(TracingTags.AUTH_SUCCESS, Boolean.toString(false))
                .stop();

        return GatewayAuthenticationFailedException.newBuilder("The UNKNOWN could not be verified")
                .description("Check if your token is not expired and set the token accordingly.")
                .dittoHeaders(DittoHeaders.newBuilder().correlationId(correlationId).build())
                .build();
    }
}
