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
package org.eclipse.ditto.gateway.service.endpoints.directives;

import static akka.http.javadsl.server.Directives.extractRequest;
import static akka.http.javadsl.server.Directives.logRequest;
import static akka.http.javadsl.server.Directives.logResult;
import static akka.http.javadsl.server.Directives.mapRouteResult;
import static org.eclipse.ditto.gateway.service.endpoints.directives.RequestLoggingFilter.filterHeaders;
import static org.eclipse.ditto.gateway.service.endpoints.directives.RequestLoggingFilter.filterRawUri;
import static org.eclipse.ditto.gateway.service.endpoints.directives.RequestLoggingFilter.filterUri;

import java.util.function.Supplier;

import org.eclipse.ditto.gateway.service.endpoints.utils.HttpUtils;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLogger;

import akka.http.javadsl.server.Complete;
import akka.http.javadsl.server.Route;

/**
 * Custom Akka Http directive logging the StatusCode and duration of the route.
 */
public final class RequestResultLoggingDirective {

    private static final String DITTO_TRACE_HEADERS = "ditto-trace-headers";
    private static final ThreadSafeDittoLogger LOGGER =
            DittoLoggerFactory.getThreadSafeLogger(RequestResultLoggingDirective.class);
    private static final ThreadSafeDittoLogger TRACE_LOGGER = DittoLoggerFactory.getThreadSafeLogger(
            RequestResultLoggingDirective.class.getName() + "." + DITTO_TRACE_HEADERS);

    private RequestResultLoggingDirective() {
        throw new AssertionError();
    }

    /**
     * Logs the StatusCode and duration of the route.
     *
     * @param correlationId the correlationId which will be added to the log
     * @param inner the inner Route to be logged
     * @return the new Route wrapping {@code inner} with logging
     */
    public static Route logRequestResult(final CharSequence correlationId, final Supplier<Route> inner) {
        // add akka standard logging to the route
        final Supplier<Route> innerWithAkkaLoggingRoute = () -> logRequest("http-request", () ->
                logResult("http-response", inner));

        // add our own logging with time measurement and creating a kamon trace
        // code is inspired by DebuggingDirectives#logRequestResult
        return extractRequest(request -> {
            final String requestMethod = request.method().name();
            final String filteredRelativeRequestUri = filterUri(request.getUri().toRelative()).toString();
            return mapRouteResult(routeResult -> {
                final ThreadSafeDittoLogger logger = LOGGER.withCorrelationId(correlationId);
                if (routeResult instanceof Complete) {
                    final Complete complete = (Complete) routeResult;
                    final int statusCode = complete.getResponse().status().intValue();
                    logger.info("StatusCode of request {} '{}' was: {}", requestMethod, filteredRelativeRequestUri,
                            statusCode);
                    if (logger.isDebugEnabled()) {
                        final String filteredRawRequestUri = filterRawUri(HttpUtils.getRawRequestUri(request));
                        logger.debug("Raw request URI was: {}", filteredRawRequestUri);
                    }
                    request.getHeader(DITTO_TRACE_HEADERS)
                            .filter(unused -> TRACE_LOGGER.isDebugEnabled())
                            .ifPresent(unused -> TRACE_LOGGER.withCorrelationId(correlationId)
                                    .debug("Request headers: {}", filterHeaders(request.getHeaders())));
                } else {
                         /* routeResult could be Rejected, if no route is able to handle the request -> but this should
                            not happen when rejections are handled before this directive is called. */
                    logger.warn("Unexpected routeResult for request {} '{}': {}, routeResult will be handled by " +
                                    "akka default RejectionHandler.", requestMethod, filteredRelativeRequestUri,
                            routeResult);
                }

                return routeResult;
            }, innerWithAkkaLoggingRoute);
        });
    }

}
