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
package org.eclipse.ditto.gateway.service.endpoints.utils;

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
                        .to(Sink.lazyCompletionStageSink(() -> CompletableFuture.completedFuture(
                                createSink(request)))));
    }

    /**
     * Create an event sniffer that does not do anything.
     *
     * @param <T> type of events to sniff.
     * @return an event sniffer that does not do anything.
     */
    static <T> EventSniffer<T> noOp() {
        return new NoOp<>();
    }

    /**
     * An event sniffer that does not do anything.
     *
     * @param <T> Type of events to sniff.
     */
    final class NoOp<T> implements EventSniffer<T> {

        private NoOp() {}

        @Override
        public Sink<T, ?> createSink(final HttpRequest request) {
            return Sink.ignore();
        }

        @Override
        public Flow<T, T, NotUsed> toAsyncFlow(final HttpRequest request) {
            return Flow.create();
        }
    }

}
