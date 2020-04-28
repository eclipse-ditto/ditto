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

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.protocoladapter.HeaderTranslator;
import org.eclipse.ditto.protocoladapter.ProtocolAdapter;
import org.eclipse.ditto.services.gateway.endpoints.utils.EventSniffer;
import org.eclipse.ditto.services.gateway.endpoints.utils.GatewaySignalEnrichmentProvider;

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
     * Set the signal enrichment provider.
     * If not set or set to null, all streaming requests with the 'extraFields' parameter result in error.
     *
     * @param provider the provider.
     * @return this builder instance to allow method chaining.
     */
    WebSocketRouteBuilder withSignalEnrichmentProvider(@Nullable GatewaySignalEnrichmentProvider provider);

    /**
     * Set the header translator.
     * If not set or set to null, all streaming requests with the 'extraFields' parameter result in error.
     *
     * @param headerTranslator the header translator.
     * @return this builder instance to allow method chaining.
     * @throws NullPointerException if {@code webSocketSupervisor} is {@code null}.
     */
    WebSocketRouteBuilder withHeaderTranslator(HeaderTranslator headerTranslator);

    /**
     * Creates the Akka HTTP route for websocket.
     *
     * @param version the WS API version.
     * @param correlationId the correlation ID of the request to open the WS connection.
     * @param dittoHeaders the ditto headers of the WS connection.
     * @param chosenProtocolAdapter protocol adapter to map incoming and outgoing signals.
     * @return the route.
     * @throws NullPointerException if any argument is {@code null}.
     */
    Route build(JsonSchemaVersion version,
            CharSequence correlationId,
            DittoHeaders dittoHeaders,
            ProtocolAdapter chosenProtocolAdapter);

}
