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
package org.eclipse.ditto.services.gateway.endpoints.routes.websocket;

import org.eclipse.ditto.model.base.auth.AuthorizationContext;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.protocoladapter.ProtocolAdapter;
import org.eclipse.ditto.services.gateway.endpoints.utils.EventSniffer;

import akka.http.javadsl.server.Route;

/**
 * Builder with a fluent API for creating Akka HTTP routes for websocket connections.
 */
public interface WebsocketRouteBuilder {

    /**
     * Sets the given event sniffer for incoming messages.
     *
     * @param eventSniffer the event sniffer for incoming messages.
     * @return this builder instance to allow method chaining.
     * @throws NullPointerException if {@code eventSniffer} is {@code null}.
     */
    WebsocketRouteBuilder withIncomingEventSniffer(EventSniffer<String> eventSniffer);

    /**
     * Sets the given event sniffer for outgoing messages.
     *
     * @param eventSniffer the event sniffer for outgoing messages.
     * @return this builder instance to allow method chaining.
     * @throws NullPointerException if {@code eventSniffer} is {@code null}.
     */
    WebsocketRouteBuilder withOutgoingEventSniffer(EventSniffer<String> eventSniffer);

    /**
     * Creates the Akka HTTP route for websocket.
     *
     * @param version
     * @param correlationId
     * @param connectionAuthContext
     * @param additionalHeaders
     * @param chosenProtocolAdapter
     * @return the route.
     * @throws NullPointerException if any argument is {@code null}.
     */
    Route build(Integer version,
            CharSequence correlationId,
            AuthorizationContext connectionAuthContext,
            DittoHeaders additionalHeaders,
            ProtocolAdapter chosenProtocolAdapter);

}
