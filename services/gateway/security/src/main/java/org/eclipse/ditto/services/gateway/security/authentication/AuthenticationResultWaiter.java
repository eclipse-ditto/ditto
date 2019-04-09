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

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import javax.annotation.concurrent.ThreadSafe;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayAuthenticationProviderUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Waits for the future holding an {@link AuthenticationResult} and returns a failed authentication result with
 * {@link GatewayAuthenticationProviderUnavailableException} as reason if this takes longer than defined in
 * {@link AuthenticationResultWaiter#AWAIT_AUTH_TIMEOUT}.
 */
@ThreadSafe
public final class AuthenticationResultWaiter<R extends AuthenticationResult>
        implements Supplier<AuthenticationResult> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationResultWaiter.class);

    private static final Duration AWAIT_AUTH_TIMEOUT = Duration.ofSeconds(5L);

    private final Future<R> authenticationResultFuture;
    private final CharSequence correlationId;
    private final Duration awaitAuthTimeout;

    private AuthenticationResultWaiter(final Future<R> authenticationResultFuture, final CharSequence correlationId,
            final Duration awaitAuthTimeout) {

        this.authenticationResultFuture = authenticationResultFuture;
        this.correlationId = correlationId;
        this.awaitAuthTimeout = awaitAuthTimeout;
    }

    /**
     * Returns an instance of this class holding the given future.
     *
     * @param authenticationResultFuture the Future that should eventually resolve to an authentication result.
     * @param correlationId the correlation ID for this authentication.
     * @return the created instance.
     */
    public static <R extends AuthenticationResult> AuthenticationResultWaiter<R> of(
            final Future<R> authenticationResultFuture, final CharSequence correlationId) {

        return new AuthenticationResultWaiter<>(authenticationResultFuture, correlationId, AWAIT_AUTH_TIMEOUT);
    }

    /**
     * Waits for {@link #authenticationResultFuture} to be completed.
     *
     * @return the authentication result
     * @throws GatewayAuthenticationProviderUnavailableException if this takes longer than defined in
     * {@link AuthenticationResultWaiter#awaitAuthTimeout}.
     */
    @Override
    public R get() {
        return tryToGetResult();
    }

    @SuppressWarnings({"squid:S2142", "squid:S2139"})
    private R tryToGetResult() {
        try {
            return getResult();
        } catch (final InterruptedException | ExecutionException | TimeoutException e) {
            LOGGER.warn("Error while waiting for authentication result!", e);
            throw GatewayAuthenticationProviderUnavailableException.newBuilder()
                    .dittoHeaders(DittoHeaders.newBuilder().correlationId(correlationId).build())
                    .cause(e)
                    .build();
        }
    }

    private R getResult() throws InterruptedException, ExecutionException, TimeoutException {
        LOGGER.debug("Waiting for authentication result ...");
        return authenticationResultFuture.get(awaitAuthTimeout.getSeconds(), TimeUnit.SECONDS);
    }

}
