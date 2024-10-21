/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.gateway.service.endpoints.routes.checkpermissions;

import org.apache.pekko.http.javadsl.server.PathMatchers;
import org.apache.pekko.http.javadsl.server.RequestContext;
import org.apache.pekko.http.javadsl.server.Route;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.gateway.service.endpoints.routes.AbstractRoute;
import org.eclipse.ditto.gateway.service.endpoints.routes.RouteBaseProperties;
import org.eclipse.ditto.json.JsonFactory;

/**
 * Route for checking permissions on multiple resources.
 */
public final class CheckPermissionsRoute extends AbstractRoute {

    private static final String CHECK_PERMISSIONS_PATH = "checkPermissions";

    /**
     * Constructs the CheckPermissionsRoute object.
     *
     * @param routeBaseProperties the base properties for the route.
     */
    public CheckPermissionsRoute(final RouteBaseProperties routeBaseProperties) {
        super(routeBaseProperties);
    }

    /**
     * Builds the route for checking permissions.
     *
     * @param ctx the HTTP request context.
     * @param dittoHeaders the Ditto headers extracted from the request.
     * @return the route for handling check permissions requests.
     */
    public Route buildCheckPermissionsRoute(final RequestContext ctx, final DittoHeaders dittoHeaders) {
        return rawPathPrefix(PathMatchers.slash().concat(CHECK_PERMISSIONS_PATH), () ->
                pathEndOrSingleSlash(() ->
                        // POST /checkPermissions
                        post(() ->
                                ensureMediaTypeJsonWithFallbacksThenExtractDataBytes(ctx, dittoHeaders,
                                        payloadSource ->
                                                handlePerRequest(ctx, dittoHeaders, payloadSource,
                                                        jsonPayload -> CheckPermissions.fromJson(
                                                                JsonFactory.newObject(jsonPayload), dittoHeaders)
                                                )
                                )
                        )
                )
        );
    }

}
