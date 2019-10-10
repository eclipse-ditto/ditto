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
package org.eclipse.ditto.services.gateway.endpoints.routes.sse;

import java.util.function.Supplier;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.services.gateway.endpoints.utils.EventSniffer;

import akka.http.javadsl.model.sse.ServerSentEvent;
import akka.http.javadsl.server.RequestContext;
import akka.http.javadsl.server.Route;

/**
 * Builder with a fluent API for creating Akka HTTP routes for SSE (Server Sent Events).
 */
public interface SseRouteBuilder {

    /**
     * Sets the given event sniffer.
     *
     * @param eventSniffer the new event sniffer.
     * @return this builder instance to allow method chaining.
     * @throws NullPointerException if {@code eventSniffer} is {@code null}.
     */
    SseRouteBuilder withEventSniffer(EventSniffer<ServerSentEvent> eventSniffer);

    /**
     * Creates the Akka HTTP route for SSE.
     *
     * @param requestContext provides the HTTP request.
     * @param dittoHeadersSupplier provides the Ditto headers to be used.
     * @return the route.
     * @throws NullPointerException if any argument is {@code null}.
     */
    Route build(RequestContext requestContext, Supplier<DittoHeaders> dittoHeadersSupplier);

}
