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

import org.apache.pekko.http.javadsl.model.ContentTypes;
import org.apache.pekko.http.javadsl.model.HttpEntities;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.StatusCodes;
import org.apache.pekko.http.javadsl.server.Route;
import org.apache.pekko.http.javadsl.testkit.TestRoute;
import org.eclipse.ditto.gateway.service.endpoints.EndpointTestBase;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.policies.model.ResourceKey;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;

public final class CheckPermissionsRouteTest extends EndpointTestBase {

    private CheckPermissionsRoute checkPermissionsRoute;
    private TestRoute routeUnderTest;

    @Before
    public void setUp() {
        checkPermissionsRoute = new CheckPermissionsRoute(routeBaseProperties);
        final Route route = extractRequestContext(ctx -> checkPermissionsRoute.buildCheckPermissionsRoute(ctx, dittoHeaders));
        routeUnderTest = testRoute(handleExceptions(() -> route));
    }

    @Test
    public void postCheckPermissionsForwardCommand() {
        final var permissionResource =
                ImmutablePermissionCheck.of(ResourceKey.newInstance("policy:/"),
                        "org.eclipse.ditto:some-policy-1",
                        List.of("WRITE"));
        final var jsonPayload = JsonObject.newBuilder().set("check1", permissionResource.toJson()).build();
        final var request = HttpRequest.POST("/checkPermissions")
                .withEntity(HttpEntities.create(ContentTypes.APPLICATION_JSON, jsonPayload.toString()));

        final var result = routeUnderTest.run(request);
        result.assertStatusCode(StatusCodes.OK);

        assertThat(JsonObject.of(result.entityString()))
                .isEqualTo(JsonObject.newBuilder().set("check1", false).build());
    }
}
