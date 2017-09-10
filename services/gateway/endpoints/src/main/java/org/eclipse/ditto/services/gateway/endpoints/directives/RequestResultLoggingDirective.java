/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.gateway.endpoints.directives;

import static akka.http.javadsl.server.Directives.extractRequestContext;
import static akka.http.javadsl.server.Directives.logRequest;
import static akka.http.javadsl.server.Directives.logResult;
import static akka.http.javadsl.server.Directives.mapRouteResult;

import java.util.function.Supplier;

import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.services.gateway.endpoints.utils.DirectivesLoggingUtils;
import org.eclipse.ditto.services.gateway.endpoints.utils.HttpUtils;
import org.eclipse.ditto.services.gateway.endpoints.utils.TraceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.server.Complete;
import akka.http.javadsl.server.Route;

/**
 * Custom Akka Http directive logging the StatusCode and duration of the route.
 */
public final class RequestResultLoggingDirective {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestResultLoggingDirective.class);

    private static final int UNKNOWN_STATUS_CODE = HttpStatusCode.IM_A_TEAPOT.toInt();

    private RequestResultLoggingDirective() {
        // no op
    }

    /**
     * Logs the StatusCode and duration of the route.
     *
     * @param correlationId the correlationId which will be added to the log
     * @param inner the inner Route to be logged
     * @return the new Route wrapping {@code inner} with logging
     */
    public static Route logRequestResult(final String correlationId, final Supplier<Route> inner) {
        // add akka standard logging to the route
        final Supplier<Route> innerWithAkkaLoggingRoute = () -> logRequest("http-request", () ->
                logResult("http-response", inner));

        // add our own logging with time measurement and creating a kamon trace
        // code is inspired by DebuggingDirectives#logRequestResult
        return extractRequestContext(requestContext -> {
            final long startNanos = System.nanoTime();
            final HttpRequest request = requestContext.getRequest();
            final String requestMethod = request.method().name();
            final String requestUri = request.getUri().toRelative().toString();
            return mapRouteResult(
                    routeResult -> DirectivesLoggingUtils.enhanceLogWithCorrelationId(correlationId, () -> {
                if (routeResult instanceof Complete) {
                    final Complete complete = (Complete) routeResult;
                    final int statusCode = complete.getResponse().status().intValue();
                    LOGGER.info("StatusCode of request {} '{}' was: {}", requestMethod, requestUri, statusCode);
                    final String rawRequestUri = HttpUtils.getRawRequestUri(request);
                    LOGGER.debug("Raw request URI was: {}", rawRequestUri);

                    TraceUtils.createTrace(correlationId, startNanos, requestContext, statusCode);
                } else {
                /* routeResult could be Rejected, if no route is able to handle the request -> but this should
                   not happen when rejections are handled before this directive is called. */
                    LOGGER.warn("Unexpected routeResult for request {} '{}': {}, routeResult will be handled by " +
                                    "akka default RejectionHandler.", requestMethod, requestUri,
                            routeResult);
                    TraceUtils.createTrace(correlationId, startNanos, requestContext, UNKNOWN_STATUS_CODE);
                }

                return routeResult;
            }), innerWithAkkaLoggingRoute);
        });
    }

}
