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
package org.eclipse.ditto.services.gateway.endpoints.routes.stats;

import static org.eclipse.ditto.services.gateway.endpoints.EndpointTestConstants.KNOWN_CORRELATION_ID;
import static org.eclipse.ditto.services.gateway.endpoints.EndpointTestConstants.UNKNOWN_PATH;

import java.util.Optional;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.services.gateway.endpoints.EndpointTestBase;
import org.eclipse.ditto.services.gateway.endpoints.EndpointTestConstants;
import org.eclipse.ditto.signals.commands.thingsearch.query.CountThingsResponse;
import org.junit.Before;
import org.junit.Test;

import akka.actor.ActorRef;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.testkit.TestRoute;
import akka.http.javadsl.testkit.TestRouteResult;

/**
 * Tests {@link StatsRoute}.
 */
public class StatsRouteTest extends EndpointTestBase {

    private static final String STATS_PATH = "/" + StatsRoute.STATISTICS_PATH_PREFIX;

    private TestRoute statsTestRoute;

    @Before
    public void setUp() {
        final ActorRef proxyActor = createDummyResponseActor();
        setUp(proxyActor);
    }

    private void setUp(final ActorRef proxyActor) {
        final StatsRoute statsRoute = new StatsRoute(proxyActor, system());
        statsTestRoute = testRoute(statsRoute.buildStatsRoute(KNOWN_CORRELATION_ID));
    }

    @Test
    public void getStatsToplevelUrl() {
        final TestRouteResult result = statsTestRoute.run(HttpRequest.GET(STATS_PATH));
        result.assertStatusCode(StatusCodes.NOT_FOUND);
    }

    @Test
    public void getStatsThingsUrl() {
        final TestRouteResult result = statsTestRoute.run(HttpRequest.GET(STATS_PATH +
                "/" + StatsRoute.THINGS_PATH));
        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
    }

    @Test
    public void postStatsThingsUrlReturnsMethodNotAllowed() {
        final TestRouteResult result = statsTestRoute.run(HttpRequest.POST(STATS_PATH +
                "/" + StatsRoute.THINGS_PATH));
        result.assertStatusCode(StatusCodes.METHOD_NOT_ALLOWED);
    }

    @Test
    public void getStatsSearchUrl() {
        setUp(createDummyResponseActor(m -> Optional.of(CountThingsResponse.of(42, DittoHeaders.empty()))));

        final TestRouteResult result = statsTestRoute.run(HttpRequest.GET(STATS_PATH +
                "/" + StatsRoute.SEARCH_PATH));
        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
    }

    @Test
    public void getNonExistingToplevelUrl() {
        final TestRouteResult result = statsTestRoute.run(HttpRequest.GET(UNKNOWN_PATH));
        result.assertStatusCode(StatusCodes.NOT_FOUND);
    }

}
