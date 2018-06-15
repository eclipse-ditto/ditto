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
package org.eclipse.ditto.services.utils.tracing;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.base.Command;

import akka.http.javadsl.server.RequestContext;
import kamon.Kamon;
import kamon.trace.Tracer;

/**
 * Utility for tracing Http requests.
 */
@Immutable
public final class TraceUtils {

    private static final String TRACING_FILTER_DELIMITER = "_";

    private static final String TIMER_HTTP_ROUNDTRIP_PREFIX = "roundtrip_http";
    private static final String TIMER_AMQP_ROUNDTRIP_PREFIX = "roundtrip_amqp";

    private TraceUtils() {
        throw new AssertionError();
    }

    public static MutableKamonTimerBuilder newRoundTripTimer(final RequestContext requestContext) {
        final String requestMethod = requestContext.getRequest().method().name();
        final String requestPath = requestContext.getRequest().getUri().toRelative().path();

        final TraceInformation traceInformation = determineTraceInformation(requestPath);

        final String metricsUri = TIMER_HTTP_ROUNDTRIP_PREFIX + traceInformation.getTraceUri();
        return newTimer(metricsUri)
                .tags(traceInformation.getTags())
                .tag(TracingTags.REQUEST_METHOD, requestMethod);
    }

    public static MutableKamonTimerBuilder newRoundTripTimer(final Command<?> command) {
        final String metricsUri = TIMER_AMQP_ROUNDTRIP_PREFIX + command.getType();
        return newTimer(metricsUri)
                .tag(TracingTags.COMMAND_TYPE, command.getType())
                .tag(TracingTags.COMMAND_TYPE_PREFIX, command.getTypePrefix())
                .tag(TracingTags.COMMAND_CATEGORY, command.getCategory().name());
    }

    public static MutableKamonTimerBuilder newRoundTripTimer(final Signal<?> command) {
        if (command instanceof Command) {
            return newRoundTripTimer((Command) command);
        }

        final String metricsUri = TIMER_AMQP_ROUNDTRIP_PREFIX + command.getType();
        return newTimer(metricsUri)
                .tag(TracingTags.COMMAND_TYPE, command.getType());
    }

    public static MutableKamonTimerBuilder newTimer(final String tracingFilter) {
        return MutableKamonTimerBuilder.newTimer(metricizeTraceUri(tracingFilter));
    }

    public static Tracer.SpanBuilder createTrace(final RequestContext requestContext) {
        final String requestMethod = requestContext.getRequest().method().name();
        final String requestPath = requestContext.getRequest().getUri().toRelative().path();

        final TraceInformation traceInformation = determineTraceInformation(requestPath);

        return Kamon.buildSpan(metricizeTraceUri(traceInformation.getTraceUri()))
                .withTag(TracingTags.REQUEST_METHOD, requestMethod);
    }

    public static TraceInformation determineTraceInformation(final String requestPath) {
        final TraceUriGenerator traceUriGenerator = TraceUriGenerator.getInstance();
        return traceUriGenerator.apply(requestPath);
    }

    /**
     * Replaces all characters that are invalid for metrics (at least for Prometheus metrics).
     */
    public static String metricizeTraceUri(final String traceUri) {
        return traceUri.replaceAll("[./:-]", TRACING_FILTER_DELIMITER);
    }


}
