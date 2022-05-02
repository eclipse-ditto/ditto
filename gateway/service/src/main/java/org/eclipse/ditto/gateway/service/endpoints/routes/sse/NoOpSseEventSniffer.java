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

import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.sse.ServerSentEvent;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;

/**
 * Sniffer for Server Sent Events that does purposefully nothing.
 */
public class NoOpSseEventSniffer extends SseEventSniffer {

    public NoOpSseEventSniffer(final ActorSystem actorSystem) {
        super(actorSystem);
    }

    @Override
    public Sink<ServerSentEvent, ?> createSink(final HttpRequest request) {
        return Sink.ignore();
    }

    @Override
    public Flow<ServerSentEvent, ServerSentEvent, NotUsed> toAsyncFlow(final HttpRequest request) {
        return Flow.create();
    }
}
