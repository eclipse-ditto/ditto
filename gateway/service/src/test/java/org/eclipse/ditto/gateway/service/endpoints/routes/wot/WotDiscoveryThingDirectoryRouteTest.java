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

import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.StatusCodes;
import org.apache.pekko.http.javadsl.server.Route;
import org.apache.pekko.http.javadsl.testkit.TestRoute;
import org.apache.pekko.http.javadsl.testkit.TestRouteResult;
import org.eclipse.ditto.gateway.service.endpoints.EndpointTestBase;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link WotDiscoveryThingDirectoryRoute}.
 */
public final class WotDiscoveryThingDirectoryRouteTest extends EndpointTestBase {

    private WotDiscoveryThingDirectoryRoute wotDiscoveryThingDirectoryRoute;
    private TestRoute underTest;

    @Before
    public void setUp() {
        wotDiscoveryThingDirectoryRoute = new WotDiscoveryThingDirectoryRoute(routeBaseProperties);
        final Route route = extractRequestContext(ctx ->
                wotDiscoveryThingDirectoryRoute.buildRoute(ctx, dittoHeaders));
        underTest = testRoute(route);
    }

    @Test
    public void getWotDiscoveryThingDirectoryReturnsOk() {
        final TestRouteResult result = underTest.run(HttpRequest.GET("/.well-known/wot"));

        result.assertStatusCode(StatusCodes.OK);
    }

    @Test
    public void getWotDiscoveryThingDirectoryWithTrailingSlashReturnsOk() {
        final TestRouteResult result = underTest.run(HttpRequest.GET("/.well-known/wot/"));

        result.assertStatusCode(StatusCodes.OK);
    }

    @Test
    public void headWotDiscoveryThingDirectoryReturnsOk() {
        final TestRouteResult result = underTest.run(HttpRequest.HEAD("/.well-known/wot"));

        result.assertStatusCode(StatusCodes.OK);
    }

    @Test
    public void headWotDiscoveryThingDirectoryWithTrailingSlashReturnsOk() {
        final TestRouteResult result = underTest.run(HttpRequest.HEAD("/.well-known/wot/"));

        result.assertStatusCode(StatusCodes.OK);
    }

    @Test
    public void postToWotDiscoveryReturnsMethodNotAllowed() {
        final TestRouteResult result = underTest.run(HttpRequest.POST("/.well-known/wot"));

        result.assertStatusCode(StatusCodes.METHOD_NOT_ALLOWED);
    }

    @Test
    public void putToWotDiscoveryReturnsMethodNotAllowed() {
        final TestRouteResult result = underTest.run(HttpRequest.PUT("/.well-known/wot"));

        result.assertStatusCode(StatusCodes.METHOD_NOT_ALLOWED);
    }

    @Test
    public void deleteToWotDiscoveryReturnsMethodNotAllowed() {
        final TestRouteResult result = underTest.run(HttpRequest.DELETE("/.well-known/wot"));

        result.assertStatusCode(StatusCodes.METHOD_NOT_ALLOWED);
    }

    @Test
    public void nonExistingSubPathReturnsNotFound() {
        final TestRouteResult result = underTest.run(HttpRequest.GET("/.well-known/wot/unknown"));

        result.assertStatusCode(StatusCodes.NOT_FOUND);
    }

    @Test
    public void routeHasCorrectPathConstants() {
        org.assertj.core.api.Assertions.assertThat(WotDiscoveryThingDirectoryRoute.PATH_WELLKNOWN_WOT)
                .isEqualTo(".well-known");
        org.assertj.core.api.Assertions.assertThat(WotDiscoveryThingDirectoryRoute.PATH_WOT)
                .isEqualTo("wot");
    }

}
