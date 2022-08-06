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
package org.eclipse.ditto.gateway.service.endpoints.routes.stats;

import static org.eclipse.ditto.gateway.service.endpoints.EndpointTestConstants.KNOWN_CORRELATION_ID;
import static org.eclipse.ditto.gateway.service.endpoints.EndpointTestConstants.UNKNOWN_PATH;

import java.util.Optional;
import java.util.UUID;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.gateway.service.endpoints.EndpointTestBase;
import org.eclipse.ditto.gateway.service.endpoints.EndpointTestConstants;
import org.eclipse.ditto.gateway.service.endpoints.directives.auth.DevOpsOAuth2AuthenticationDirective;
import org.eclipse.ditto.gateway.service.endpoints.routes.RouteBaseProperties;
import org.eclipse.ditto.gateway.service.security.authentication.jwt.JwtAuthenticationFactory;
import org.eclipse.ditto.gateway.service.security.authentication.jwt.JwtAuthenticationProvider;
import org.eclipse.ditto.gateway.service.util.config.security.DevOpsConfig;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;
import org.eclipse.ditto.thingsearch.model.signals.commands.query.CountThingsResponse;
import org.junit.Before;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.testkit.TestRoute;

/**
 * Tests {@link StatsRoute}.
 */
public final class StatsRouteTest extends EndpointTestBase {

    private static final String STATS_PATH = "/" + StatsRoute.STATISTICS_PATH_PREFIX;
    private static final ActorSystem actorSystem =
            ActorSystem.create(UUID.randomUUID().toString(), ConfigFactory.load("test"));

    private TestRoute statsTestRoute;
    private DevOpsConfig devOpsConfig;

    @Before
    public void setUp() {
        devOpsConfig = authConfig.getDevOpsConfig();
        final var proxyActor = createDummyResponseActor();
        setUp(proxyActor);
    }

    private void setUp(final ActorRef proxyActor) {
        final var devopsJwtAuthenticationFactory =
                JwtAuthenticationFactory.newInstance(devOpsConfig.getOAuthConfig(), cacheConfig, httpClientFacade,
                        actorSystem);
        final var jwtAuthenticationProvider = JwtAuthenticationProvider.newInstance(
                devopsJwtAuthenticationFactory.newJwtAuthenticationResultProvider(ScopedConfig.DITTO_EXTENSIONS_SCOPE),
                devopsJwtAuthenticationFactory.getJwtValidator());
        final var routeBaseProperties = RouteBaseProperties.newBuilder(this.routeBaseProperties)
                .proxyActor(proxyActor)
                .build();
        final var statsRoute = new StatsRoute(routeBaseProperties,
                DevOpsOAuth2AuthenticationDirective.status(devOpsConfig, jwtAuthenticationProvider));

        statsTestRoute = testRoute(statsRoute.buildStatsRoute(KNOWN_CORRELATION_ID));
    }

    @Test
    public void getStatsToplevelUrl() {
        final var result = statsTestRoute.run(HttpRequest.GET(STATS_PATH));
        result.assertStatusCode(StatusCodes.NOT_FOUND);
    }

    @Test
    public void getStatsThingsUrl() {
        final var result = statsTestRoute.run(HttpRequest.GET(STATS_PATH + "/" + StatsRoute.THINGS_PATH));
        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
    }

    @Test
    public void postStatsThingsUrlReturnsMethodNotAllowed() {
        final var result = statsTestRoute.run(HttpRequest.POST(STATS_PATH +
                "/" + StatsRoute.THINGS_PATH));
        result.assertStatusCode(StatusCodes.METHOD_NOT_ALLOWED);
    }

    @Test
    public void getStatsSearchUrl() {
        setUp(createDummyResponseActor(m -> Optional.of(CountThingsResponse.of(42, DittoHeaders.empty()))));

        final var result = statsTestRoute.run(HttpRequest.GET(STATS_PATH +
                "/" + StatsRoute.SEARCH_PATH));
        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
    }

    @Test
    public void getNonExistingToplevelUrl() {
        final var result = statsTestRoute.run(HttpRequest.GET(UNKNOWN_PATH));
        result.assertStatusCode(StatusCodes.NOT_FOUND);
    }

}
