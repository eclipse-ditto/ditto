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

import static org.apache.pekko.http.javadsl.server.Directives.extractRequest;
import static org.apache.pekko.http.javadsl.server.Directives.mapRequest;
import static org.apache.pekko.http.javadsl.server.Directives.mapRouteResult;
import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.net.URI;
import java.text.MessageFormat;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.http.javadsl.model.HttpHeader;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.HttpResponse;
import org.apache.pekko.http.javadsl.model.Uri;
import org.apache.pekko.http.javadsl.server.Complete;
import org.apache.pekko.http.javadsl.server.Route;
import org.apache.pekko.http.javadsl.server.RouteResult;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.util.ByteString;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.common.HttpStatusCodeOutOfRangeException;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.gateway.api.GatewayDuplicateHeaderException;
import org.eclipse.ditto.internal.utils.pekko.logging.DittoLogger;
import org.eclipse.ditto.internal.utils.pekko.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.tracing.DittoTracing;
import org.eclipse.ditto.internal.utils.tracing.TraceInformationGenerator;
import org.eclipse.ditto.internal.utils.tracing.span.SpanOperationName;
import org.eclipse.ditto.internal.utils.tracing.span.SpanTagKey;
import org.eclipse.ditto.internal.utils.tracing.span.StartedSpan;

/**
 * Custom Pekko Http directive tracing the request.
 */
@Immutable
public final class RequestTracingDirective {

    private static final DittoLogger LOGGER = DittoLoggerFactory.getLogger(RequestTracingDirective.class);

    private RequestTracingDirective() {
        throw new AssertionError();
    }

    /**
     * Conditionally starts and finishes a new trace for every request.
     * If the resolved span operation name is contained in the known disabled span operation names then the inner
     * Route will be returned as is.
     *
     * @param innerRouteSupplier supplies the inner Route to be traced.
     * @param correlationId the correlation ID which will be added to the log or {@code null} if no correlation ID
     * should be added.
     * @param actorSystem the ActorSystem we run in.
     * @return the new Route wrapping {@code inner} with tracing.
     * @throws NullPointerException if {@code inner} is {@code null}.
     */
    public static Route traceRequest(final Supplier<Route> innerRouteSupplier,
            @Nullable final CharSequence correlationId, final ActorSystem actorSystem) {

        checkNotNull(innerRouteSupplier, "innerRouteSupplier");
        return extractRequest(request -> {
            final Route result;
            @Nullable final var operationName = tryToResolveSpanOperationName(request, correlationId);
            if (null == operationName) {
                result = innerRouteSupplier.get();
            } else {
                result = getRouteWithEnabledTracing(
                        startTrace(getHttpHeadersAsMap(request.getHeaders()), operationName, correlationId),
                        request,
                        innerRouteSupplier,
                        correlationId,
                        actorSystem
                );
            }
            return result;
        });
    }

    @Nullable // internally working with null to avoid creating many Optionals
    private static SpanOperationName tryToResolveSpanOperationName(
            final HttpRequest httpRequest,
            @Nullable final CharSequence correlationId
    ) {
        try {
            return resolveSpanOperationName(httpRequest);
        } catch (final IllegalArgumentException e) {
            LOGGER.withCorrelationId(correlationId).warn("Failed to resolve span operation name: {}", e.getMessage());
            return null;
        }
    }

    private static SpanOperationName resolveSpanOperationName(final HttpRequest httpRequest) {
        return SpanOperationName.of(
                MessageFormat.format("{0} {1}", getRequestMethodName(httpRequest), getTraceUri(httpRequest))
        );
    }

    private static URI getTraceUri(final HttpRequest httpRequest) {
        final var traceInformationGenerator = TraceInformationGenerator.getInstance();
        final var traceInformation = traceInformationGenerator.apply(getRelativeUriPath(httpRequest));
        return traceInformation.getTraceUri();
    }

    private static Uri getRelativeUri(final HttpRequest httpRequest) {
        final var uri = httpRequest.getUri();
        return uri.toRelative();
    }

    private static String getRelativeUriPath(final HttpRequest httpRequest) {
        final var uri = httpRequest.getUri();
        final var relativeUri = uri.toRelative();
        return relativeUri.path();
    }

    private static String getRequestMethodName(final HttpRequest httpRequest) {
        final var httpMethod = httpRequest.method();
        return httpMethod.name();
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
            final StartedSpan startedSpan,
            final HttpRequest httpRequest,
            final Supplier<Route> innerRouteSupplier,
            @Nullable final CharSequence correlationId,
            final ActorSystem actorSystem
    ) {
        return mapRequest(
                req -> adjustSpanContextHeadersOfRequest(req, correlationId, startedSpan),
                () -> mapRouteResult(
                        routeResult ->
                                tryToHandleRouteResult(routeResult, httpRequest, startedSpan, correlationId, actorSystem),
                        innerRouteSupplier
                )
        );
    }

    private static HttpRequest adjustSpanContextHeadersOfRequest(
            final HttpRequest originalRequest,
            @Nullable final CharSequence correlationId,
            final StartedSpan startedSpan
    ) {
        final Set<String> headerNames = new HashSet<>();
        final Map<String, String> httpHeaders = StreamSupport.stream(originalRequest.getHeaders().spliterator(), false)
                .peek(httpHeader -> {
                    if (!headerNames.add(httpHeader.name())) {
                        throw GatewayDuplicateHeaderException.newBuilder(httpHeader.name())
                                .dittoHeaders(DittoHeaders.newBuilder()
                                        .correlationId(correlationId)
                                        .build()
                                ).build();
                    }
                })
                .collect(Collectors.toMap(HttpHeader::name, HttpHeader::value, (dv1, dv2) -> {
                    throw GatewayDuplicateHeaderException.newBuilder()
                            .dittoHeaders(DittoHeaders.newBuilder()
                                    .correlationId(correlationId)
                                    .build()
                            ).build();
                }));

        final Map<String, String> propagatedHeaders = startedSpan.propagateContext(httpHeaders);
        // Replace W3C tracing headers of original request because from now
        // on the newly started span is the parent of all subsequent spans.
        var result = originalRequest;
        for (final var w3cTracingHeader : getW3cTracingHeaders(propagatedHeaders)) {
            result = result.removeHeader(w3cTracingHeader.name()).addHeader(w3cTracingHeader);
        }
        return result;
    }

    private static Set<HttpHeader> getW3cTracingHeaders(final Map<String, String> spanContextHeaders) {
        return Stream.of(DittoHeaderDefinition.W3C_TRACEPARENT, DittoHeaderDefinition.W3C_TRACESTATE)
                .map(DittoHeaderDefinition::getKey)
                .filter(spanContextHeaders::containsKey)
                .map(w3cTracingHeaderName -> HttpHeader.parse(
                        w3cTracingHeaderName,
                        spanContextHeaders.get(w3cTracingHeaderName)
                ))
                .collect(Collectors.toSet());
    }

    @Nullable
    private static RouteResult tryToHandleRouteResult(
            @Nullable final RouteResult routeResult,
            final HttpRequest httpRequest,
            final StartedSpan startedSpan,
            @Nullable final CharSequence correlationId,
            final ActorSystem actorSystem
    ) {
        try {
            handleRouteResult(routeResult, httpRequest, startedSpan, correlationId, actorSystem)
                    .thenAccept(StartedSpan::finish);
        } catch (final Exception e) {
            startedSpan.tagAsFailed(e).finish();
        }
        return routeResult;
    }

    private static CompletionStage<StartedSpan> handleRouteResult(
            @Nullable final RouteResult routeResult,
            final HttpRequest httpRequest,
            final StartedSpan startedSpan,
            @Nullable final CharSequence correlationId,
            final ActorSystem actorSystem
    ) {
        if (routeResult instanceof Complete complete) {
            return addRequestResponseTags(startedSpan, httpRequest, complete.getResponse(), correlationId, actorSystem);
        } else if (null != routeResult) {
            startedSpan.tagAsFailed("Request rejected: " + routeResult.getClass().getName());
        } else {
            startedSpan.tagAsFailed("Request failed.");
        }
        return CompletableFuture.completedStage(startedSpan);
    }

    private static CompletionStage<StartedSpan> addRequestResponseTags(
            final StartedSpan startedSpan,
            final HttpRequest httpRequest,
            final HttpResponse httpResponse,
            @Nullable final CharSequence correlationId,
            final ActorSystem actorSystem
    ) {
        startedSpan.tag(SpanTagKey.REQUEST_METHOD_NAME.getTagForValue(getRequestMethodName(httpRequest)));
        startedSpan.tag(SpanTagKey.REQUEST_URI.getTagForValue(URI.create(getRelativeUri(httpRequest).toString())));
        @Nullable final var httpStatus = tryToGetResponseHttpStatus(httpResponse, correlationId);
        if (null != httpStatus) {
            startedSpan.tag(SpanTagKey.HTTP_STATUS.getTagForValue(httpStatus));
            if (!httpStatus.isSuccess()) {
                return httpResponse.entity().getDataBytes().fold(ByteString.emptyByteString(), ByteString::concat)
                        .map(ByteString::utf8String)
                        .runWith(Sink.head(), actorSystem)
                        .thenApply(startedSpan::tagAsFailed);
            }
        }
        return CompletableFuture.completedStage(startedSpan);
    }

    @Nullable
    private static HttpStatus tryToGetResponseHttpStatus(
            final HttpResponse httpResponse,
            @Nullable final CharSequence correlationId
    ) {
        try {
            return getResponseHttpStatus(httpResponse);
        } catch (final HttpStatusCodeOutOfRangeException e) {
            LOGGER.withCorrelationId(correlationId)
                    .info("Failed to get {} for HTTP response: {}", HttpStatus.class.getSimpleName(), e.getMessage());
            return null;
        }
    }

    private static HttpStatus getResponseHttpStatus(final HttpResponse httpResponse)
            throws HttpStatusCodeOutOfRangeException {

        final var statusCode = httpResponse.status();
        return HttpStatus.getInstance(statusCode.intValue());
    }

}
