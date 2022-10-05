/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.gateway.service.endpoints.routes;

import java.util.Collection;

import org.eclipse.ditto.base.model.headers.translator.HeaderTranslator;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.edge.service.headers.DittoHeadersValidator;
import org.eclipse.ditto.gateway.service.endpoints.directives.auth.GatewayAuthenticationDirective;
import org.eclipse.ditto.gateway.service.endpoints.routes.cloudevents.CloudEventsRoute;
import org.eclipse.ditto.gateway.service.endpoints.routes.connections.ConnectionsRoute;
import org.eclipse.ditto.gateway.service.endpoints.routes.devops.DevOpsRoute;
import org.eclipse.ditto.gateway.service.endpoints.routes.health.CachingHealthRoute;
import org.eclipse.ditto.gateway.service.endpoints.routes.policies.PoliciesRoute;
import org.eclipse.ditto.gateway.service.endpoints.routes.sse.SseRouteBuilder;
import org.eclipse.ditto.gateway.service.endpoints.routes.stats.StatsRoute;
import org.eclipse.ditto.gateway.service.endpoints.routes.status.OverallStatusRoute;
import org.eclipse.ditto.gateway.service.endpoints.routes.things.ThingsRoute;
import org.eclipse.ditto.gateway.service.endpoints.routes.thingsearch.ThingSearchRoute;
import org.eclipse.ditto.gateway.service.endpoints.routes.websocket.WebSocketRouteBuilder;
import org.eclipse.ditto.gateway.service.endpoints.routes.whoami.WhoamiRoute;
import org.eclipse.ditto.internal.utils.health.routes.StatusRoute;
import org.eclipse.ditto.internal.utils.protocol.ProtocolAdapterProvider;

import akka.http.javadsl.server.ExceptionHandler;
import akka.http.javadsl.server.RejectionHandler;
import akka.http.javadsl.server.Route;

/**
 * A builder for the root {@code Route}.
 */
public interface RootRouteBuilder {

    /**
     * Sets the status sub-route.
     *
     * @param route the route to set.
     * @return the Builder to allow method chaining.
     */
    RootRouteBuilder statusRoute(StatusRoute route);

    /**
     * Sets the overall status sub-route.
     *
     * @param route the route to set.
     * @return the Builder to allow method chaining.
     */
    RootRouteBuilder overallStatusRoute(OverallStatusRoute route);

    /**
     * Sets the caching health sub-route.
     *
     * @param route the route to set.
     * @return the Builder to allow method chaining.
     */
    RootRouteBuilder cachingHealthRoute(CachingHealthRoute route);

    /**
     * Sets the devops sub-route.
     *
     * @param route the route to set.
     * @return the Builder to allow method chaining.
     */
    RootRouteBuilder devopsRoute(DevOpsRoute route);

    /**
     * Sets the policies sub-route.
     *
     * @param route the route to set.
     * @return the Builder to allow method chaining.
     */
    RootRouteBuilder policiesRoute(PoliciesRoute route);

    /**
     * Sets the sse things sub-route.
     *
     * @param sseThingsRouteBuilder the route to set.
     * @return the Builder to allow method chaining.
     */
    RootRouteBuilder sseThingsRoute(SseRouteBuilder sseThingsRouteBuilder);

    /**
     * Sets the things sub-route.
     *
     * @param route the route to set.
     * @return the Builder to allow method chaining.
     */
    RootRouteBuilder thingsRoute(ThingsRoute route);

    /**
     * Sets the thing search sub-route.
     *
     * @param route the route to set.
     * @return the Builder to allow method chaining.
     */
    RootRouteBuilder thingSearchRoute(ThingSearchRoute route);

    /**
     * Sets the connections sub-route.
     *
     * @param route the route to set.
     * @return the Builder to allow method chaining.
     */
    RootRouteBuilder connectionsRoute(ConnectionsRoute route);

    /**
     * Sets the websocket sub-route.
     *
     * @param websocketRouteBuilder the route to set.
     * @return the Builder to allow method chaining.
     */
    RootRouteBuilder websocketRoute(WebSocketRouteBuilder websocketRouteBuilder);

    /**
     * Sets the stats sub-route.
     *
     * @param route the route to set.
     * @return the Builder to allow method chaining.
     */
    RootRouteBuilder statsRoute(StatsRoute route);

    /**
     * Sets the stats whoami-route.
     *
     * @param route the route to set.
     * @return the Builder to allow method chaining.
     * @since 1.2.0
     */
    RootRouteBuilder whoamiRoute(WhoamiRoute route);

    /**
     * Sets the cloud events route.
     *
     * @param route the route to set.
     * @return the Builder to allow method chaining.
     * @since 1.5.0
     */
    RootRouteBuilder cloudEventsRoute(CloudEventsRoute route);

    /**
     * Sets the http authentication directive.
     *
     * @param directive the directive to set.
     * @return the Builder to allow method chaining.
     */
    RootRouteBuilder httpAuthenticationDirective(GatewayAuthenticationDirective directive);

    /**
     * Sets the websocket authentication directive.
     *
     * @param directive the directive to set.
     * @return the Builder to allow method chaining.
     */
    RootRouteBuilder wsAuthenticationDirective(GatewayAuthenticationDirective directive);

    /**
     * Sets the supported API versions.
     *
     * @param versions the versions to set.
     * @return the Builder to allow method chaining.
     * @throws NullPointerException if {@code versions} is {@code null}.
     */
    RootRouteBuilder supportedSchemaVersions(Collection<JsonSchemaVersion> versions);

    /**
     * Sets the protocol adapter provider.
     *
     * @param provider the provider to set.
     * @return the Builder to allow method chaining.
     */
    RootRouteBuilder protocolAdapterProvider(ProtocolAdapterProvider provider);

    /**
     * Sets the header translator.
     *
     * @param translator the translator to set.
     * @return the Builder to allow method chaining.
     */
    RootRouteBuilder headerTranslator(HeaderTranslator translator);

    /**
     * Sets the custom api routes provider.
     *
     * @param provider the provider to set.
     * @return the Builder to allow method chaining.
     */
    RootRouteBuilder customApiRoutesProvider(CustomApiRoutesProvider provider, RouteBaseProperties routeBaseProperties);

    /**
     * Sets the custom headers handler.
     *
     * @param handler the handler to set.
     * @return the Builder to allow method chaining.
     */
    RootRouteBuilder customHeadersHandler(CustomHeadersHandler handler);

    /**
     * Sets the exception handler.
     *
     * @param handler the handler to set.
     * @return the Builder to allow method chaining.
     */
    RootRouteBuilder exceptionHandler(ExceptionHandler handler);

    /**
     * Sets the rejection handler.
     *
     * @param handler the handler to set.
     * @return the Builder to allow method chaining.
     */
    RootRouteBuilder rejectionHandler(RejectionHandler handler);

    /**
     * Sets the headers validator.
     *
     * @return the builder.
     */
    RootRouteBuilder dittoHeadersValidator(DittoHeadersValidator dittoHeadersValidator);

    /**
     * Builds the root route.
     *
     * @return the route.
     */
    Route build();

}
