/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.gateway.endpoints.routes.things;

import java.time.Duration;
import java.util.UUID;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.services.gateway.endpoints.EndpointTestBase;
import org.eclipse.ditto.services.gateway.endpoints.routes.RootRoute;
import org.junit.Before;
import org.junit.Test;

import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.Route;
import akka.http.javadsl.testkit.TestRoute;
import akka.http.javadsl.testkit.TestRouteResult;

/**
 * Tests {@link ThingsRoute}.
 */
public class ThingsRouteTest extends EndpointTestBase {

    private ThingsRoute thingsRoute;

    private TestRoute underTest;

    @Before
    public void setUp() {
        thingsRoute = new ThingsRoute(createDummyResponseActor(), system(), Duration.ZERO, Duration.ZERO,
                Duration.ZERO, Duration.ZERO);

        final Route route =
                extractRequestContext(ctx -> thingsRoute.buildThingsRoute(ctx, DittoHeaders.newBuilder().build()));
        underTest = testRoute(route);
    }

    @Test
    public void postFeaturesReturnsMethodNotAllowed() {
        final TestRouteResult result = underTest.run(HttpRequest.POST("/things/%3Adummy/features"));
        result.assertStatusCode(StatusCodes.METHOD_NOT_ALLOWED);
    }

    // THIS TEST SHOULD FAIL!
    @Test(expected = StackOverflowError.class)
    public void getThingWithVeryLongId() {
        final int numberOfUUIDs = 100;
        final StringBuilder pathBuilder = new StringBuilder("/things/");
        for (int i = 0; i < numberOfUUIDs; ++i) {
            pathBuilder.append(':').append(UUID.randomUUID());
        }
        underTest.run(HttpRequest.GET(pathBuilder.toString()));
    }

    @Test
    public void getThingsWitEmptyIdsList() {
        final TestRouteResult result = underTest.run(HttpRequest.GET("/things?ids="));
        result.assertStatusCode(StatusCodes.BAD_REQUEST);
        result.assertEntity("{" +
                "\"status\":400," +
                "\"error\":\"things:thing.ids.missing\"," +
                "\"message\":\"The required list of thing ids was null or empty.\"," +
                "\"description\":\"Please provide at least one thing id and try again.\"" +
                "}");
    }

    @Test
    public void getThingsWithoutIds() {
        final TestRouteResult result = underTest.run(HttpRequest.GET("/things"));
        result.assertStatusCode(StatusCodes.BAD_REQUEST);
        result.assertEntity("Request is missing parameter: 'ids'");
    }
}
