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
package org.eclipse.ditto.services.gateway.endpoints.routes;

import static org.eclipse.ditto.services.gateway.endpoints.EndpointTestConstants.KNOWN_DOMAIN;
import static org.eclipse.ditto.services.gateway.endpoints.EndpointTestConstants.UNKNOWN_PATH;
import static org.mockito.Mockito.mock;

import java.util.function.Supplier;

import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.services.gateway.endpoints.EndpointTestBase;
import org.eclipse.ditto.services.gateway.endpoints.EndpointTestConstants;
import org.eclipse.ditto.services.gateway.endpoints.directives.HttpsEnsuringDirective;
import org.eclipse.ditto.services.gateway.endpoints.routes.status.OverallStatusRoute;
import org.eclipse.ditto.services.gateway.endpoints.routes.things.ThingsParameter;
import org.eclipse.ditto.services.gateway.endpoints.routes.things.ThingsRoute;
import org.eclipse.ditto.services.gateway.endpoints.routes.thingsearch.ThingSearchRoute;
import org.eclipse.ditto.services.gateway.security.HttpHeader;
import org.eclipse.ditto.services.gateway.starter.service.util.HttpClientFacade;
import org.eclipse.ditto.services.utils.health.cluster.ClusterStatus;
import org.junit.Before;
import org.junit.Test;

import akka.actor.ActorRef;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.model.headers.Location;
import akka.http.javadsl.model.headers.RawHeader;
import akka.http.javadsl.testkit.TestRoute;
import akka.http.javadsl.testkit.TestRouteResult;

/**
 * Tests {@link RootRoute}.
 */
public final class RootRouteTest extends EndpointTestBase {

    private static final String ROOT_PATH = "/";
    private static final String STATUS_PATH = ROOT_PATH + OverallStatusRoute.PATH_STATUS;
    private static final String THINGS_1_PATH = ROOT_PATH + RootRoute.HTTP_PATH_API_PREFIX + "/" + JsonSchemaVersion
            .V_1.toInt() + "/" + ThingsRoute.PATH_THINGS;
    private static final String THINGS_2_PATH = ROOT_PATH + RootRoute.HTTP_PATH_API_PREFIX + "/" + JsonSchemaVersion
            .V_2.toInt() + "/" + ThingsRoute.PATH_THINGS;
    private static final String THING_SEARCH_2_PATH = ROOT_PATH + RootRoute.HTTP_PATH_API_PREFIX + "/" +
            JsonSchemaVersion.V_2.toInt() + "/" + ThingSearchRoute.PATH_SEARCH + "/" + ThingSearchRoute.PATH_THINGS;
    private static final String UNKNOWN_SEARCH_PATH =
            ROOT_PATH + RootRoute.HTTP_PATH_API_PREFIX + "/" + JsonSchemaVersion.V_2.toInt() + "/" +
                    ThingSearchRoute.PATH_SEARCH + "/foo";
    private static final String THINGS_1_PATH_WITH_IDS = THINGS_1_PATH + "?" +
            ThingsParameter.IDS + "=bumlux";
    private static final String WS_2_PATH = ROOT_PATH + RootRoute.WS_PATH_PREFIX + "/" + JsonSchemaVersion
            .V_2.toInt();

    private static final String PATH_WITH_INVALID_ENCODING = ROOT_PATH + RootRoute.HTTP_PATH_API_PREFIX + "/" +
            JsonSchemaVersion
                    .V_1.toInt() + "/" + ThingsRoute.PATH_THINGS + "/:bumlux/features?fields=feature-1%2properties";

    private static final String HTTPS = "https";

    private TestRoute rootTestRoute;

    @Before
    public void setUp() {
        final ActorRef proxyActor = createDummyResponseActor();
        final ActorRef streamingActor = createDummyResponseActor();
        final ActorRef healthCheckingActor =
                createHealthCheckingActorMock();
        final Supplier<ClusterStatus> clusterStateSupplier = createClusterStatusSupplierMock();
        final HttpClientFacade httpClient = mock(HttpClientFacade.class);
        final RootRoute rootRoute =
                new RootRoute(system(), getConfig(), proxyActor, streamingActor,
                        healthCheckingActor, clusterStateSupplier, httpClient);
        rootTestRoute = testRoute(rootRoute.buildRoute());
    }

    @Test
    public void getRoot() {
        final TestRouteResult result =
                rootTestRoute.run(withHttps(withDummyAuthentication(HttpRequest.GET(ROOT_PATH))));
        result.assertStatusCode(StatusCodes.NOT_FOUND);
    }

    @Test
    public void getStatusWithAuth() {
        final TestRouteResult result =
                rootTestRoute.run(withHttps(withDevopsCredentials(HttpRequest.GET(STATUS_PATH))));
        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
    }

    @Test
    public void getStatusWithoutAuth() {
        final TestRouteResult result = rootTestRoute.run(withHttps(HttpRequest.GET(STATUS_PATH)));
        result.assertStatusCode(StatusCodes.UNAUTHORIZED);
    }

    @Test
    public void getStatusUrlWithoutHttps() {
        final TestRouteResult result = rootTestRoute.run(HttpRequest.GET(STATUS_PATH));
        result.assertStatusCode(StatusCodes.NOT_FOUND);
    }

    @Test
    public void getThingsUrlWithoutIds() {
        final TestRouteResult result =
                rootTestRoute.run(withHttps(withDummyAuthentication(HttpRequest.GET(THINGS_1_PATH))));
        result.assertStatusCode(StatusCodes.NOT_FOUND);
    }

    @Test
    public void getThingsUrlWithIds() {
        final TestRouteResult result =
                rootTestRoute.run(withHttps(withDummyAuthentication(HttpRequest.GET(
                        THINGS_1_PATH_WITH_IDS))));
        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
    }

    @Test
    public void getThings1UrlWithoutHttps() {
        final TestRouteResult result = rootTestRoute.run(HttpRequest.GET(THINGS_1_PATH));
        result.assertStatusCode(StatusCodes.NOT_FOUND);
    }

    @Test
    public void getThings2UrlWithoutHttps() {
        final TestRouteResult result = rootTestRoute.run(HttpRequest.GET(THINGS_2_PATH));
        result.assertStatusCode(StatusCodes.NOT_FOUND);
    }

    @Test
    public void getThingsUrlWithIdsWithWrongVersionNumber() {
        final String thingsUrlWithIdsWithWrongVersionNumber = ROOT_PATH + RootRoute.HTTP_PATH_API_PREFIX + "/" +
                "nan" + "/" + ThingsRoute.PATH_THINGS + "?" +
                ThingsParameter.IDS + "=bumlux";
        final TestRouteResult result =
                rootTestRoute.run(withHttps(withDummyAuthentication(HttpRequest.GET(
                        thingsUrlWithIdsWithWrongVersionNumber))));
        result.assertStatusCode(StatusCodes.NOT_FOUND);
    }

    @Test
    public void getThingsUrlWithIdsWithNonExistingVersionNumber() {
        final int nonExistingVersion = 9999;
        final String thingsUrlWithIdsWithNonExistingVersionNumber = ROOT_PATH + RootRoute.HTTP_PATH_API_PREFIX + "/" +
                nonExistingVersion + "/" + ThingsRoute.PATH_THINGS + "?" +
                ThingsParameter.IDS + "=bumlux";
        final TestRouteResult result =
                rootTestRoute.run(withHttps(withDummyAuthentication(HttpRequest.GET(
                        thingsUrlWithIdsWithNonExistingVersionNumber))));
        result.assertStatusCode(StatusCodes.NOT_FOUND);
    }

    @Test
    public void getThingSearchUrl() {
        final HttpRequest request =
                withHttps(withDummyAuthentication(HttpRequest.GET(THING_SEARCH_2_PATH)));

        final TestRouteResult result = rootTestRoute.run(request);

        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
    }

    @Test
    public void getNonExistingSearchUrl() {
        final HttpRequest request =
                withHttps(withDummyAuthentication(HttpRequest.GET(UNKNOWN_SEARCH_PATH)));

        final TestRouteResult result = rootTestRoute.run(request);

        result.assertStatusCode(StatusCodes.NOT_FOUND);
    }

    @Test
    public void getWsUrlWithoutUpgrade() {
        final TestRouteResult result =
                rootTestRoute.run(withHttps(withDummyAuthentication(HttpRequest.GET(WS_2_PATH))));
        assertWebsocketUpgradeExpectedResult(result);
    }

    @Test
    public void getWsUrlWithoutHttps() {
        final TestRouteResult result = rootTestRoute.run(HttpRequest.GET(WS_2_PATH));
        result.assertStatusCode(StatusCodes.NOT_FOUND);
    }

    @Test
    public void getNonExistingToplevelUrl() {
        final TestRouteResult result = rootTestRoute.run(withHttps(HttpRequest.GET(UNKNOWN_PATH)));
        result.assertStatusCode(StatusCodes.NOT_FOUND);
    }

    @Test
    public void getNonExistingToplevelUrlWithoutHttps() {
        final TestRouteResult result = rootTestRoute.run(HttpRequest.GET(UNKNOWN_PATH));
        result.assertStatusCode(StatusCodes.MOVED_PERMANENTLY);
        result.assertHeaderExists(Location.create(HTTPS + "://" + KNOWN_DOMAIN + UNKNOWN_PATH));
    }

    @Test
    public void getWithInvalidEncoding() {
        final TestRouteResult result = rootTestRoute.run(HttpRequest.GET(PATH_WITH_INVALID_ENCODING));
        result.assertStatusCode(StatusCodes.BAD_REQUEST);
    }

    protected HttpRequest withHttps(final HttpRequest httpRequest) {
        return httpRequest.addHeader(RawHeader.create
                (HttpsEnsuringDirective.X_FORWARDED_PROTO_LBAAS, HTTPS));
    }

    protected HttpRequest withDummyAuthentication(final HttpRequest httpRequest) {
        return httpRequest.addHeader(RawHeader.create
                (HttpHeader.X_DITTO_DUMMY_AUTH.getName(), "some-issuer:foo"));
    }

}
