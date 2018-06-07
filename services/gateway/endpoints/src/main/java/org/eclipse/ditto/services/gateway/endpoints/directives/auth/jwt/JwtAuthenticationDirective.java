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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.function.Function;
import java.util.function.Supplier;

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
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayAuthenticationFailedException;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayAuthenticationProviderUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.http.javadsl.server.RequestContext;
import akka.http.javadsl.server.Route;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureException;
import io.jsonwebtoken.impl.DefaultJwtParser;
import kamon.Kamon;
import kamon.trace.TraceContext;

/**
 * Implementation of {@link AuthenticationProvider} handling JWT authentication.
 */
public final class JwtAuthenticationDirective implements AuthenticationProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(JwtAuthenticationDirective.class);

    private static final String AUTHORIZATION_JWT = "Bearer";

    private static final String TRACE_FILTER_AUTH_JWT_FAIL = "filter.auth.jwt.fail";
    private static final String TRACE_FILTER_AUTH_JWT_ERROR = "filter.auth.jwt.error";
    private static final String TRACE_FILTER_AUTH_JWT_SUCCESS = "filter.auth.jwt.success";

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
                    final TraceContext traceContext = Kamon.tracer().newContext(TRACE_FILTER_AUTH_JWT_ERROR);
                    final Optional<String> authorization =
                            getRequestHeader(requestContext, HttpHeader.AUTHORIZATION.toString().toLowerCase());

                    if (!authorization.isPresent()) {
                        traceContext.rename(TRACE_FILTER_AUTH_JWT_FAIL);
                        traceContext.finish();

                        throw buildMissingJwtException(correlationId);
                    }

                    final JsonWebToken jwt = ImmutableJsonWebToken.fromAuthorizationString(authorization.get());

                    return onSuccess(() -> publicKeyProvider.getPublicKey(jwt.getIssuer(), jwt.getKeyId())
                                    .thenApply(publicKeyOpt ->
                                            DirectivesLoggingUtils.enhanceLogWithCorrelationId(correlationId,
                                                    () -> {
                                                        final Supplier<DittoRuntimeException>
                                                                missingAuthExceptionSupplier =
                                                                () -> buildJwtUnauthorizedException(correlationId);
                                                        final PublicKey publicKey = publicKeyOpt
                                                                .orElseThrow(missingAuthExceptionSupplier);

                                                        validateToken(jwt, publicKey, correlationId);

                                                        final List<AuthorizationSubject> authSubjects =
                                                                authorizationSubjectsProvider
                                                                        .getAuthorizationSubjects(jwt);
                                                        final AuthorizationContext authContext =
                                                                AuthorizationModelFactory.newAuthContext(authSubjects);

                                                        traceContext.rename(TRACE_FILTER_AUTH_JWT_SUCCESS);
                                                        traceContext.finish();

                                                        return authContext;
                                                    })
                                    ).exceptionally(t -> {
                                        final Throwable rootCause =
                                                (t instanceof CompletionException) ? t.getCause() : t;
                                        if (rootCause instanceof GatewayAuthenticationFailedException) {
                                            traceContext.rename(TRACE_FILTER_AUTH_JWT_FAIL);
                                            traceContext.finish();

                                            final DittoRuntimeException e = (DittoRuntimeException) rootCause;
                                            LOGGER.debug("JWT authentication failed.", e);
                                            throw e;
                                        } else {
                                            traceContext.finish();

                                            LOGGER.warn("Unexpected error during JWT authentication.", rootCause);
                                            throw buildAuthenticationProviderUnavailableException(correlationId,
                                                    rootCause);
                                        }
                                    }),
                            inner);
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
        final DefaultJwtParser defaultJwtParser = new DefaultJwtParser();

        try {
            defaultJwtParser.setSigningKey(publicKey).parse(authorizationToken.getToken());
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
