/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.internal.utils.metrics.DittoMetrics;
import org.eclipse.ditto.internal.utils.metrics.instruments.timer.StartedTimer;
import org.eclipse.ditto.internal.utils.tracing.DittoTracing;
import org.eclipse.ditto.internal.utils.tracing.TraceOperationName;
import org.eclipse.ditto.internal.utils.tracing.TracingTags;
import org.eclipse.ditto.internal.utils.tracing.instruments.trace.StartedTrace;
import org.eclipse.ditto.protocol.Adaptable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class helps to create {@link DittoMetrics#timer}s measuring the different segments of a mapping
 * operation.
 */
@NotThreadSafe
final class MappingTimer {

    private static final Logger LOGGER = LoggerFactory.getLogger(MappingTimer.class);

    private static final String TIMER_NAME = "connectivity_message_mapping";
    private static final String INBOUND = "inbound";
    private static final String OUTBOUND = "outbound";
    private static final String PAYLOAD_SEGMENT_NAME = "payload";
    private static final String PROTOCOL_SEGMENT_NAME = "protocol";
    private static final String DIRECTION_TAG_NAME = "direction";
    private static final String MAPPER_TAG_NAME = "mapper";

    private final StartedTimer timer;

    /**
     * Holds the current trace context to create child spans to reflect the call hierarchy e.g. overall -> protocol
     * -> payload.
     */
    private StartedTrace startedTrace;

    private MappingTimer(final StartedTimer timer, final StartedTrace startedTrace) {
        this.timer = timer;
        this.startedTrace = startedTrace;
    }

    /**
     * @param connectionId ID of the connection
     * @param connectionType the type of the connection.
     * @return a new MappingTimer instance ready to measure inbound mappings.
     */
    static MappingTimer inbound(
            final ConnectionId connectionId,
            final ConnectionType connectionType,
            final Map<String, String> headersMap
    ) {
        final var startedTimer = startNewTimer(connectionId, connectionType, INBOUND);
        return new MappingTimer(startedTimer, DittoTracing.newStartedTraceByTimer(headersMap, startedTimer));
    }

    private static StartedTimer startNewTimer(
            final ConnectionId connectionId,
            final ConnectionType connectionType,
            final CharSequence messageDirection
    ) {
        return DittoMetrics.timer(TIMER_NAME)
                .tag(TracingTags.CONNECTION_ID, connectionId.toString())
                .tag(TracingTags.CONNECTION_TYPE, connectionType.getName())
                .tag(DIRECTION_TAG_NAME, messageDirection.toString())
                .onExpiration(expiredTimer -> {
                    LOGGER.warn("Mapping timer expired. This should not happen. Timer: <{}>", expiredTimer);
                    expiredTimer.tag(TracingTags.MAPPING_SUCCESS, false);
                })
                .start();
    }

    /**
     * @param connectionId ID of the connection.
     * @param connectionType the type of the connection.
     * @return a new MappingTimer instance ready to measure outbound mappings.
     */
    static MappingTimer outbound(
            final ConnectionId connectionId,
            final ConnectionType connectionType,
            final Map<String, String> headersMap
    ) {
        final var startedTimer = startNewTimer(connectionId, connectionType, OUTBOUND);
        return new MappingTimer(startedTimer, DittoTracing.newStartedTraceByTimer(headersMap, startedTimer));
    }

    /**
     * Measures the overall execution time.
     *
     * @param <T> the return type
     * @param supplier the supplier of which the execution time is measured
     * @return the value returned by the given supplier
     */
    <T> T overall(final Supplier<T> supplier) {
        return timed(timer, supplier);
    }

    /**
     * Measures the execution of the given supplier using a separate 'payload' segment and a tag for the given mapper.
     * The current trace context is attached to each resulting external messages.
     *
     * @param mapper ID of the used mapper.
     * @param supplier the supplier which is invoked and measured.
     * @return the result of the supplier.
     */
    List<ExternalMessage> outboundPayload(final String mapper, final Supplier<List<ExternalMessage>> supplier) {
        final var startedTimer = startNewTimerSegment(mapper);
        startedTrace = spawnChildTraceFromStartedTimer(startedTimer);
        return timed(
                startedTimer,
                () -> {
                    final var externalMessages = supplier.get();
                    return externalMessages.stream()
                            .map(this::propagateContextToExternalMessage)
                            .toList();
                }
        );
    }

    private StartedTimer startNewTimerSegment(final String mapperName) {
        return timer.startNewSegment(PAYLOAD_SEGMENT_NAME).tag(MAPPER_TAG_NAME, mapperName);
    }

    private StartedTrace spawnChildTraceFromStartedTimer(final StartedTimer startedTimer) {
        final var preparedTrace = startedTrace.spawnChild(TraceOperationName.of(startedTimer.getName()));
        return preparedTrace.startBy(startedTimer);
    }

    private ExternalMessage propagateContextToExternalMessage(final ExternalMessage externalMessage) {
        return externalMessage.withHeaders(startedTrace.propagateContext(externalMessage.getHeaders()));
    }

    /**
     * Measures the execution of the given adaptable supplier using a separate 'payload' segment and a tag for the given
     * mapper. The current trace context is attached to each resulting adaptables.
     *
     * @param mapper the used mapper
     * @param supplier the supplier of adaptables
     * @return the list of mapped adaptables
     */
    List<Adaptable> inboundPayload(final String mapper, final Supplier<List<Adaptable>> supplier) {
        final var startedTimer = startNewTimerSegment(mapper);
        startedTrace = spawnChildTraceFromStartedTimer(startedTimer);
        return timed(
                startedTimer,
                () -> {
                    final var adaptables = supplier.get();
                    return adaptables.stream()
                            .map(this::propagateContextToAdaptable)
                            .toList();
                }
        );
    }

    private Adaptable propagateContextToAdaptable(final Adaptable adaptable) {
        return adaptable.setDittoHeaders(DittoHeaders.of(startedTrace.propagateContext(adaptable.getDittoHeaders())));
    }

    /**
     * Measures the execution of the given signal supplier using a separate protocol segment.
     * The current trace context is attached to the resulting signal.
     *
     * @param supplier the supplier which is invoked and measured
     * @return the result of the supplier
     */
    Signal<?> inboundProtocol(final Supplier<Signal<?>> supplier) {
        final var startedTimer = timer.startNewSegment(PROTOCOL_SEGMENT_NAME);
        startedTrace = spawnChildTraceFromStartedTimer(startedTimer);
        return timed(startedTimer, () -> propagateContextToSignalDittoHeaders(supplier.get()));
    }

    private Signal<?> propagateContextToSignalDittoHeaders(final Signal<?> signal) {
        return signal.setDittoHeaders(DittoHeaders.of(startedTrace.propagateContext(signal.getDittoHeaders())));
    }

    /**
     * Measures the execution of the given supplier using a separate protocol.
     *
     * @param supplier the supplier which is invoked and measured
     * @param <T> result type of the given supplier
     * @return the result of the supplier
     */
    <T> T protocol(final Supplier<T> supplier) {
        return timed(timer.startNewSegment(PROTOCOL_SEGMENT_NAME), supplier);
    }

    private static <T> T timed(final StartedTimer startedTimer, final Supplier<T> supplier) {
        try {
            final var result = supplier.get();
            startedTimer.tag(TracingTags.MAPPING_SUCCESS, true).stop();
            return result;
        } catch (final Exception ex) {
            startedTimer.tag(TracingTags.MAPPING_SUCCESS, false).stop();
            throw ex;
        }
    }

    StartedTrace getTrace() {
        return startedTrace;
    }

}
