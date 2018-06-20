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

import akka.http.javadsl.model.HttpRequest;

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

    public static KamonTimerBuilder newHttpRoundTripTimer(final HttpRequest request) {
        final String requestMethod = request.method().name();
        final String requestPath = request.getUri().toRelative().path();

        final TraceInformation traceInformation = determineTraceInformation(requestPath);

        final String metricsUri = TIMER_HTTP_ROUNDTRIP_PREFIX + traceInformation.getTraceUri();
        return newTimer(metricsUri)
                .tags(traceInformation.getTags())
                .tag(TracingTags.REQUEST_METHOD, requestMethod);
    }

    public static KamonTimerBuilder newAmqpRoundTripTimer(final Command<?> command) {
        final String metricsUri = TIMER_AMQP_ROUNDTRIP_PREFIX + command.getType();
        return newTimer(metricsUri)
                .tag(TracingTags.COMMAND_TYPE, command.getType())
                .tag(TracingTags.COMMAND_TYPE_PREFIX, command.getTypePrefix())
                .tag(TracingTags.COMMAND_CATEGORY, command.getCategory().name());
    }

    public static KamonTimerBuilder newAmqpRoundTripTimer(final Signal<?> command) {
        if (command instanceof Command) {
            return newAmqpRoundTripTimer((Command) command);
        }

        final String metricsUri = TIMER_AMQP_ROUNDTRIP_PREFIX + command.getType();
        return newTimer(metricsUri)
                .tag(TracingTags.COMMAND_TYPE, command.getType());
    }

    public static KamonTimerBuilder newTimer(final String tracingFilter) {
        return KamonTimerBuilder.newTimer(metricizeTraceUri(tracingFilter));
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
