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
package org.eclipse.ditto.services.gateway.endpoints.utils;

import org.eclipse.ditto.services.utils.metrics.DittoMetrics;
import org.eclipse.ditto.services.utils.metrics.instruments.gauge.Gauge;

import akka.http.javadsl.model.HttpRequest;
import akka.stream.javadsl.Sink;

/**
 * An event sniffer that counts metrics via a {@link Gauge}.
 *
 * @param <T> Type of events to sniff.
 */
final class MetricsEventSniffer<T> implements EventSniffer<T> {

    private final Gauge messageCount;

    /**
     * Creates a new MetricsEventSniffer counting messages via a Gauge.
     *
     * @param streamingType the type of streaming metric, e.g. "ws" or "sse"
     * @param direction the direction the message went, e.g. "in" or "out"
     */
    MetricsEventSniffer(final String streamingType, final String direction) {
        messageCount = DittoMetrics.gauge("streaming_message_count")
                .tag("type", streamingType)
                .tag("direction", direction);
    }

    @Override
    public Sink<T, ?> createSink(final HttpRequest request) {
        return Sink.foreach(x -> messageCount.increment());
    }

}
