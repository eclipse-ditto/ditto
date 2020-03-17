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
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.services.gateway.security.authentication.AuthenticationChain;
import org.eclipse.ditto.services.gateway.security.authentication.AuthenticationResult;
import org.eclipse.ditto.services.utils.akka.logging.DittoLogger;
import org.eclipse.ditto.services.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayAuthenticationFailedException;

import akka.http.javadsl.model.Uri;
import akka.http.javadsl.server.Directives;
import akka.http.javadsl.server.Route;
import scala.util.Try;

/**
 * Akka Http directive which performs authentication for the gateway.
 */
public final class GatewayAuthenticationDirective {

    private static final DittoLogger LOGGER = DittoLoggerFactory.getLogger(GatewayAuthenticationDirective.class);

    private final AuthenticationChain authenticationChain;
    private final Function<DittoHeaders, DittoRuntimeException> defaultUnauthorizedExceptionFactory;

    /**
     * Constructor.
     *
     * @param authenticationChain the authentication chain that should be applied.
     * @throws NullPointerException if authenticationChain is {@code null}.
     */
    public GatewayAuthenticationDirective(final AuthenticationChain authenticationChain) {
        this(authenticationChain, dittoHeaders -> GatewayAuthenticationFailedException.newBuilder("Unauthorized.")
                .dittoHeaders(dittoHeaders)
                .build());
    }

    /**
     * Constructor.
     *
     * @param authenticationChain the authentication chain that should be applied.
     * @param defaultUnauthorizedExceptionFactory a function that creates a DittoRuntimeException that should be
     * returned if {@code authenticationChain} did not contain an applicable AuthenticationProvider.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public GatewayAuthenticationDirective(final AuthenticationChain authenticationChain,
            final Function<DittoHeaders, DittoRuntimeException> defaultUnauthorizedExceptionFactory) {

        this.authenticationChain = checkNotNull(authenticationChain, "authenticationChain");
        this.defaultUnauthorizedExceptionFactory =
                checkNotNull(defaultUnauthorizedExceptionFactory, "defaultUnauthorizedExceptionFactory");
    }

    /**
     * Depending on the request headers, one of the supported authentication mechanisms is applied.
     *
     * @param dittoHeaders the DittoHeaders which will be added to the log.
     * @param inner the inner route which will be wrapped with the {@link AuthorizationContext}.
     * @return the inner route.
     */
    public Route authenticate(final DittoHeaders dittoHeaders,
            final BiFunction<AuthorizationContext, DittoHeaders, Route> inner) {
        return extractRequestContext(requestContext -> {
            final Uri requestUri = requestContext.getRequest().getUri();

            final CompletableFuture<AuthenticationResult> authenticationResult =
                    authenticationChain.authenticate(requestContext, dittoHeaders);

            final Function<Try<AuthenticationResult>, Route> handleAuthenticationTry =
                    authenticationResultTry -> handleAuthenticationTry(authenticationResultTry, requestUri,
                            dittoHeaders, inner);

            return Directives.onComplete(authenticationResult, handleAuthenticationTry);
        });
    }

    private Route handleAuthenticationTry(final Try<AuthenticationResult> authenticationResultTry,
            final Uri requestUri,
            final DittoHeaders dittoHeaders,
            final BiFunction<AuthorizationContext, DittoHeaders, Route> inner) {

        if (authenticationResultTry.isSuccess()) {
            final AuthenticationResult authenticationResult = authenticationResultTry.get();
            if (authenticationResult.isSuccess()) {
                return inner.apply(authenticationResult.getAuthorizationContext(), authenticationResult.getDittoHeaders());
            }
            return handleFailedAuthentication(authenticationResult.getReasonOfFailure(), requestUri, dittoHeaders);
        }
        return handleFailedAuthentication(authenticationResultTry.failed().get(), requestUri, dittoHeaders);
    }

    private Route handleFailedAuthentication(final Throwable reasonOfFailure, final Uri requestUri,
            final DittoHeaders dittoHeaders) {

        if (reasonOfFailure instanceof DittoRuntimeException) {
            LOGGER.debug("Authentication for URI <{}> failed. Rethrow DittoRuntimeException.", requestUri,
                    reasonOfFailure);
            throw (DittoRuntimeException) reasonOfFailure;
        }

        LOGGER.debug("Unexpected error during authentication for URI <{}>! Applying unauthorizedDirective",
                requestUri, reasonOfFailure);
        throw defaultUnauthorizedExceptionFactory.apply(dittoHeaders);
    }

}
