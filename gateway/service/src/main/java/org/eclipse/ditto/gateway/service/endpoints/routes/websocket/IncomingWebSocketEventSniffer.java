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
package org.eclipse.ditto.gateway.service.endpoints.routes.websocket;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import org.eclipse.ditto.base.service.DittoExtensionPoint;

import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.http.javadsl.model.HttpRequest;
import akka.stream.javadsl.Flow;

/**
 * Extension to sniff incoming events over websocket.
 */
public interface IncomingWebSocketEventSniffer extends DittoExtensionPoint {

    String CONFIG_PATH = "ditto.gateway.streaming.websocket.incoming-event-sniffer";

    /**
     * Create an async flow for event sniffing.
     *
     * @param request the HTTP request that started the event stream.
     * @return flow to pass events through with a wiretap attached over an async barrier to the sink for sniffed events.
     */
    Flow<String, String, NotUsed> toAsyncFlow(final HttpRequest request);

    /**
     * Loads the implementation of {@code IncomingWebSocketEventSniffer} which is configured for the
     * {@code ActorSystem}.
     *
     * @param actorSystem the actorSystem in which the {@code IncomingWebSocketEventSniffer} should be loaded.
     * @return the {@code IncomingWebSocketEventSniffer} implementation.
     * @throws NullPointerException if {@code actorSystem} is {@code null}.
     * @since 3.0.0
     */
    static IncomingWebSocketEventSniffer get(final ActorSystem actorSystem) {
        checkNotNull(actorSystem, "actorSystem");
        final var implementation = actorSystem.settings().config().getString(CONFIG_PATH);

        return new ExtensionId<>(implementation, IncomingWebSocketEventSniffer.class).get(actorSystem);
    }

}
