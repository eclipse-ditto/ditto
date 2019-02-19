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

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.services.utils.akka.LogUtil;
import org.eclipse.ditto.services.utils.metrics.instruments.timer.StartedTimer;
import org.eclipse.ditto.services.utils.tracing.TraceUtils;
import org.eclipse.ditto.services.utils.tracing.TracingTags;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayAuthenticationProviderUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.http.javadsl.server.RequestContext;

/**
 * An abstract class that measures the time it takes to authenticate a request.
 *
 * @param <R> the type of the AuthenticationResult implementation.
 */
@Immutable
public abstract class TimeMeasuringAuthenticationProvider<R extends AuthenticationResult>
        implements AuthenticationProvider<R> {

    private static final Logger LOGGER = LoggerFactory.getLogger(TimeMeasuringAuthenticationProvider.class);

    private static final String AUTH_ERROR_TAG = TracingTags.AUTH_ERROR;
    private static final String AUTH_SUCCESS_TAG = TracingTags.AUTH_SUCCESS;

    /**
     * Authenticates the given {@link RequestContext request context} and measures the time it took to complete the
     * authentication.
     *
     * @param requestContext the request context to authenticate.
     * @param correlationId the correlation id of the request.
     * @param blockingDispatcher dispatcher used for blocking calls.
     * @return A future resolving to an authentication result.
     */
    @Override
    public final CompletableFuture<R> extractAuthentication(final RequestContext requestContext,
            final String correlationId, final Executor blockingDispatcher) {
        final StartedTimer timer = TraceUtils
                .newAuthFilterTimer(getType())
                .build();

        return CompletableFuture.runAsync(() -> LogUtil.enhanceLogWithCorrelationId(correlationId), blockingDispatcher)
                .thenCompose(voidValue -> doExtractAuthentication(requestContext, correlationId, blockingDispatcher))
                .thenApply(result -> {
                    timer.tag(AUTH_SUCCESS_TAG, result.isSuccess()).stop();
                    return result;
                }).exceptionally(throwable -> {
                    timer.tag(AUTH_SUCCESS_TAG, false).tag(AUTH_ERROR_TAG, true).stop();
                    return toFailedAuthenticationResult(throwable, correlationId);
                }).thenApply(result -> {
                    if (!result.isSuccess()) {
                        final Throwable reasonOfFailure = result.getReasonOfFailure();
                        LOGGER.info("Authentication failed with Exception of type '{}' with message '{}'",
                                reasonOfFailure.getClass().getName(), reasonOfFailure.getMessage());
                    }
                    return result;
                });
    }

    /**
     * Used to identify the authentication provider in order to distinguish measured metrics for this authentication provider.
     *
     * @return the type of this authentication provider. For example "JWT".
     */
    protected abstract String getType();

    /**
     * Authenticates the given {@link RequestContext request context}.
     *
     * @param requestContext the request context to authenticate.
     * @param correlationId the correlation id of the request.
     * @param blockingDispatcher dispatcher used for blocking calls.
     * @return A future resolving to an authentication result.
     */
    protected abstract CompletableFuture<R> doExtractAuthentication(RequestContext requestContext,
            String correlationId, Executor blockingDispatcher);

    /**
     * Creates failed authentication result with a {@link AuthenticationResult#getReasonOfFailure() reason of failure}
     * based on the given throwable.
     *
     * @param throwable the throwable that caused a failure.
     * @param correlationId the correlationId to append to the failed result.
     * @return a failed authentication result holding the extracted reason of failure.
     */
    protected abstract R toFailedAuthenticationResult(Throwable throwable, final String correlationId);


    /**
     * Converts the given {@link Throwable} to a {@link DittoRuntimeException} either by returning the
     * ditto runtime exception hold as a cause or by building a
     * {@link GatewayAuthenticationProviderUnavailableException} with the given throwable as cause (Unwrapped in case
     * the throwable is of type {@link CompletionException}).
     *
     * @param throwable the throwable to convert to a ditto runtime exception.
     * @param correlationId the correlation id of the request that caused the given throwable.
     * @return the converted ditto runtime exception.
     */
    protected DittoRuntimeException toDittoRuntimeException(final Throwable throwable, final String correlationId) {
        final Throwable throwableToMap = unwrapCompletionException(throwable);
        return unwrapDittoRuntimeException(throwableToMap, correlationId)
                .orElse(buildInternalErrorException(throwableToMap, correlationId));
    }

    private Throwable unwrapCompletionException(final Throwable potentialExecutionException) {
        if (potentialExecutionException instanceof CompletionException &&
                potentialExecutionException.getCause() != null) {
            return potentialExecutionException.getCause();
        }

        return potentialExecutionException;
    }

    private Optional<DittoRuntimeException> unwrapDittoRuntimeException(final Throwable throwable,
            final String correlationId) {
        if (throwable == null) {
            return Optional.empty();
        }

        if (throwable instanceof DittoRuntimeException) {
            final DittoRuntimeException dre = (DittoRuntimeException) throwable;
            if (dre.getDittoHeaders().getCorrelationId().isPresent()) {
                return Optional.of(dre);
            }

            final DittoRuntimeException dreWithEnsuredCorrelationId =
                    dre.setDittoHeaders(dre.getDittoHeaders().toBuilder().correlationId(correlationId).build());
            return Optional.of(dreWithEnsuredCorrelationId);
        }

        return unwrapDittoRuntimeException(throwable.getCause(), correlationId);
    }

    protected static DittoRuntimeException buildInternalErrorException(final Throwable cause,
            final String correlationId) {
        return GatewayAuthenticationProviderUnavailableException.newBuilder()
                .dittoHeaders(DittoHeaders.newBuilder().correlationId(correlationId).build())
                .cause(cause)
                .build();
    }
}
