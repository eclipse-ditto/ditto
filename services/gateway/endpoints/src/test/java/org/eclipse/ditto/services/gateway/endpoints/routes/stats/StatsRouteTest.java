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
package org.eclipse.ditto.services.gateway.endpoints.routes.stats;

import static org.eclipse.ditto.services.gateway.endpoints.EndpointTestConstants.KNOWN_CORRELATION_ID;
import static org.eclipse.ditto.services.gateway.endpoints.EndpointTestConstants.UNKNOWN_PATH;

import java.util.Optional;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.services.gateway.endpoints.EndpointTestBase;
import org.eclipse.ditto.services.gateway.endpoints.EndpointTestConstants;
import org.eclipse.ditto.services.utils.protocol.ProtocolAdapterProvider;
import org.eclipse.ditto.signals.commands.thingsearch.query.CountThingsResponse;
import org.junit.Before;
import org.junit.Test;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.testkit.TestRoute;
import akka.http.javadsl.testkit.TestRouteResult;

/**
 * Tests {@link StatsRoute}.
 */
public final class StatsRouteTest extends EndpointTestBase {

    private static final String STATS_PATH = "/" + StatsRoute.STATISTICS_PATH_PREFIX;

    private TestRoute statsTestRoute;

    @Before
    public void setUp() {
        final ActorRef proxyActor = createDummyResponseActor();
        setUp(proxyActor);
    }

    private void setUp(final ActorRef proxyActor) {
        final ActorSystem actorSystem = system();
        final ProtocolAdapterProvider adapterProvider = ProtocolAdapterProvider.load(protocolConfig, actorSystem);
        final StatsRoute statsRoute = new StatsRoute(proxyActor, actorSystem, httpConfig, commandConfig,
                authConfig.getDevOpsConfig(),
                adapterProvider.getHttpHeaderTranslator());

        statsTestRoute = testRoute(statsRoute.buildStatsRoute(KNOWN_CORRELATION_ID));
    }

    @Test
    public void getStatsToplevelUrl() {
        final TestRouteResult result = statsTestRoute.run(HttpRequest.GET(STATS_PATH));
        result.assertStatusCode(StatusCodes.NOT_FOUND);
    }

    @Test
    public void getStatsThingsUrl() {
        final TestRouteResult result = statsTestRoute.run(HttpRequest.GET(STATS_PATH + "/" + StatsRoute.THINGS_PATH));
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
