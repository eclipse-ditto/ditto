/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.utils.tracing;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.metrics.DittoMetrics;
import org.eclipse.ditto.services.utils.metrics.instruments.timer.ExpiringTimerBuilder;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.base.Command;

import akka.http.javadsl.model.HttpRequest;

/**
 * Utility for tracing Http requests.
 */
@Immutable
public final class TraceUtils {

    private static final String TRACING_FILTER_DELIMITER = "_";

    private static final String HTTP_ROUNDTRIP_METRIC_NAME = "roundtrip_http";
    private static final String AMQP_ROUNDTRIP_METRIC_NAME = "roundtrip_amqp";
    private static final String FILTER_AUTH_METRIC_NAME = "filter_auth";

    private TraceUtils() {
        throw new AssertionError();
    }

    /**
     * Prepares an {@link ExpiringTimerBuilder} with default {@link #HTTP_ROUNDTRIP_METRIC_NAME} and tags.
     * @param request The request to extract tags and request method.
     * @return The prepared {@link ExpiringTimerBuilder}
     */
    public static ExpiringTimerBuilder newHttpRoundTripTimer(final HttpRequest request) {
        final String requestMethod = request.method().name();
        final String requestPath = request.getUri().toRelative().path();

        final TraceInformation traceInformation = determineTraceInformation(requestPath);

        return newExpiringTimer(HTTP_ROUNDTRIP_METRIC_NAME)
                .tags(traceInformation.getTags())
                .tag(TracingTags.REQUEST_METHOD, requestMethod);
    }

    /**
     * Prepares an {@link ExpiringTimerBuilder} with default {@link #AMQP_ROUNDTRIP_METRIC_NAME} and tags.
     * @param command The command to extract tags.
     * @return The prepared {@link ExpiringTimerBuilder}
     */
    public static ExpiringTimerBuilder newAmqpRoundTripTimer(final Command<?> command) {
        return newExpiringTimer(AMQP_ROUNDTRIP_METRIC_NAME)
                .tag(TracingTags.COMMAND_TYPE, command.getType())
                .tag(TracingTags.COMMAND_TYPE_PREFIX, command.getTypePrefix())
                .tag(TracingTags.COMMAND_CATEGORY, command.getCategory().name());
    }

    /**
     * Prepares an {@link ExpiringTimerBuilder} with default {@link #AMQP_ROUNDTRIP_METRIC_NAME} and tags.
     * @param command The command to extract tags.
     * @return The prepared {@link ExpiringTimerBuilder}
     */
    public static ExpiringTimerBuilder newAmqpRoundTripTimer(final Signal<?> command) {
        if (command instanceof Command) {
            return newAmqpRoundTripTimer((Command) command);
        }

        final String metricsUri = AMQP_ROUNDTRIP_METRIC_NAME + command.getType();
        return newExpiringTimer(metricsUri)
                .tag(TracingTags.COMMAND_TYPE, command.getType());
    }

    /**
     * Prepares an {@link ExpiringTimerBuilder} with default {@link #FILTER_AUTH_METRIC_NAME} and tags.
     * @param authenticationType The name of the authentication type (i.e. jwt, ..)
     * @return The prepared {@link ExpiringTimerBuilder}
     */
    public static ExpiringTimerBuilder newAuthFilterTimer(final String authenticationType) {
        return newAuthFilterTimer(authenticationType, new HashMap<>());
    }

    /**
     * Prepares an {@link ExpiringTimerBuilder} with default {@link #FILTER_AUTH_METRIC_NAME} and tags.
     * @param authenticationType The name of the authentication type (i.e. jwt,...)
     * @param request The HttpRequest used to extract required tags.
     * @return The prepared {@link ExpiringTimerBuilder}
     */
    public static ExpiringTimerBuilder newAuthFilterTimer(final String authenticationType, final HttpRequest request) {
        final String requestPath = request.getUri().toRelative().path();

        final TraceInformation traceInformation = determineTraceInformation(requestPath);

        return newAuthFilterTimer(authenticationType, traceInformation.getTags());
    }

    private static ExpiringTimerBuilder newAuthFilterTimer(final String authenticationType,
            final Map<String, String> requestTags) {

        Map<String, String> defaultTags = new HashMap<>();
        defaultTags.put(TracingTags.AUTH_SUCCESS, Boolean.toString(false));
        defaultTags.put(TracingTags.AUTH_ERROR, Boolean.toString(false));

        return newExpiringTimer(FILTER_AUTH_METRIC_NAME)
                .tags(requestTags)
                .tags(defaultTags)
                .tag(TracingTags.AUTH_TYPE, authenticationType)
                .expirationHandling(expiredTimer ->
                        expiredTimer
                                .tag(TracingTags.AUTH_SUCCESS, false)
                                .tag(TracingTags.AUTH_ERROR, true));
    }

    private static ExpiringTimerBuilder newExpiringTimer(final String tracingFilter) {
        return DittoMetrics.expiringTimer(metricizeTraceUri(tracingFilter));
    }

    private static TraceInformation determineTraceInformation(final String requestPath) {
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
