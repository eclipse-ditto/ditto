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

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayAuthenticationProviderUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Waits for the future holding an {@link AuthenticationResult} and returns a failed authentication result with
 * {@link GatewayAuthenticationProviderUnavailableException} as reason if this takes longer than defined in
 * {@link AuthenticationResultWaiter#AWAIT_AUTH_TIMEOUT}.
 */
@Immutable
public final class AuthenticationResultWaiter implements Supplier<AuthenticationResult> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationResultWaiter.class);

    private static final Duration AWAIT_AUTH_TIMEOUT = Duration.ofSeconds(5L);

    private final Future<AuthenticationResult> authenticationResultFuture;
    private final String correlationId;
    private final Duration awaitAuthTimeout;

    private AuthenticationResultWaiter(final Future<AuthenticationResult> authenticationResultFuture,
            final String correlationId, final Duration awaitAuthTimeout) {
        this.authenticationResultFuture = authenticationResultFuture;
        this.correlationId = correlationId;
        this.awaitAuthTimeout = awaitAuthTimeout;
    }

    /**
     * Creates a new instance of {@link AuthenticationResultWaiter} holding the given future.
     *
     * @param authenticationResultFuture the future that should eventually resolve to an authentication result.
     * @param correlationId the correlation id for this authentication.
     * @return the created instance of {@link AuthenticationResultWaiter}.
     */
    public static AuthenticationResultWaiter of(Future<AuthenticationResult> authenticationResultFuture,
            final String correlationId) {
        return new AuthenticationResultWaiter(authenticationResultFuture, correlationId, AWAIT_AUTH_TIMEOUT);
    }

    /**
     * Waits for {@link #authenticationResultFuture} and returns a failed authentication result with
     * {@link GatewayAuthenticationProviderUnavailableException} as reason if this takes longer than defined in
     * {@link AuthenticationResultWaiter#awaitAuthTimeout}.
     *
     * @return the authentication result
     */
    @Override
    @SuppressWarnings("squid:S2142")
    public AuthenticationResult get() {
        try {
            LOGGER.debug("Waiting for authentication result");
            return authenticationResultFuture.get(awaitAuthTimeout.getSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            LOGGER.warn("Error while waiting for authentication result", e);
            return DefaultAuthenticationResult.failed(
                    GatewayAuthenticationProviderUnavailableException.newBuilder()
                            .dittoHeaders(DittoHeaders.newBuilder().correlationId(correlationId).build())
                            .cause(e)
                            .build()
            );
        }
    }
}
