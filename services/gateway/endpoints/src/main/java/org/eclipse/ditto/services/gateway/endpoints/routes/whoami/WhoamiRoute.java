/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

package org.eclipse.ditto.services.gateway.endpoints.routes.whoami;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.protocol.HeaderTranslator;
import org.eclipse.ditto.services.gateway.endpoints.routes.AbstractRoute;
import org.eclipse.ditto.services.gateway.util.config.endpoints.CommandConfig;
import org.eclipse.ditto.services.gateway.util.config.endpoints.HttpConfig;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.http.javadsl.server.PathMatchers;
import akka.http.javadsl.server.RequestContext;
import akka.http.javadsl.server.Route;

/**
 * Route providing information about the current user.
 * @since 1.2.0
 */
public final class WhoamiRoute extends AbstractRoute {

    public static final String PATH_WHOAMI = "whoami";

    /**
     * Constructs the abstract route builder.
     *
     * @param proxyActor an actor selection of the actor handling delegating to persistence.
     * @param actorSystem the ActorSystem to use.
     * @param httpConfig the configuration settings of the Gateway service's HTTP endpoint.
     * @param commandConfig the configuration settings for incoming commands (via HTTP requests) in the gateway.
     * @param headerTranslator translates headers from external sources or to external sources.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public WhoamiRoute(final ActorRef proxyActor, final ActorSystem actorSystem,
            final HttpConfig httpConfig,
            final CommandConfig commandConfig,
            final HeaderTranslator headerTranslator) {
        super(proxyActor, actorSystem, httpConfig, commandConfig, headerTranslator);
    }

    public Route buildWhoamiRoute(final RequestContext ctx, final DittoHeaders dittoHeaders) {
        return rawPathPrefix(PathMatchers.slash().concat(PATH_WHOAMI), () -> whoami(ctx, dittoHeaders));
    }

    private Route whoami(final RequestContext ctx, final DittoHeaders dittoHeaders) {
        return pathEndOrSingleSlash(() ->
                get(() -> // GET /whoami
                       handlePerRequest(ctx, Whoami.of(dittoHeaders))
                )
        );
    }

}
