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
package org.eclipse.ditto.services.gateway.security.authentication;

import static org.eclipse.ditto.model.base.common.ConditionChecker.argumentNotEmpty;
import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.http.javadsl.model.Uri;
import akka.http.javadsl.server.RequestContext;

/**
 * Handles authentication by multiple authentication providers.
 */
@Immutable
public final class AuthenticationChain {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationChain.class);
    private final Collection<AuthenticationProvider> authenticationProviderChain;
    private final Executor blockingDispatcher;
    private final AuthenticationFailureAggregator authenticationFailureAggregator;

    private AuthenticationChain(final Collection<AuthenticationProvider> authenticationProviders,
            final AuthenticationFailureAggregator authenticationFailureAggregator,
            final Executor blockingDispatcher) {
        checkNotNull(authenticationProviders, "authenticationProviders");
        argumentNotEmpty(authenticationProviders, "authenticationProviders");
        this.authenticationProviderChain = authenticationProviders;
        this.authenticationFailureAggregator =
                checkNotNull(authenticationFailureAggregator, "authenticationFailureAggregator");
        this.blockingDispatcher = checkNotNull(blockingDispatcher, "blockingDispatcher");
    }

    /**
     * Builds a new instance of {@link AuthenticationChain}.
     *
     * @param authenticationProviders the list of authentication providers that should be used in the given order.
     * @param authenticationFailureAggregator aggregates multiple failed authentication results to a single
     * {@link org.eclipse.ditto.model.base.exceptions.DittoRuntimeException ditto runtime exception } in case multiple
     * {@link AuthenticationProvider authentication providers} in the given collection of authentication providers were
     * applicable to a request and all of them failed.
     * @param blockingDispatcher dispatcher used for blocking calls.
     * @return the new instance of {@link AuthenticationChain}.
     */
    public static AuthenticationChain getInstance(final Collection<AuthenticationProvider> authenticationProviders,
            final AuthenticationFailureAggregator authenticationFailureAggregator,
            final Executor blockingDispatcher) {
        return new AuthenticationChain(authenticationProviders, authenticationFailureAggregator, blockingDispatcher);
    }

    /**
     * Indicates whether this {@link AuthenticationChain authentication chain} contains any
     * {@link AuthenticationProvider authentication provider} that
     * {@link AuthenticationProvider#isApplicable(RequestContext) is applicable} for the given
     * {@link RequestContext request context}.
     *
     * @param requestContext the request context that should be authenticated.
     * @return true if this {@link AuthenticationChain authentication chain} contains any
     * {@link AuthenticationProvider authentication provider}. False if not.
     */
    public boolean isApplicable(final RequestContext requestContext) {
        return authenticationProviderChain.stream()
                .anyMatch(authenticationProvider -> authenticationProvider.isApplicable(requestContext));
    }

    /**
     * Authenticates the given {@link RequestContext request context} and returns the
     * {@link AuthenticationResult result}.
     *
     * @param requestContext the request context that should be authenticated.
     * @param correlationId the correlation id of the request.
     * @return A future resolving to the {@link AuthenticationResult authentication result}.
     */
    public CompletableFuture<AuthenticationResult> authenticate(final RequestContext requestContext,
            final String correlationId) {

        return CompletableFuture
                .runAsync(() -> LogUtil.enhanceLogWithCorrelationId(correlationId))
                .thenApply(voidValue -> doAuthenticate(requestContext, correlationId));
    }

    private AuthenticationResult doAuthenticate(final RequestContext requestContext, final String correlationId) {
        final Uri requestUri = requestContext.getRequest().getUri();

        final List<AuthenticationResult> failedAuthenticationResults = new ArrayList<>();

        for (AuthenticationProvider authenticationProvider : authenticationProviderChain) {
            if (!authenticationProvider.isApplicable(requestContext)) {
                continue;
            }

            LOGGER.debug("Applying authentication provider '{}' to URI '{}'",
                    authenticationProvider.getClass().getSimpleName(), requestUri);
            final CompletableFuture<AuthenticationResult> authenticationResultFuture =
                    authenticationProvider.extractAuthentication(requestContext, correlationId, blockingDispatcher);
            final AuthenticationResultWaiter authenticationResultSupplier =
                    AuthenticationResultWaiter.of(authenticationResultFuture, correlationId);
            final AuthenticationResult authenticationResult = authenticationResultSupplier.get();

            if (authenticationResult.isSuccess()) {
                LOGGER.debug("Authentication using authentication provider '{}' to URI '{}' was successful.",
                        authenticationProvider.getClass().getSimpleName(), requestUri);
                return authenticationResult;
            } else {
                LOGGER.debug("Authentication using authentication provider '{}' to URI '{}' failed.",
                        authenticationProvider.getClass().getSimpleName(), requestUri,
                        authenticationResult.getReasonOfFailure());
                failedAuthenticationResults.add(authenticationResult);
            }
        }

        if (failedAuthenticationResults.isEmpty()) {
            return DefaultAuthenticationResult.failed(
                    new IllegalStateException("No applicable authentication provider was found. " +
                            "Check with 'isApplicable' before calling 'authenticate'."));
        }

        if (failedAuthenticationResults.size() == 1) {
            return failedAuthenticationResults.get(0);
        }

        final DittoRuntimeException aggregatedAuthenticationFailures =
                authenticationFailureAggregator.aggregateAuthenticationFailures(failedAuthenticationResults);

        return DefaultAuthenticationResult.failed(aggregatedAuthenticationFailures);
    }


}
