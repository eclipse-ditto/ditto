/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.gateway.service.endpoints.routes;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.text.MessageFormat;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.function.Function;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonRuntimeException;
import org.eclipse.ditto.base.model.exceptions.DittoJsonException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLogger;

import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.Directives;
import akka.http.javadsl.server.ExceptionHandler;
import akka.http.javadsl.server.Route;
import akka.japi.pf.FI;

/**
 * This class provides an {@link ExceptionHandler} for the root route.
 */
@Immutable
public final class RootRouteExceptionHandler {

    private static final ThreadSafeDittoLogger LOGGER = DittoLoggerFactory
            .getThreadSafeLogger(RootRouteExceptionHandler.class);

    private final Function<DittoRuntimeException, HttpResponse> dreToHttpResponse;

    private RootRouteExceptionHandler(final Function<DittoRuntimeException, HttpResponse> dreToHttpResponse) {
        this.dreToHttpResponse = checkNotNull(dreToHttpResponse, "dittoRuntimeExceptionToHttpResponse");
    }

    /**
     * Returns an instance of an {@link ExceptionHandler} for the root route.
     *
     * @param dittoRuntimeExceptionToHttpResponse this function is used to convert a DittoRuntimeException to a
     * HttpResponse.
     * @return the instance.
     * @throws NullPointerException if {@code dittoRuntimeExceptionToHttpResponse} is {@code null}.
     */
    public static ExceptionHandler getInstance(
            final Function<DittoRuntimeException, HttpResponse> dittoRuntimeExceptionToHttpResponse) {

        final RootRouteExceptionHandler rootRouteExceptionHandler =
                new RootRouteExceptionHandler(dittoRuntimeExceptionToHttpResponse);

        final FI.TypedPredicate<CompletionException> isCausedByDittoRuntimeException = exception -> {
            @Nullable final Throwable cause = exception.getCause();
            return cause instanceof DittoRuntimeException;
        };

        final FI.TypedPredicate<CompletionException> isCausedByJsonRuntimeException = exception -> {
            @Nullable final Throwable cause = exception.getCause();
            return cause instanceof JsonRuntimeException;
        };

        return ExceptionHandler.newBuilder()
                .match(DittoRuntimeException.class, rootRouteExceptionHandler::handleDittoRuntimeException)
                .match(JsonRuntimeException.class, jsonRuntimeException -> {
                    final DittoRuntimeException dittoJsonException = new DittoJsonException(jsonRuntimeException);
                    return rootRouteExceptionHandler.handleDittoRuntimeException(dittoJsonException);
                })
                .match(CompletionException.class, isCausedByDittoRuntimeException, ce -> {
                    final DittoRuntimeException dittoRuntimeException = (DittoRuntimeException) ce.getCause();
                    return rootRouteExceptionHandler.handleDittoRuntimeException(dittoRuntimeException);
                })
                .match(CompletionException.class, isCausedByJsonRuntimeException, ce -> {
                    final JsonRuntimeException jsonRuntimeException = (JsonRuntimeException) ce.getCause();
                    final DittoRuntimeException dittoRuntimeException = new DittoJsonException(jsonRuntimeException);
                    return rootRouteExceptionHandler.handleDittoRuntimeException(dittoRuntimeException);
                })
                .matchAny(RootRouteExceptionHandler::handleInternalServerError)
                .build();
    }

    private Route handleDittoRuntimeException(final DittoRuntimeException dittoRuntimeException) {
        logException(dittoRuntimeException);
        return Directives.complete(dreToHttpResponse.apply(dittoRuntimeException));
    }

    private static void logException(final DittoRuntimeException exception) {
        final DittoHeaders dittoHeaders = exception.getDittoHeaders();
        final Optional<String> correlationIdOptional = dittoHeaders.getCorrelationId();
        final String simpleExceptionName = exception.getClass().getSimpleName();
        if (correlationIdOptional.isEmpty()) {
            LOGGER.warn("Correlation ID was missing in headers of <{}>!", simpleExceptionName);
        }

        // We do not want to print stack trace of exception thus exception must not be last argument of logger call.
        final String message = MessageFormat.format("<{0}> occurred in gateway root route: <{1}>!", simpleExceptionName,
                exception.toString());
        LOGGER.withCorrelationId(dittoHeaders).info(message);
    }

    private static Route handleInternalServerError(final Throwable cause) {
        LOGGER.error("Unexpected exception in gateway root route: <{}>!", cause.getMessage(), cause);
        return Directives.complete(StatusCodes.INTERNAL_SERVER_ERROR);
    }

}
