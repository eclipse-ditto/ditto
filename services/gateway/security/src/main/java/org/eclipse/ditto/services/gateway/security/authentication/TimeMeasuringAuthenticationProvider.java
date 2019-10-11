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

import java.util.concurrent.CompletionException;
import java.util.concurrent.Future;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
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

    private static final String AUTH_ERROR_TAG = TracingTags.AUTH_ERROR;
    private static final String AUTH_SUCCESS_TAG = TracingTags.AUTH_SUCCESS;

    private static final Logger LOGGER = LoggerFactory.getLogger(TimeMeasuringAuthenticationProvider.class);

    @Override
    public final R authenticate(final RequestContext requestContext, final CharSequence correlationId) {
        final StartedTimer timer = TraceUtils.newAuthFilterTimer(getType()).build();
        try {
            final R authenticationResult = tryToAuthenticate(requestContext, correlationId);
            timer.tag(AUTH_SUCCESS_TAG, authenticationResult.isSuccess());
            return authenticationResult;
        } catch (final DittoRuntimeException e) {
            timer.tag(AUTH_SUCCESS_TAG, false);
            if (e.getStatusCode().isInternalError()) {
                LOGGER.warn("An unexpected error occurred during authentication of type <{}>.", getType(), e);
                timer.tag(AUTH_ERROR_TAG, true);
            }
            return toFailedAuthenticationResult(e, correlationId);
        } catch (final Exception e) {
            timer.tag(AUTH_SUCCESS_TAG, false).tag(AUTH_ERROR_TAG, true);
            return toFailedAuthenticationResult(e, correlationId);
        } finally {
            timer.stop();
        }
    }

    /**
     * Used to identify the authentication provider in order to distinguish measured metrics for this authentication
     * provider.
     *
     * @return the type of this authentication provider. For example "JWT".
     */
    protected abstract String getType();

    /**
     * Authenticates the given {@link RequestContext request context}.
     *
     * @param requestContext the request context to authenticate.
     * @param correlationId the correlation ID of the request.
     * @return the authentication result.
     */
    protected abstract R tryToAuthenticate(RequestContext requestContext, CharSequence correlationId);

    /**
     * Creates failed authentication result with a {@link AuthenticationResult#getReasonOfFailure() reason of failure}
     * based on the given throwable.
     *
     * @param throwable the throwable that caused a failure.
     * @param correlationId the correlation ID to append to the failed result.
     * @return a failed authentication result holding the extracted reason of failure.
     */
    protected abstract R toFailedAuthenticationResult(Throwable throwable, CharSequence correlationId);

    /**
     * Converts the given {@link Throwable} to a {@link DittoRuntimeException} either by returning the
     * ditto runtime exception hold as a cause or by building a
     * {@link GatewayAuthenticationProviderUnavailableException} with the given throwable as cause (Unwrapped in case
     * the throwable is of type {@link CompletionException}).
     *
     * @param throwable the throwable to convert to a {@link org.eclipse.ditto.model.base.exceptions.DittoRuntimeException}.
     * @param correlationId the correlation ID of the request that caused the given throwable.
     * @return the converted exception.
     */
    protected static DittoRuntimeException toDittoRuntimeException(final Throwable throwable,
            final CharSequence correlationId) {

        return DittoRuntimeException.asDittoRuntimeException(throwable,
                cause -> {
                    final DittoRuntimeException dittoRuntimeException =
                            unwrapDittoRuntimeException(cause, correlationId);

                    if (dittoRuntimeException == null) {
                        LOGGER.warn("Failed to unwrap DittoRuntimeException from Throwable!", throwable);
                        return buildInternalErrorException(cause, correlationId);
                    }

                    return dittoRuntimeException;
                });
    }

    private static DittoRuntimeException unwrapDittoRuntimeException(final Throwable throwable,
            final CharSequence correlationId) {

        if (null == throwable) {
            return null;
        }

        if (throwable instanceof DittoRuntimeException) {
            final DittoRuntimeException dre = (DittoRuntimeException) throwable;
            if (dre.getDittoHeaders().getCorrelationId().isPresent()) {
                return dre;
            }
            return dre.setDittoHeaders(dre.getDittoHeaders().toBuilder().correlationId(correlationId).build());
        }

        return unwrapDittoRuntimeException(throwable.getCause(), correlationId);
    }

    protected R waitForResult(final Future<R> authenticationResultFuture, final CharSequence correlationId) {
        return AuthenticationResultWaiter.of(authenticationResultFuture, correlationId).get();
    }

    protected static DittoRuntimeException buildInternalErrorException(final Throwable cause,
            final CharSequence correlationId) {

        return GatewayAuthenticationProviderUnavailableException.newBuilder()
                .dittoHeaders(DittoHeaders.newBuilder().correlationId(correlationId).build())
                .cause(cause)
                .build();
    }

}
