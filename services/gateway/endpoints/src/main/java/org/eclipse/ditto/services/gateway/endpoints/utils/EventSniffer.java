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
package org.eclipse.ditto.services.gateway.endpoints.utils;

import java.util.concurrent.CompletableFuture;

import akka.NotUsed;
import akka.http.javadsl.model.HttpRequest;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;

/**
 * Functional interface to sniff events over websocket or SSE.
 *
 * @param <T> Type of events to sniff.
 */
@FunctionalInterface
public interface EventSniffer<T> {

    /**
     * Create a receiver for sniffed events.
     *
     * @param request the HTTP request that started the event stream.
     * @return sink to send events into.
     */
    Sink<T, ?> createSink(HttpRequest request);

    /**
     * Create an async flow for event sniffing.
     *
     * @param request the HTTP request that started the event stream.
     * @return flow to pass events through with a wiretap attached over an async barrier to the sink for sniffed events.
     */
    default Flow<T, T, NotUsed> toAsyncFlow(final HttpRequest request) {
        return Flow.<T>create().wireTap(
                Flow.<T>create()
                        .async()
                        .to(Sink.lazyInitAsync(() -> CompletableFuture.completedFuture(createSink(request)))));
    }

    /**
     * Create an event sniffer that gathers metrics.
     *
     * @param streamingType the type of streaming metric, e.g. "ws" or "sse"
     * @param direction the direction the message went, e.g. "in" or "out"
     * @param <T> type of events to sniff.
     * @return an event sniffer that gathers metrics.
     */
    static <T> EventSniffer<T> metricsSniffer(final String streamingType, final String direction) {
        return new MetricsEventSniffer<>(streamingType, direction);
    }

}
