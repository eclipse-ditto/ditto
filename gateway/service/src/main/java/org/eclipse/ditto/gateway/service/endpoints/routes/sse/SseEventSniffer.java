/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.gateway.service.endpoints.routes.sse;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.concurrent.CompletableFuture;

import org.eclipse.ditto.base.service.DittoExtensionPoint;
import org.eclipse.ditto.gateway.service.util.config.DittoGatewayConfig;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;

import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.sse.ServerSentEvent;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;

/**
 * Functional interface to sniff events over or SSE.
 */
public abstract class SseEventSniffer extends DittoExtensionPoint {

    /**
     * @param actorSystem the actor system in which to load the extension.
     */
    protected SseEventSniffer(final ActorSystem actorSystem) {
        super(actorSystem);
    }

    /**
     * Create a receiver for sniffed events.
     *
     * @param request the HTTP request that started the event stream.
     * @return sink to send events into.
     */
    protected abstract Sink<ServerSentEvent, ?> createSink(HttpRequest request);

    /**
     * Create an async flow for event sniffing.
     *
     * @param request the HTTP request that started the event stream.
     * @return flow to pass events through with a wiretap attached over an async barrier to the sink for sniffed events.
     */
    protected Flow<ServerSentEvent, ServerSentEvent, NotUsed> toAsyncFlow(final HttpRequest request) {
        return Flow.<ServerSentEvent>create().wireTap(
                Flow.<ServerSentEvent>create()
                        .async()
                        .to(Sink.lazyCompletionStageSink(() -> CompletableFuture.completedFuture(
                                createSink(request)))));
    }

    /**
     * Loads the implementation of {@code SseEventSniffer} which is configured for the
     * {@code ActorSystem}.
     *
     * @param actorSystem the actorSystem in which the {@code SseEventSniffer} should be loaded.
     * @return the {@code SseEventSniffer} implementation.
     * @throws NullPointerException if {@code actorSystem} is {@code null}.
     * @since 3.0.0
     */
    public static SseEventSniffer get(final ActorSystem actorSystem) {
        checkNotNull(actorSystem, "actorSystem");
        final var implementation = DittoGatewayConfig.of(DefaultScopedConfig.dittoScoped(
                actorSystem.settings().config())).getStreamingConfig().getSseConfig().getEventSniffer();

        return new ExtensionId<>(implementation, SseEventSniffer.class).get(actorSystem);
    }

}
