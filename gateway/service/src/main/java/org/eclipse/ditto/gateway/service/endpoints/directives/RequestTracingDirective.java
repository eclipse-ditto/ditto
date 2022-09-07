/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
import static akka.http.javadsl.server.Directives.mapRequest;
import static akka.http.javadsl.server.Directives.mapRouteResult;
import static org.eclipse.ditto.gateway.service.endpoints.directives.RequestLoggingFilter.filterUri;

import java.util.Map;
import java.util.function.Supplier;

import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.internal.utils.tracing.DittoTracing;
import org.eclipse.ditto.internal.utils.tracing.TraceUriGenerator;
import org.eclipse.ditto.internal.utils.tracing.TracingTags;
import org.eclipse.ditto.internal.utils.tracing.instruments.trace.StartedTrace;

import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.server.Complete;
import akka.http.javadsl.server.Route;
import akka.http.javadsl.server.RouteResult;
import kamon.context.Context;

/**
 * Custom Akka Http directive tracing the request.
 */
public final class RequestTracingDirective {

    private static final TraceUriGenerator TRACE_URI_GENERATOR = TraceUriGenerator.getInstance();

    private RequestTracingDirective() {
        throw new AssertionError();
    }

    /**
     * Starts and finishes a new trace for every request.
     *
     * @param correlationId the correlationId which will be added to the log
     * @param inner the inner Route to be traced
     * @return the new Route wrapping {@code inner} with tracing
     */
    public static Route traceRequest(final CharSequence correlationId, final Supplier<Route> inner) {
        return extractRequest(request -> {
            final Context context = DittoTracing.extractTraceContext(request);
            // creates new context if it was empty
            final String requestMethod = request.method().name();
            final String contextName = String.format("%s %s", requestMethod,
                    TRACE_URI_GENERATOR.apply(request.getUri().toRelative().path()).getTraceUri());
            final StartedTrace startedTrace = DittoTracing
                    .trace(context, contextName)
                    .correlationId(correlationId)
                    .start();
            final String filteredRelativeRequestUri = filterUri(request.getUri().toRelative()).toString();
            return mapRequest(
                    r -> addIfHeaderExists(r, DittoTracing.propagateContext(startedTrace.getContext()),
                            DittoHeaderDefinition.W3C_TRACEPARENT, DittoHeaderDefinition.W3C_TRACESTATE),
                    () -> mapRouteResult(
                            routeResult -> handleRouteResult(startedTrace, requestMethod, filteredRelativeRequestUri,
                                    routeResult), inner)
            );
        });
    }

    private static RouteResult handleRouteResult(final StartedTrace startedTrace, final String requestMethod,
            final String filteredRelativeRequestUri, final RouteResult routeResult) {
        try {
            if (routeResult instanceof Complete complete) {
                final int statusCode = complete.getResponse().status().intValue();
                startedTrace.tag(TracingTags.REQUEST_METHOD, requestMethod);
                startedTrace.tag(TracingTags.STATUS_CODE, statusCode);
                startedTrace.tag(TracingTags.REQUEST_PATH, filteredRelativeRequestUri);
            } else if (routeResult != null) {
                startedTrace.fail("Request rejected: " + routeResult.getClass().getName());
            } else {
                startedTrace.fail("Request failed.");
            }
        } catch (final Exception exception) {
            startedTrace.fail(exception);
        } finally {
            startedTrace.finish();
        }
        return routeResult;
    }

    private static HttpRequest addIfHeaderExists(final HttpRequest originalRequest,
            final Map<String, String> headers, final DittoHeaderDefinition... keys) {
        HttpRequest result = originalRequest;
        for (final DittoHeaderDefinition key : keys) {
            if (headers.containsKey(key.getKey())) {
                final String value = headers.get(key.getKey());
                result = result.addHeader(HttpHeader.parse(key.getKey(), value));
            }
        }
        return result;
    }
}
