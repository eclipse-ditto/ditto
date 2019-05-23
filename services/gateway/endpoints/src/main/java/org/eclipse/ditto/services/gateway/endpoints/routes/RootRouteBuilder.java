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
package org.eclipse.ditto.services.gateway.endpoints.routes;

import java.util.Collection;

import org.eclipse.ditto.model.base.headers.DittoHeadersSizeChecker;
import org.eclipse.ditto.protocoladapter.HeaderTranslator;
import org.eclipse.ditto.services.gateway.endpoints.directives.auth.GatewayAuthenticationDirective;
import org.eclipse.ditto.services.gateway.endpoints.routes.devops.DevOpsRoute;
import org.eclipse.ditto.services.gateway.endpoints.routes.health.CachingHealthRoute;
import org.eclipse.ditto.services.gateway.endpoints.routes.policies.PoliciesRoute;
import org.eclipse.ditto.services.gateway.endpoints.routes.sse.SseThingsRoute;
import org.eclipse.ditto.services.gateway.endpoints.routes.stats.StatsRoute;
import org.eclipse.ditto.services.gateway.endpoints.routes.status.OverallStatusRoute;
import org.eclipse.ditto.services.gateway.endpoints.routes.things.ThingsRoute;
import org.eclipse.ditto.services.gateway.endpoints.routes.thingsearch.ThingSearchRoute;
import org.eclipse.ditto.services.gateway.endpoints.routes.websocket.WebsocketRoute;
import org.eclipse.ditto.services.utils.health.routes.StatusRoute;
import org.eclipse.ditto.services.utils.protocol.ProtocolAdapterProvider;

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
     * @param route the route to set.
     * @return the Builder to allow method chaining.
     */
    RootRouteBuilder sseThingsRoute(SseThingsRoute route);

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
     * Sets the websocket sub-route.
     *
     * @param route the route to set.
     * @return the Builder to allow method chaining.
     */
    RootRouteBuilder websocketRoute(WebsocketRoute route);

    /**
     * Sets the stats sub-route.
     *
     * @param route the route to set.
     * @return the Builder to allow method chaining.
     */
    RootRouteBuilder statsRoute(StatsRoute route);

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
     * Sets the supported api versions.
     *
     * @param versions the versions to set.
     * @return the Builder to allow method chaining.
     */
    RootRouteBuilder supportedSchemaVersions(Collection<Integer> versions);

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
    RootRouteBuilder customApiRoutesProvider(CustomApiRoutesProvider provider);

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
     * Sets the headers size checker.
     *
     * @return the builder.
     */
    RootRouteBuilder dittoHeadersSizeChecker(DittoHeadersSizeChecker checker);

    /**
     * Builds the root route.
     *
     * @return the route.
     */
    Route build();

}
