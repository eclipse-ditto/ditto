/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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

package org.eclipse.ditto.gateway.service.endpoints.routes.wot;

import org.apache.pekko.http.javadsl.server.RequestContext;
import org.apache.pekko.http.javadsl.server.Route;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.gateway.service.endpoints.routes.AbstractRoute;
import org.eclipse.ditto.gateway.service.endpoints.routes.RouteBaseProperties;

/**
 * Route providing a WoT (Web of Things) "Thing Directory" at specified endpoint {@code /.well-known/wot}.
 *
 * @since 3.9.0
 */
public final class WotDiscoveryThingDirectoryRoute extends AbstractRoute {

    public static final String PATH_WELLKNOWN_WOT = ".well-known";
    public static final String PATH_WOT = "wot";

    /**
     * Constructs a {@code WhoamiRoute} object.
     *
     * @param routeBaseProperties the base properties of the route.
     * @throws NullPointerException if {@code routeBaseProperties} is {@code null}.
     */
    public WotDiscoveryThingDirectoryRoute(final RouteBaseProperties routeBaseProperties) {
        super(routeBaseProperties);
    }

    public Route buildRoute(final RequestContext ctx, final DittoHeaders dittoHeaders) {
        return pathPrefix(PATH_WELLKNOWN_WOT, () -> pathPrefix(PATH_WOT, () ->
                wot(ctx, dittoHeaders)
        ));
    }

    private Route wot(final RequestContext ctx, final DittoHeaders dittoHeaders) {
        return pathEndOrSingleSlash(() ->
                get(() -> // GET /.well-known/wot
                        handlePerRequest(ctx, RetrieveWotDiscoveryThingDirectory.of(dittoHeaders))
                )
        );
    }

}
