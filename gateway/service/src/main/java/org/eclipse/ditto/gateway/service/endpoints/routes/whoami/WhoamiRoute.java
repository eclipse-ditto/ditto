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

package org.eclipse.ditto.gateway.service.endpoints.routes.whoami;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.gateway.service.endpoints.routes.AbstractRoute;
import org.eclipse.ditto.gateway.service.endpoints.routes.RouteBaseProperties;

import akka.http.javadsl.server.PathMatchers;
import akka.http.javadsl.server.RequestContext;
import akka.http.javadsl.server.Route;

/**
 * Route providing information about the current user.
 *
 * @since 1.2.0
 */
public final class WhoamiRoute extends AbstractRoute {

    public static final String PATH_WHOAMI = "whoami";

    /**
     * Constructs a {@code WhoamiRoute} object.
     *
     * @param routeBaseProperties the base properties of the route.
     * @throws NullPointerException if {@code routeBaseProperties} is {@code null}.
     */
    public WhoamiRoute(final RouteBaseProperties routeBaseProperties) {
        super(routeBaseProperties);
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
