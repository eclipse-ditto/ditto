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

import com.typesafe.config.Config;

import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.stream.javadsl.Flow;
import org.apache.pekko.stream.javadsl.Sink;

/**
 * Event Sniffer for incoming WebSocket messages that does purposefully nothing.
 */
public final class NoOpIncomingWebSocketEventSniffer implements IncomingWebSocketEventSniffer {

    public NoOpIncomingWebSocketEventSniffer(final ActorSystem actorSystem, final Config config) {
        //No-Op because extensions need a constructor accepting an actorSystem
    }

    @Override
    public Flow<String, String, NotUsed> toAsyncFlow(final HttpRequest request) {
        return Flow.create();
    }

}
