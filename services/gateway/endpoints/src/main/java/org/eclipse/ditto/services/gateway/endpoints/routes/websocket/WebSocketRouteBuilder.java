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
public interface WebSocketRouteBuilder {

    /**
     * Sets the given event sniffer for incoming messages.
     *
     * @param eventSniffer the event sniffer for incoming messages.
     * @return this builder instance to allow method chaining.
     * @throws NullPointerException if {@code eventSniffer} is {@code null}.
     */
    WebSocketRouteBuilder withIncomingEventSniffer(EventSniffer<String> eventSniffer);

    /**
     * Sets the given event sniffer for outgoing messages.
     *
     * @param eventSniffer the event sniffer for outgoing messages.
     * @return this builder instance to allow method chaining.
     * @throws NullPointerException if {@code eventSniffer} is {@code null}.
     */
    WebSocketRouteBuilder withOutgoingEventSniffer(EventSniffer<String> eventSniffer);

    /**
     * Sets the given object to enforce authorization in order to establish the WebSocket connection.
     * If no enforcer is set no authorization enforcement is performed.
     *
     * @param enforcer the enforcer to be used.
     * @return this builder instance to allow method chaining.
     * @throws NullPointerException if {@code enforcer} is {@code null}.
     */
    WebSocketRouteBuilder withAuthorizationEnforcer(WebSocketAuthorizationEnforcer enforcer);

    /**
     * Sets the given supervisor.
     * If this method is never called the WebSocket remains without supervision.
     *
     * @param webSocketSupervisor the supervisor to be used.
     * @return this builder instance to allow method chaining.
     * @throws NullPointerException if {@code webSocketSupervisor} is {@code null}.
     */
    WebSocketRouteBuilder withWebSocketSupervisor(WebSocketSupervisor webSocketSupervisor);

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
