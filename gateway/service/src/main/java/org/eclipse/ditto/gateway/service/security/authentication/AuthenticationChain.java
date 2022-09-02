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
package org.eclipse.ditto.gateway.service.security.authentication;

import static org.eclipse.ditto.base.model.common.ConditionChecker.argumentNotEmpty;
import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Stream;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLogger;

import akka.http.javadsl.server.RequestContext;

/**
 * Handles authentication by multiple authentication providers.
 */
@Immutable
public final class AuthenticationChain {

    private static final ThreadSafeDittoLogger LOGGER = DittoLoggerFactory
            .getThreadSafeLogger(AuthenticationChain.class);

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
     * {@link org.eclipse.ditto.base.model.exceptions.DittoRuntimeException ditto runtime exception } in case multiple
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

        return authenticationProviderChain.stream()
                .reduce(
                        emptyAuthResultAccumulator(authenticationProviderChain, requestContext, dittoHeaders),
                        (future, provider) ->
                                future.thenComposeAsync(accumulator -> accumulator.andThen(provider),
                                        authenticationDispatcher),
                        (future1, future2) ->
                                future1.thenComposeAsync(accumulator -> accumulator.andThen(future2),
                                        authenticationDispatcher)
                )
                .thenApplyAsync(AuthResultAccumulator::getResult, authenticationDispatcher);
    }

    private CompletableFuture<AuthResultAccumulator> emptyAuthResultAccumulator(final Collection<?> authProviders,
            final RequestContext requestContext,
            final DittoHeaders dittoHeaders) {
        return CompletableFuture.completedFuture(
                new AuthResultAccumulator(null, new ArrayList<>(authProviders.size()), requestContext, dittoHeaders)
        );
    }

    private final class AuthResultAccumulator {

        @Nullable private final AuthenticationResult successResult;
        private final List<AuthenticationResult> failureResults;
        private final RequestContext requestContext;
        private final DittoHeaders dittoHeaders;

        private AuthResultAccumulator(
                @Nullable final AuthenticationResult successResult,
                final List<AuthenticationResult> failureResults,
                final RequestContext requestContext,
                final DittoHeaders dittoHeaders) {
            this.successResult = successResult;
            this.failureResults = Collections.unmodifiableList(failureResults);
            this.requestContext = requestContext;
            this.dittoHeaders = dittoHeaders;
        }

        // precondition: successResult == null
        private AuthResultAccumulator appendResult(final AuthenticationProvider<?> authenticationProvider,
                final AuthenticationResult nextResult) {
            if (nextResult.isSuccess()) {
                logSuccess(authenticationProvider);
                return new AuthResultAccumulator(nextResult, failureResults, requestContext, dittoHeaders);
            } else {
                logFailure(authenticationProvider, nextResult);
                final var newFailureResults =
                        Stream.concat(failureResults.stream(), Stream.of(nextResult)).toList();
                return new AuthResultAccumulator(successResult, newFailureResults, requestContext, dittoHeaders);
            }
        }

        private AuthResultAccumulator appendFailure(final AuthenticationProvider<?> provider,
                final Throwable throwable) {

            return appendResult(provider, DefaultAuthenticationResult.failed(dittoHeaders, throwable));
        }

        private void logSuccess(final AuthenticationProvider<?> provider) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.withCorrelationId(dittoHeaders)
                        .debug("Authentication using authentication provider <{}> to URI <{}> was successful.",
                                provider.getClass().getSimpleName(), requestContext.getRequest().getUri());
            }
        }

        private void logFailure(final AuthenticationProvider<?> provider, final AuthenticationResult result) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.withCorrelationId(dittoHeaders)
                        .debug("Authentication using authentication provider <{}> to URI <{}> failed due to {}: {}",
                                provider.getClass().getSimpleName(),
                                requestContext.getRequest().getUri(),
                                result.getReasonOfFailure().getClass().getSimpleName(),
                                result.getReasonOfFailure().getMessage());
            }
        }

        private CompletableFuture<AuthResultAccumulator> andThen(final CompletableFuture<AuthResultAccumulator> other) {
            if (successResult != null) {
                return CompletableFuture.completedFuture(this);
            } else {
                return other.thenApplyAsync(that -> {
                    final var newFailureResults =
                            Stream.concat(failureResults.stream(), that.failureResults.stream()).toList();
                    return new AuthResultAccumulator(that.successResult, newFailureResults, requestContext,
                            dittoHeaders);
                }, authenticationDispatcher);
            }
        }

        private CompletableFuture<AuthResultAccumulator> andThen(
                final AuthenticationProvider<?> authenticationProvider) {
            if (successResult == null && authenticationProvider.isApplicable(requestContext)) {
                try {
                    return authenticationProvider.authenticate(requestContext, dittoHeaders)
                            .thenApplyAsync(result -> appendResult(authenticationProvider, result),
                                    authenticationDispatcher)
                            .exceptionally(e -> appendFailure(authenticationProvider, e));
                } catch (final Throwable e) {
                    return CompletableFuture.completedFuture(appendFailure(authenticationProvider, e));
                }
            } else {
                return CompletableFuture.completedFuture(this);
            }
        }

        private AuthenticationResult asFailure() {
            if (failureResults.isEmpty()) {
                return DefaultAuthenticationResult.failed(dittoHeaders,
                        new IllegalStateException("No applicable authentication provider was found!"));
            }

            if (1 == failureResults.size()) {
                return failureResults.get(0);
            }

            return DefaultAuthenticationResult.failed(dittoHeaders,
                    authenticationFailureAggregator.aggregateAuthenticationFailures(failureResults));
        }

        /**
         * @return either the success result or the aggregated failure result.
         */
        private AuthenticationResult getResult() {
            return Objects.requireNonNullElseGet(successResult, this::asFailure);
        }
    }

}
