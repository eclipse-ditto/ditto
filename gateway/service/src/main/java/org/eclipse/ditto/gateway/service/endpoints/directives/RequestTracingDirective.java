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

import java.text.MessageFormat;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.ConditionChecker;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.internal.utils.tracing.DittoTracing;
import org.eclipse.ditto.internal.utils.tracing.TraceUriGenerator;
import org.eclipse.ditto.internal.utils.tracing.span.SpanOperationName;
import org.eclipse.ditto.internal.utils.tracing.span.SpanTags;
import org.eclipse.ditto.internal.utils.tracing.span.StartedSpan;

import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.Uri;
import akka.http.javadsl.server.Complete;
import akka.http.javadsl.server.Route;
import akka.http.javadsl.server.RouteResult;

/**
 * Custom Akka Http directive tracing the request.
 */
@Immutable
public final class RequestTracingDirective {

    private final Set<SpanOperationName> disabledSpanOperationNames;
    private final TraceUriGenerator traceUriGenerator;

    private RequestTracingDirective(
            final Set<SpanOperationName> disabledSpanOperationNames,
            final TraceUriGenerator traceUriGenerator
    ) {
        this.disabledSpanOperationNames = Set.copyOf(disabledSpanOperationNames);
        this.traceUriGenerator = traceUriGenerator;
    }

    /**
     * Return new instance of {@code RequestTracingDirective} which disables tracing for operations with the specified
     * set of operation names.
     *
     * @param disabledSpanOperationNames names of operations for which no tracing is performed at all.
     * @return the new instance.
     * @throws NullPointerException if {@code disabledSpanOperationNames} is {@code null}.
     */
    public static RequestTracingDirective newInstanceWithDisabled(
            final Set<SpanOperationName> disabledSpanOperationNames
    ) {
        return new RequestTracingDirective(
                ConditionChecker.checkNotNull(disabledSpanOperationNames, "disabledSpanOperationNames"),
                TraceUriGenerator.getInstance()
        );
    }

    /**
     * Conditionally starts and finishes a new trace for every request.
     * If the resolved span operation name is contained in the known disabled span operation names then the inner
     * Route will be returned as is.
     *
     * @param innerRouteSupplier supplies the inner Route to be traced.
     * @param correlationId the correlation ID which will be added to the log or {@code null} if no correlation ID
     * should be added.
     * @return the new Route wrapping {@code inner} with tracing.
     * @throws NullPointerException if {@code inner} is {@code null}.
     */
    public Route traceRequest(final Supplier<Route> innerRouteSupplier, @Nullable final CharSequence correlationId) {
        ConditionChecker.checkNotNull(innerRouteSupplier, "innerRouteSupplier");
        return extractRequest(request -> {
            final Route result;
            final var operationName = resolveSpanOperationName(request);
            if (isTracingDisabledForOperationName(operationName)) {
                result = innerRouteSupplier.get();
            } else {
                result = getRouteWithEnabledTracing(
                        startTrace(getHttpHeadersAsMap(request.getHeaders()), operationName, correlationId),
                        request,
                        innerRouteSupplier
                );
            }
            return result;
        });
    }

    private SpanOperationName resolveSpanOperationName(final HttpRequest httpRequest) {
        return SpanOperationName.of(
                MessageFormat.format("{0} {1}", getTraceUri(httpRequest), getRequestMethodName(httpRequest))
        );
    }

    private String getTraceUri(final HttpRequest httpRequest) {
        final var traceInformation = traceUriGenerator.apply(String.valueOf(getRelativeUri(httpRequest)));
        return traceInformation.getTraceUri();
    }

    private static Uri getRelativeUri(final HttpRequest httpRequest) {
        final var uri = httpRequest.getUri();
        return uri.toRelative();
    }

    private static String getRequestMethodName(final HttpRequest httpRequest) {
        final var httpMethod = httpRequest.method();
        return httpMethod.name();
    }

    private boolean isTracingDisabledForOperationName(final SpanOperationName traceOperationName) {
        return disabledSpanOperationNames.contains(traceOperationName);
    }

    private static Map<String, String> getHttpHeadersAsMap(final Iterable<HttpHeader> httpHeaders) {
        return StreamSupport.stream(httpHeaders.spliterator(), false)
                .collect(Collectors.toMap(HttpHeader::name, HttpHeader::value, (oldValue, value) -> value));
    }

    private static StartedSpan startTrace(
            final Map<String, String> headersMap,
            final SpanOperationName traceOperationName,
            @Nullable final CharSequence correlationId
    ) {
        return DittoTracing.newPreparedSpan(headersMap, traceOperationName)
                .correlationId(correlationId)
                .start();
    }

    private static Route getRouteWithEnabledTracing(
            final StartedSpan startedTrace,
            final HttpRequest httpRequest,
            final Supplier<Route> innerRouteSupplier
    ) {
        return mapRequest(
                req -> addIfHeaderExists(
                        req,
                        startedTrace.propagateContext(Map.of()),
                        DittoHeaderDefinition.W3C_TRACEPARENT,
                        DittoHeaderDefinition.W3C_TRACESTATE
                ),
                () -> mapRouteResult(
                        routeResult -> tryToHandleRouteResult(routeResult, httpRequest, startedTrace),
                        innerRouteSupplier
                )
        );
    }

    private static HttpRequest addIfHeaderExists(
            final HttpRequest originalRequest,
            final Map<String, String> headers,
            final DittoHeaderDefinition... dittoHeaderDefinitions
    ) {
        var result = originalRequest;
        for (final var dittoHeaderDefinition : dittoHeaderDefinitions) {
            @Nullable final var headerValue = headers.get(dittoHeaderDefinition.getKey());
            if (null != headerValue) {
                result = result.addHeader(HttpHeader.parse(dittoHeaderDefinition.getKey(), headerValue));
            }
        }
        return result;
    }

    @Nullable
    private static RouteResult tryToHandleRouteResult(
            @Nullable final RouteResult routeResult,
            final HttpRequest httpRequest,
            final StartedSpan startedTrace
    ) {
        try {
            handleRouteResult(routeResult, httpRequest, startedTrace);
        } catch (final Exception e) {
            startedTrace.fail(e);
        } finally {
            startedTrace.finish();
        }
        return routeResult;
    }

    private static void handleRouteResult(
            @Nullable final RouteResult routeResult,
            final HttpRequest httpRequest,
            final StartedSpan startedTrace
    ) {
        if (routeResult instanceof Complete complete) {
            addRequestResponseTags(startedTrace, httpRequest, complete.getResponse());
        } else if (null != routeResult) {
            startedTrace.fail("Request rejected: " + routeResult.getClass().getName());
        } else {
            startedTrace.fail("Request failed.");
        }
    }

    private static void addRequestResponseTags(
            final StartedSpan startedTrace,
            final HttpRequest httpRequest,
            final HttpResponse httpResponse
    ) {
        startedTrace.tag(SpanTags.REQUEST_METHOD, getRequestMethodName(httpRequest));
        startedTrace.tag(
                SpanTags.REQUEST_PATH,
                String.valueOf(RequestLoggingFilter.filterUri(getRelativeUri(httpRequest)))
        );
        startedTrace.tag(SpanTags.STATUS_CODE, getResponseStatusCode(httpResponse));
    }

    private static int getResponseStatusCode(final HttpResponse httpResponse) {
        final var statusCode = httpResponse.status();
        return statusCode.intValue();
    }

}
