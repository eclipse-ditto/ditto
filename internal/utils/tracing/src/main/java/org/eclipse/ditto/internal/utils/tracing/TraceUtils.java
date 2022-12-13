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
package org.eclipse.ditto.internal.utils.tracing;

import java.util.regex.Pattern;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.auth.AuthorizationContextType;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.internal.utils.metrics.DittoMetrics;
import org.eclipse.ditto.internal.utils.metrics.instruments.tag.Tag;
import org.eclipse.ditto.internal.utils.metrics.instruments.tag.TagSet;
import org.eclipse.ditto.internal.utils.metrics.instruments.timer.PreparedTimer;
import org.eclipse.ditto.internal.utils.tracing.span.SpanTagKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.http.javadsl.model.HttpRequest;

/**
 * Utility for tracing Http requests.
 */
@Immutable
public final class TraceUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(TraceUtils.class);

    private static final String TRACING_FILTER_DELIMITER = "_";
    private static final String SLASH = "/";
    private static final Pattern DUPLICATE_SLASH_PATTERN = Pattern.compile("\\/+");

    private static final String HTTP_ROUNDTRIP_METRIC_NAME = "roundtrip_http";
    private static final String FILTER_AUTH_METRIC_NAME = "filter_auth";
    private static final String LIVE_CHANNEL_NAME = "live";
    private static final String TWIN_CHANNEL_NAME = "twin";
    private static final Pattern MESSAGE_PATTERN = Pattern.compile("(.*/(inbox|outbox)/messages/.*)|(.*/inbox/claim)");

    private TraceUtils() {
        throw new AssertionError();
    }

    /**
     * Prepares an {@link PreparedTimer} with default {@link #HTTP_ROUNDTRIP_METRIC_NAME} and tags.
     *
     * @param request The request to extract tags and request method.
     * @return The prepared {@link PreparedTimer}
     */
    public static PreparedTimer newHttpRoundTripTimer(final HttpRequest request) {
        return newExpiringTimer(HTTP_ROUNDTRIP_METRIC_NAME)
                .tags(getTraceInformationTags(request))
                .tag(SpanTagKey.REQUEST_METHOD_NAME.getTagForValue(request.method().name()))
                .tag(SpanTagKey.CHANNEL.getTagForValue(determineChannel(request)));
    }

    private static TagSet getTraceInformationTags(final HttpRequest httpRequest) {
        final TagSet result;
        @Nullable final var traceInformation = tryToDetermineTraceInformation(getRelativeUriPath(httpRequest));
        if (null == traceInformation) {
            result = TagSet.empty();
        } else {
            result = traceInformation.getTagSet();
        }
        return result;
    }

    @Nullable
    private static TraceInformation tryToDetermineTraceInformation(final String requestPath) {
        try {
            return determineTraceInformation(requestPath);
        } catch (final IllegalArgumentException e) {
            LOGGER.warn("Failed to determine trace information for request path <{}>: {}", requestPath, e.getMessage());
            return null;
        }
    }

    private static TraceInformation determineTraceInformation(final String requestPath) {
        final var traceInformationGenerator = TraceInformationGenerator.getInstance();
        return traceInformationGenerator.apply(requestPath);
    }

    private static String getRelativeUriPath(final HttpRequest httpRequest) {
        final var uri = httpRequest.getUri();
        final var relativeUri = uri.toRelative();
        return relativeUri.path();
    }

    /**
     * Prepares an {@link PreparedTimer} with default {@link #FILTER_AUTH_METRIC_NAME} and tags.
     *
     * @param authorizationContextType the authorization context type (i.e. JWT, ...).
     * @return the prepared {@link PreparedTimer}.
     * @throws NullPointerException if {@code authorizationContextType} is {@code null}.
     */
    public static PreparedTimer newAuthFilterTimer(final AuthorizationContextType authorizationContextType) {
        return newAuthFilterTimer(authorizationContextType, TagSet.empty());
    }

    private static PreparedTimer newAuthFilterTimer(
            final AuthorizationContextType authorizationContextType,
            final TagSet requestTags
    ) {
        return newExpiringTimer(FILTER_AUTH_METRIC_NAME)
                .tags(requestTags)
                .tag(SpanTagKey.AUTH_SUCCESS.getTagForValue(false))
                .tag(SpanTagKey.AUTH_ERROR.getTagForValue(false))
                .tag(getAuthTypeMetricsTag(authorizationContextType))
                .onExpiration(expiredTimer ->
                        expiredTimer
                                .tag(SpanTagKey.AUTH_SUCCESS.getTagForValue(false))
                                .tag(SpanTagKey.AUTH_ERROR.getTagForValue(true)));
    }

    private static PreparedTimer newExpiringTimer(final String tracingFilter) {
        return DittoMetrics.timer(metricizeTraceUri(tracingFilter));
    }

    private static Tag getAuthTypeMetricsTag(final AuthorizationContextType authorizationContextType) {
        return Tag.of(SpanTagKey.KEY_PREFIX + "auth.type", authorizationContextType.toString());
    }

    private static String determineChannel(final HttpRequest request) {
        // determine channel based on header or query parameter
        final boolean liveHeaderPresent = request.getHeader(DittoHeaderDefinition.CHANNEL.getKey())
                .filter(channelHeader -> LIVE_CHANNEL_NAME.equals(channelHeader.value()))
                .isPresent();
        final boolean liveQueryPresent = request.getUri().query().get(DittoHeaderDefinition.CHANNEL.getKey())
                .filter(LIVE_CHANNEL_NAME::equals)
                .isPresent();
        // messages are always live commands
        final String normalizePath = normalizePath(request.getUri().path());
        final boolean messageRequest = MESSAGE_PATTERN.matcher(normalizePath).matches();

        return liveHeaderPresent || liveQueryPresent || messageRequest ? LIVE_CHANNEL_NAME : TWIN_CHANNEL_NAME;
    }

    /**
     * Replaces all characters that are invalid for metrics (at least for Prometheus metrics).
     */
    public static String metricizeTraceUri(final String traceUri) {
        return traceUri.replaceAll("[./:-]", TRACING_FILTER_DELIMITER);
    }

    /**
     * Normalizes the path and removes duplicate slashes.
     */
    public static String normalizePath(final String path) {
        if (path.isEmpty()) {
            return SLASH;
        }

        // remove duplicate slashes
        String normalized = DUPLICATE_SLASH_PATTERN.matcher(path).replaceAll(SLASH);

        // strip trailing slash if necessary
        if (normalized.length() > 1 && normalized.endsWith(SLASH)) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        // add leading slash if necessary
        if (!normalized.startsWith(SLASH)) {
            normalized = SLASH + normalized;
        }

        return normalized;
    }

}
