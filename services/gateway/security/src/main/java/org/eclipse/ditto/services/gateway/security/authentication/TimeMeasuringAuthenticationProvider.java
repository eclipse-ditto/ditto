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

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.concurrent.CompletionException;
import java.util.concurrent.Future;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.auth.AuthorizationContextType;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.services.utils.akka.logging.DittoLogger;
import org.eclipse.ditto.services.utils.metrics.instruments.timer.StartedTimer;
import org.eclipse.ditto.services.utils.tracing.TraceUtils;
import org.eclipse.ditto.services.utils.tracing.TracingTags;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayAuthenticationProviderUnavailableException;

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

    private final DittoLogger logger;

    /**
     * Constructs a new TimeMeasuringAuthenticationProvider object.
     *
     * @param logger the logger to be used.
     * @throws NullPointerException if {@code logger} is {@code null}.
     */
    protected TimeMeasuringAuthenticationProvider(final DittoLogger logger) {
        this.logger = checkNotNull(logger, "logger");
    }

    @Override
    public final R authenticate(final RequestContext requestContext, final DittoHeaders dittoHeaders) {
        final AuthorizationContextType authorizationContextType = getType(requestContext);
        final StartedTimer timer = TraceUtils.newAuthFilterTimer(authorizationContextType).build();
        try {
            final R authenticationResult = tryToAuthenticate(requestContext, dittoHeaders);
            timer.tag(AUTH_SUCCESS_TAG, authenticationResult.isSuccess());
            return authenticationResult;
        } catch (final DittoRuntimeException e) {
            timer.tag(AUTH_SUCCESS_TAG, false);
            if (isInternalError(e.getStatusCode())) {
                logger.withCorrelationId(dittoHeaders)
                        .warn("An unexpected error occurred during authentication of type <{}>.",
                                authorizationContextType, e);
                timer.tag(AUTH_ERROR_TAG, true);
            }
            return toFailedAuthenticationResult(e, dittoHeaders);
        } catch (final Exception e) {
            timer.tag(AUTH_SUCCESS_TAG, false).tag(AUTH_ERROR_TAG, true);
            return toFailedAuthenticationResult(e, dittoHeaders);
        } finally {
            timer.stop();
        }
    }

    private static boolean isInternalError(final HttpStatusCode httpStatusCode) {
        return httpStatusCode.isInternalError();
    }

    /**
     * Used to identify the authentication provider in order to distinguish measured metrics for this authentication
     * provider.
     *
     * @param requestContext the request context to authenticate.
     * @return the type of this authentication provider.
     */
    protected abstract AuthorizationContextType getType(RequestContext requestContext);

    /**
     * Authenticates the given {@link RequestContext request context}.
     *
     * @param requestContext the request context to authenticate.
     * @param dittoHeaders the (potentially not completely set) DittoHeaders of the request.
     * @return the authentication result.
     */
    protected abstract R tryToAuthenticate(RequestContext requestContext, DittoHeaders dittoHeaders);

    /**
     * Creates failed authentication result with a {@link AuthenticationResult#getReasonOfFailure() reason of failure}
     * based on the given throwable.
     *
     * @param throwable the throwable that caused a failure.
     * @param dittoHeaders the (potentially not completely set) DittoHeaders of the request.
     * @return a failed authentication result holding the extracted reason of failure.
     */
    protected abstract R toFailedAuthenticationResult(Throwable throwable, DittoHeaders dittoHeaders);

    /**
     * Converts the given {@link Throwable} to a {@link DittoRuntimeException} either by returning the
     * ditto runtime exception hold as a cause or by building a
     * {@link GatewayAuthenticationProviderUnavailableException} with the given throwable as cause (Unwrapped in case
     * the throwable is of type {@link CompletionException}).
     *
     * @param throwable the throwable to convert to a {@link org.eclipse.ditto.model.base.exceptions.DittoRuntimeException}.
     * @param dittoHeaders the built DittoHeaders of the request that caused the given throwable.
     * @return the converted exception.
     */
    protected DittoRuntimeException toDittoRuntimeException(final Throwable throwable,
            final DittoHeaders dittoHeaders) {

        return DittoRuntimeException.asDittoRuntimeException(throwable,
                cause -> {
                    final DittoRuntimeException dittoRuntimeException =
                            unwrapDittoRuntimeException(cause, dittoHeaders);

                    if (dittoRuntimeException == null) {
                        logger.withCorrelationId(dittoHeaders)
                                .warn("Failed to unwrap DittoRuntimeException from Throwable!", throwable);
                        return buildInternalErrorException(cause, dittoHeaders);
                    }

                    return dittoRuntimeException;
                });
    }

    private static DittoRuntimeException unwrapDittoRuntimeException(final Throwable throwable,
            final DittoHeaders dittoHeaders) {

        if (null == throwable) {
            return null;
        }

        if (throwable instanceof DittoRuntimeException) {
            final DittoRuntimeException dre = (DittoRuntimeException) throwable;
            if (dre.getDittoHeaders().getCorrelationId().isPresent()) {
                return dre;
            }
            return dre.setDittoHeaders(dittoHeaders);
        }

        return unwrapDittoRuntimeException(throwable.getCause(), dittoHeaders);
    }

    protected R waitForResult(final Future<R> authenticationResultFuture, final DittoHeaders dittoHeaders) {
        return AuthenticationResultWaiter.of(authenticationResultFuture, dittoHeaders).get();
    }

    protected static DittoRuntimeException buildInternalErrorException(final Throwable cause,
            final DittoHeaders dittoHeaders) {

        return GatewayAuthenticationProviderUnavailableException.newBuilder()
                .dittoHeaders(dittoHeaders)
                .cause(cause)
                .build();
    }

}
