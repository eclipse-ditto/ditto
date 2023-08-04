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

import com.typesafe.config.Config;

import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.sse.ServerSentEvent;
import org.apache.pekko.stream.javadsl.Flow;

/**
 * Sniffer for Server Sent Events that does purposefully nothing.
 */
public final class NoOpSseEventSniffer implements SseEventSniffer {

    @SuppressWarnings("unused")
    public NoOpSseEventSniffer(final ActorSystem actorSystem, final Config config) {
        //No-Op because extensions need a constructor accepting an actorSystem
    }

    @Override
    public Flow<ServerSentEvent, ServerSentEvent, NotUsed> toAsyncFlow(final HttpRequest request) {
        return Flow.create();
    }

}
