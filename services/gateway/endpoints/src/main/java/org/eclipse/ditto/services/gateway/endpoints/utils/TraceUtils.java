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
package org.eclipse.ditto.services.gateway.endpoints.utils;

import java.text.MessageFormat;

import javax.annotation.concurrent.Immutable;

import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.server.RequestContext;
import kamon.Kamon;
import kamon.trace.Status;
import kamon.trace.TraceContext;
import scala.Option;
import scala.Some;
import scala.collection.immutable.Map$;

/**
 * Utility for tracing Http requests.
 */
@Immutable
public final class TraceUtils {

    private static final String TRACE_HTTP_ROUNDTRIP_TEMPLATE = "roundtrip.http{0}.{1}.{2}";

    private TraceUtils() {
        throw new AssertionError();
    }

    /**
     * Creates a trace for the given request.
     *
     * @param correlationId the correlationId to be used as token for the trace
     * @param startNanos the start of the request in nanos
     * @param requestContext the context of the request
     * @param statusCode the status code of the response for the request
     */
    public static void createTrace(final String correlationId, final long startNanos, final RequestContext
            requestContext, final int statusCode) {
        final String traceName = determineTraceName(requestContext.getRequest(), statusCode);

        final Option<String> token = Some.<String>apply(correlationId);
        final scala.collection.immutable.Map<String, String> tags = Map$.MODULE$.<String, String>empty();
        final TraceContext traceContext = Kamon.tracer().newContext(traceName, token, tags, startNanos, Status
                .FinishedSuccessfully$.MODULE$, true);
        traceContext.finish();
    }

    static String determineTraceName(final HttpRequest request, final int statusCode) {
        final String requestMethod = request.method().name();
        final String requestPath = request.getUri().toRelative().path();

        final String traceUri = determineTraceUri(requestPath);
        return MessageFormat.format(TRACE_HTTP_ROUNDTRIP_TEMPLATE, traceUri, requestMethod, statusCode);
    }

    private static String determineTraceUri(final String requestPath) {
        final TraceUriGenerator traceUriGenerator = TraceUriGenerator.getInstance();
        return traceUriGenerator.apply(requestPath);
    }

}
