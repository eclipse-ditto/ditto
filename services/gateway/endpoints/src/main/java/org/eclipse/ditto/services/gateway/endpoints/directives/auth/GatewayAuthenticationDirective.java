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
package org.eclipse.ditto.services.gateway.endpoints.directives.auth;

import static akka.http.javadsl.server.Directives.extractRequestContext;
import static akka.http.javadsl.server.Directives.onComplete;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.services.gateway.endpoints.utils.DirectivesLoggingUtils;
import org.eclipse.ditto.services.gateway.security.authentication.AuthenticationChain;
import org.eclipse.ditto.services.gateway.security.authentication.AuthenticationResult;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayAuthenticationFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.Uri;
import akka.http.javadsl.server.Directives;
import akka.http.javadsl.server.Route;
import akka.util.ByteString;
import scala.util.Try;

/**
 * Akka Http directive which performs authentication for the Things service.
 */
public final class GatewayAuthenticationDirective {

    private static final Logger LOGGER = LoggerFactory.getLogger(GatewayAuthenticationDirective.class);

    private final AuthenticationChain authenticationChain;
    private final Function<String, DittoRuntimeException> defaultUnauthorizedExceptionFactory;

    /**
     * Constructor.
     *
     * @param authenticationChain the authentication chain that should be applied.
     * @throws NullPointerException if authenticationChain is {@code null}.
     */
    public GatewayAuthenticationDirective(final AuthenticationChain authenticationChain) {
        this(authenticationChain, correlationId -> GatewayAuthenticationFailedException.newBuilder("Unauthorized.")
                .dittoHeaders(DittoHeaders.newBuilder().correlationId(correlationId).build())
                .build());
    }

    /**
     * Constructor.
     *
     * @param authenticationChain the authentication chain that should be applied.
     * @param defaultUnauthorizedExceptionFactory a function that creates a ditto runtime exception that should be
     * returned if no
     * {@link org.eclipse.ditto.services.gateway.security.authentication.AuthenticationProvider authentication provider}
     * in {@link AuthenticationChain#authenticationProviderChain} is applicable.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public GatewayAuthenticationDirective(final AuthenticationChain authenticationChain,
            final Function<String, DittoRuntimeException> defaultUnauthorizedExceptionFactory) {
        checkNotNull(authenticationChain, "authenticationChain");
        checkNotNull(defaultUnauthorizedExceptionFactory, "defaultUnauthorizedExceptionFactory");

        this.authenticationChain = authenticationChain;
        this.defaultUnauthorizedExceptionFactory = defaultUnauthorizedExceptionFactory;
    }

    /**
     * Depending on the request headers, one of the supported authentication mechanisms is applied.
     *
     * @param correlationId the correlationId which will be added to the log
     * @param inner the inner route which will be wrapped with the {@link AuthorizationContext}
     * @return the inner route wrapped with the {@link AuthorizationContext}
     */
    public Route authenticate(final String correlationId, final Function<AuthorizationContext, Route> inner) {
        return extractRequestContext(
                requestContext -> DirectivesLoggingUtils.enhanceLogWithCorrelationId(correlationId, () -> {

                    final Uri requestUri = requestContext.getRequest().getUri();

                    if (!authenticationChain.isApplicable(requestContext)) {
                        LOGGER.debug("Missing Authentication for URI '{}'. Applying unauthorizedDirective '{}'",
                                requestUri, defaultUnauthorizedExceptionFactory);
                        return defaultUnauthorized(correlationId);
                    }

                    final CompletableFuture<AuthenticationResult> authenticationResult =
                            authenticationChain.authenticate(requestContext, correlationId);

                    final Function<Try<AuthenticationResult>, Route> handleAuthenticationTry =
                            authenticationResultTry -> handleAuthenticationTry(authenticationResultTry, requestUri,
                                    correlationId, inner);

                    return onComplete(authenticationResult, handleAuthenticationTry);
                }));
    }

    private Route handleAuthenticationTry(final Try<AuthenticationResult> authenticationResultTry, final Uri requestUri,
            final String correlationId, final Function<AuthorizationContext, Route> inner) {

        if (authenticationResultTry.isSuccess()) {
            final AuthenticationResult authenticationResult = authenticationResultTry.get();
            if (authenticationResult.isSuccess()) {
                return inner.apply(authenticationResult.getAuthorizationContext());
            } else {
                return handleFailedAuthentication(authenticationResult.getReasonOfFailure(), requestUri, correlationId);
            }
        } else {
            return handleFailedAuthentication(authenticationResultTry.failed().get(), requestUri, correlationId);
        }
    }

    private Route handleFailedAuthentication(final Throwable reasonOfFailure, final Uri requestUri,
            final String correlationId) {

        if (reasonOfFailure instanceof DittoRuntimeException) {
            final DittoRuntimeException reasonOfFailureDRE = (DittoRuntimeException) reasonOfFailure;
            LOGGER.debug("Authentication for URI '{}' failed. Rethrow DittoRuntimeException.", requestUri,
                    reasonOfFailureDRE);
            throw reasonOfFailureDRE;
        } else {
            LOGGER.warn("Unexpected error during authentication for URI '{}'. Applying unauthorizedDirective",
                    requestUri, reasonOfFailure);
            return defaultUnauthorized(correlationId);
        }
    }

    private Route defaultUnauthorized(final String correlationId) {
        final DittoRuntimeException unauthorizedException =
                defaultUnauthorizedExceptionFactory.apply(correlationId);

        final Set<HttpHeader> httpHeaders = unauthorizedException.getDittoHeaders()
                .entrySet()
                .stream()
                .map(entry -> HttpHeader.parse(entry.getKey(), entry.getValue()))
                .collect(Collectors.toSet());

        final HttpResponse httpResponse = HttpResponse.create()
                .withStatus(unauthorizedException.getStatusCode().toInt())
                .withHeaders(httpHeaders)
                .withEntity(ContentTypes.APPLICATION_JSON, ByteString.fromString(unauthorizedException.toJsonString()));

        return Directives.complete(httpResponse);
    }

}
