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
package org.eclipse.ditto.services.connectivity.messaging;

import java.util.function.Supplier;

import org.eclipse.ditto.model.connectivity.ConnectionId;
import org.eclipse.ditto.services.utils.metrics.DittoMetrics;
import org.eclipse.ditto.services.utils.metrics.instruments.timer.StartedTimer;
import org.eclipse.ditto.services.utils.tracing.TracingTags;

/**
 * TODO javadoc
 */
class MappingTimer {

    private static final String TIMER_NAME = "connectivity_message_mapping";
    private static final String INBOUND = "inbound";
    private static final String OUTBOUND = "outbound";
    private static final String PAYLOAD_SEGMENT_NAME = "payload";
    private static final String PROTOCOL_SEGMENT_NAME = "protocol";
    private static final String DIRECTION_TAG_NAME = "direction";
    private static final String MAPPER_TAG_NAME = "mapper";

    private final StartedTimer timer;

    private MappingTimer(final StartedTimer timer) {
        this.timer = timer;
    }

    static MappingTimer inbound(final ConnectionId connectionId) {
        return new MappingTimer(startNewTimer(connectionId.toString()).tag(DIRECTION_TAG_NAME, INBOUND));
    }

    static MappingTimer outbound(final ConnectionId connectionId) {
        return new MappingTimer(startNewTimer(connectionId.toString()).tag(DIRECTION_TAG_NAME, OUTBOUND));
    }

    void overall(final Runnable runnable) {
        timed(timer, runnable);
    }

    <T> T overall(final Supplier<T> supplier) {
        return timed(timer, supplier);
    }

    <T> T payload(final String mapper, final Supplier<T> supplier) {
        return timed(timer.startNewSegment(PAYLOAD_SEGMENT_NAME).tag(MAPPER_TAG_NAME, mapper), supplier);
    }

    <T> T protocol(final Supplier<T> supplier) {
        return timed(timer.startNewSegment(PROTOCOL_SEGMENT_NAME), supplier);
    }

    private <T> T timed(final StartedTimer startedTimer, final Supplier<T> supplier) {
        try {
            final T result = supplier.get();
            startedTimer.tag(TracingTags.MAPPING_SUCCESS, true).stop();
            return result;
        } catch (final Exception ex) {
            startedTimer.tag(TracingTags.MAPPING_SUCCESS, false).stop();
            throw ex;
        }
    }

    private void timed(final StartedTimer startedTimer, final Runnable runnable) {
        try {
            runnable.run();
            startedTimer.tag(TracingTags.MAPPING_SUCCESS, true).stop();
        } catch (final Exception ex) {
            startedTimer.tag(TracingTags.MAPPING_SUCCESS, false).stop();
            throw ex;
        }
    }

    private static StartedTimer startNewTimer(final String connectionId) {
        return DittoMetrics
                .expiringTimer(TIMER_NAME)
                .tag(TracingTags.CONNECTION_ID, connectionId)
                .expirationHandling(expiredTimer -> expiredTimer.tag(TracingTags.MAPPING_SUCCESS, false))
                .build();
    }

}
