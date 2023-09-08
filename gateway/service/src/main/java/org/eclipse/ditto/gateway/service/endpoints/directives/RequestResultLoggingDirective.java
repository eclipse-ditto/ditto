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

import static org.apache.pekko.http.javadsl.server.Directives.extractRequest;
import static org.apache.pekko.http.javadsl.server.Directives.logRequest;
import static org.apache.pekko.http.javadsl.server.Directives.logResult;
import static org.apache.pekko.http.javadsl.server.Directives.mapRouteResult;
import static org.eclipse.ditto.gateway.service.endpoints.directives.RequestLoggingFilter.filterHeaders;
import static org.eclipse.ditto.gateway.service.endpoints.directives.RequestLoggingFilter.filterRawUri;
import static org.eclipse.ditto.gateway.service.endpoints.directives.RequestLoggingFilter.filterUri;

import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.gateway.service.endpoints.utils.HttpUtils;
import org.eclipse.ditto.internal.utils.pekko.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.pekko.logging.ThreadSafeDittoLogger;

import org.apache.pekko.http.javadsl.model.HttpHeader;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.server.Complete;
import org.apache.pekko.http.javadsl.server.Route;

/**
 * Custom Pekko Http directive logging the StatusCode and duration of the route.
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
        // add pekko standard logging to the route
        final Supplier<Route> innerWithPekkoLoggingRoute = () -> logRequest("http-request", () ->
                logResult("http-response", inner));

        // add our own logging with time measurement and creating a kamon trace
        // code is inspired by DebuggingDirectives#logRequestResult
        return extractRequest(request -> {
            final String requestMethod = request.method().name();
            final String filteredRelativeRequestUri = filterUri(request.getUri().toRelative()).toString();
            return mapRouteResult(routeResult -> {
                final Map<String, String> headers = headersAsMap(request);
                headers.put(DittoHeaderDefinition.CORRELATION_ID.getKey(), correlationId.toString());
                final ThreadSafeDittoLogger logger = LOGGER.withCorrelationId(headers);
                if (routeResult instanceof Complete complete) {
                    final int statusCode = complete.getResponse().status().intValue();
                    logger.info("StatusCode of request {} '{}' was: {}", requestMethod, filteredRelativeRequestUri,
                            statusCode);
                    if (logger.isDebugEnabled()) {
                        final String filteredRawRequestUri = filterRawUri(HttpUtils.getRawRequestUri(request));
                        logger.debug("Raw request URI was: {}", filteredRawRequestUri);
                    }
                    request.getHeader(DITTO_TRACE_HEADERS)
                            .filter(unused -> TRACE_LOGGER.isDebugEnabled())
                            .ifPresent(unused -> TRACE_LOGGER.withCorrelationId(headers)
                                    .debug("Request headers: {}", filterHeaders(request.getHeaders())));
                } else {
                         /* routeResult could be Rejected, if no route is able to handle the request -> but this should
                            not happen when rejections are handled before this directive is called. */
                    logger.warn("Unexpected routeResult for request {} '{}': {}, routeResult will be handled by " +
                                    "pekko default RejectionHandler.", requestMethod, filteredRelativeRequestUri,
                            routeResult);
                }

                return routeResult;
            }, innerWithPekkoLoggingRoute);
        });
    }

    private static Map<String, String> headersAsMap(final HttpRequest request) {
        return StreamSupport.stream(request.getHeaders().spliterator(), false)
                .collect(Collectors.toMap(HttpHeader::name, HttpHeader::value));
    }
}
