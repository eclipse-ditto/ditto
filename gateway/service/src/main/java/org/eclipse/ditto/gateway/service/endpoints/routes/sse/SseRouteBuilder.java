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
package org.eclipse.ditto.gateway.service.endpoints.routes.sse;

import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.gateway.service.endpoints.utils.GatewaySignalEnrichmentProvider;
import org.eclipse.ditto.gateway.service.streaming.StreamingAuthorizationEnforcer;

import akka.actor.ActorRef;
import akka.http.javadsl.server.RequestContext;
import akka.http.javadsl.server.Route;

/**
 * Builder with a fluent API for creating Akka HTTP routes for SSE (Server Sent Events).
 */
public interface SseRouteBuilder {

    /**
     * Sets the given object to enforce authorization in order to establish the SSE connection.
     * If no enforcer is set no authorization enforcement is performed.
     *
     * @param enforcer the enforcer to be used.
     * @return this builder instance to allow method chaining.
     * @throws NullPointerException if {@code enforcer} is {@code null}.
     */
    SseRouteBuilder withAuthorizationEnforcer(StreamingAuthorizationEnforcer enforcer);

    /**
     * Sets the given event sniffer.
     *
     * @param eventSniffer the new event sniffer.
     * @return this builder instance to allow method chaining.
     * @throws NullPointerException if {@code eventSniffer} is {@code null}.
     */
    SseRouteBuilder withEventSniffer(SseEventSniffer eventSniffer);

    /**
     * Sets the given supervisor.
     * If this method is never called the SSE connection remains without supervision.
     *
     * @param sseConnectionSupervisor the supervisor to be used.
     * @return this builder instance to allow method chaining.
     * @throws NullPointerException if {@code sseConnectionSupervisor} is {@code null}.
     */
    SseRouteBuilder withSseConnectionSupervisor(SseConnectionSupervisor sseConnectionSupervisor);

    /**
     * Set the signal enrichment provider.
     * If not set or set to null, all streaming requests with the 'extraFields' parameter result in error.
     *
     * @param provider the provider.
     * @return this builder.
     */
    SseRouteBuilder withSignalEnrichmentProvider(@Nullable GatewaySignalEnrichmentProvider provider);

    /**
     * Set the proxy actor.
     * If not set or set to null, streaming of search results will fail.
     *
     * @param proxyActor the proxy actor.
     * @return this builder.
     */
    SseRouteBuilder withProxyActor(@Nullable ActorRef proxyActor);

    /**
     * Creates the Akka HTTP route for SSE.
     *
     * @param requestContext provides the HTTP request.
     * @param dittoHeadersSupplier provides the Ditto headers to be used.
     * @return the route.
     * @throws NullPointerException if any argument is {@code null}.
     */
    Route build(RequestContext requestContext, Supplier<CompletionStage<DittoHeaders>> dittoHeadersSupplier);

}
