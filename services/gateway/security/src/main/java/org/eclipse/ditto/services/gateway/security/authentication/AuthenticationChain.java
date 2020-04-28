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
package org.eclipse.ditto.services.gateway.security.authentication;

import static org.eclipse.ditto.model.base.common.ConditionChecker.argumentNotEmpty;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.Uri;
import akka.http.javadsl.server.RequestContext;

/**
 * Handles authentication by multiple authentication providers.
 */
@Immutable
public final class AuthenticationChain {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationChain.class);
    private final Collection<AuthenticationProvider<?>> authenticationProviderChain;
    private final Executor authenticationDispatcher;
    private final AuthenticationFailureAggregator authenticationFailureAggregator;

    private AuthenticationChain(final Collection<AuthenticationProvider<?>> authenticationProviders,
            final AuthenticationFailureAggregator authenticationFailureAggregator,
            final Executor authenticationDispatcher) {

        checkNotNull(authenticationProviders, "authenticationProviders");
        argumentNotEmpty(authenticationProviders, "authenticationProviders");
        authenticationProviderChain = authenticationProviders;
        this.authenticationFailureAggregator =
                checkNotNull(authenticationFailureAggregator, "authenticationFailureAggregator");
        this.authenticationDispatcher = checkNotNull(authenticationDispatcher, "authenticationDispatcher");
    }

    /**
     * Returns an authentication chain instance.
     *
     * @param authenticationProviders the list of authentication providers that should be used in the given order.
     * @param authenticationFailureAggregator aggregates multiple failed authentication results to a single
     * {@link org.eclipse.ditto.model.base.exceptions.DittoRuntimeException ditto runtime exception } in case multiple
     * {@link AuthenticationProvider authentication providers} in the given collection of authentication providers were
     * applicable to a request and all of them failed.
     * @param authenticationDispatcher dispatcher used for blocking calls.
     * @return the instance.
     * @throws NullPointerException if argument is {@code null}.
     * @throws IllegalArgumentException if {@code authenticationProviders} is empty.
     */
    public static AuthenticationChain getInstance(final Collection<AuthenticationProvider<?>> authenticationProviders,
            final AuthenticationFailureAggregator authenticationFailureAggregator,
            final Executor authenticationDispatcher) {

        return new AuthenticationChain(authenticationProviders, authenticationFailureAggregator,
                authenticationDispatcher);
    }

    /**
     * Authenticates the given {@link RequestContext request context} and returns the
     * {@link AuthenticationResult result}.
     *
     * @param requestContext the request context that should be authenticated.
     * @param dittoHeaders the (potentially not completely set) DittoHeaders of the request.
     * @return A future resolving to the {@link AuthenticationResult authentication result}.
     */
    public CompletableFuture<AuthenticationResult> authenticate(final RequestContext requestContext,
            final DittoHeaders dittoHeaders) {

        return CompletableFuture
                .runAsync(() -> LogUtil.enhanceLogWithCorrelationId(dittoHeaders), authenticationDispatcher)
                .thenApply(voidValue -> doAuthenticate(requestContext, dittoHeaders));
    }

    private AuthenticationResult doAuthenticate(final RequestContext requestContext, final DittoHeaders dittoHeaders) {
        final HttpRequest httpRequest = requestContext.getRequest();
        final Uri requestUri = httpRequest.getUri();

        final List<AuthenticationResult> failedAuthenticationResults = new ArrayList<>();

        for (final AuthenticationProvider authenticationProvider : authenticationProviderChain) {
            final String authProviderName = authenticationProvider.getClass().getSimpleName();

            if (!authenticationProvider.isApplicable(requestContext)) {
                LOGGER.debug("AuthenticationProvider <{}> is not applicable. Trying next ...", authProviderName);
                continue;
            }

            LOGGER.debug("Applying authentication provider <{}> to URI <{}>.", authProviderName, requestUri);
            final AuthenticationResult authenticationResult =
                    authenticationProvider.authenticate(requestContext, dittoHeaders);

            if (authenticationResult.isSuccess()) {
                LOGGER.debug("Authentication using authentication provider <{}> to URI <{}> was successful.",
                        authProviderName, requestUri);
                return authenticationResult;
            } else {
                LOGGER.debug("Authentication using authentication provider <{}> to URI <{}> failed.", authProviderName,
                        requestUri, authenticationResult.getReasonOfFailure());
                failedAuthenticationResults.add(authenticationResult);
            }
        }

        if (failedAuthenticationResults.isEmpty()) {
            return DefaultAuthenticationResult.failed(dittoHeaders,
                    new IllegalStateException("No applicable authentication provider was found!"));
        }

        if (1 == failedAuthenticationResults.size()) {
            return failedAuthenticationResults.get(0);
        }

        return DefaultAuthenticationResult.failed(dittoHeaders,
                authenticationFailureAggregator.aggregateAuthenticationFailures(failedAuthenticationResults));
    }

}
