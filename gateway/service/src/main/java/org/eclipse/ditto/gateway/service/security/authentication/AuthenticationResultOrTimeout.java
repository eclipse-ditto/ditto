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

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.gateway.api.GatewayAuthenticationProviderUnavailableException;

/**
 * Waits for the future holding an {@link AuthenticationResult} and returns a failed authentication result with
 * {@link GatewayAuthenticationProviderUnavailableException} as reason if this takes longer than defined in
 * {@link AuthenticationResultOrTimeout#AWAIT_AUTH_TIMEOUT}.
 * <p>
 */
@ThreadSafe
public final class AuthenticationResultOrTimeout<R extends AuthenticationResult>
        implements Supplier<CompletableFuture<R>> {

    private static final Duration AWAIT_AUTH_TIMEOUT = Duration.ofSeconds(5L);

    private final CompletableFuture<R> authenticationResultFuture;
    private final DittoHeaders dittoHeaders;
    private final Duration awaitAuthTimeout;

    private AuthenticationResultOrTimeout(final CompletableFuture<R> authenticationResultFuture,
            final DittoHeaders dittoHeaders, final Duration awaitAuthTimeout) {

        this.authenticationResultFuture = authenticationResultFuture;
        this.dittoHeaders = dittoHeaders;
        this.awaitAuthTimeout = awaitAuthTimeout;
    }

    /**
     * Returns an instance of this class holding the given future.
     *
     * @param authenticationResultFuture the Future that should eventually resolve to an authentication result.
     * @param dittoHeaders the correlation ID for this authentication.
     * @param <R> the type of the AuthenticationResult.
     * @return the created instance.
     */
    public static <R extends AuthenticationResult> AuthenticationResultOrTimeout<R> of(
            final CompletableFuture<R> authenticationResultFuture, final DittoHeaders dittoHeaders) {

        return new AuthenticationResultOrTimeout<>(authenticationResultFuture, dittoHeaders, AWAIT_AUTH_TIMEOUT);
    }

    /**
     * Waits for {@link #authenticationResultFuture} to be completed.
     *
     * @return the authentication result
     * @throws GatewayAuthenticationProviderUnavailableException if this takes longer than defined in
     * {@link AuthenticationResultOrTimeout#awaitAuthTimeout}.
     */
    @Override
    public CompletableFuture<R> get() {
        return authenticationResultFuture
                .orTimeout(awaitAuthTimeout.toMillis(), TimeUnit.MILLISECONDS)
                .exceptionally(ex -> {
                    throw mapException(ex);
                });
    }

    private GatewayAuthenticationProviderUnavailableException mapException(@Nullable final Throwable cause) {
        return GatewayAuthenticationProviderUnavailableException.newBuilder()
                .dittoHeaders(dittoHeaders)
                .cause(cause)
                .build();
    }

}
