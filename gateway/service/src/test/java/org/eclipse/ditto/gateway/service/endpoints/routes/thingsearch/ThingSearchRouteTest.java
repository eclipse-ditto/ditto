/*
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.gateway.service.endpoints.routes.thingsearch;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;

import java.util.List;

import org.apache.pekko.http.javadsl.model.FormData;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.StatusCodes;
import org.apache.pekko.http.javadsl.server.Route;
import org.apache.pekko.http.javadsl.testkit.TestRoute;
import org.apache.pekko.japi.Pair;
import org.eclipse.ditto.gateway.service.endpoints.EndpointTestBase;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.junit.Before;
import org.junit.Test;

/**
 * Builder for creating Akka HTTP routes for {@code /search/things}.
 */
public final class ThingSearchRouteTest extends EndpointTestBase {

    private ThingSearchRoute thingsSearchRoute;
    private TestRoute underTest;


    @Before
    public void setUp() {
        thingsSearchRoute = new ThingSearchRoute(routeBaseProperties);
        final Route route = extractRequestContext(ctx -> thingsSearchRoute.buildSearchRoute(ctx, dittoHeaders));
        underTest = testRoute(handleExceptions(() -> route));
    }

    @Test
    public void postSearchThingsShouldGetParametersFromBody() {
        final var formData = FormData.create(
                List.of(
                    new Pair<>("filter", "and(like(definition,\"*test*\"))"),
                    new Pair<>("option", "sort(+thingId)"),
                    new Pair<>("option","limit(0,5)"),
                    new Pair<>("namespaces","org.eclipse.ditto,foo.bar")
                ));
        final var result = underTest.run(HttpRequest.POST("/search/things")
                .withEntity(formData.toEntity()));

        result.assertStatusCode(StatusCodes.OK);
        result.assertEntity("{\"type\":\"thing-search.commands:queryThings\",\"filter\":\"and(and(like(definition,\\\"*test*\\\")))\",\"options\":[\"limit(0\",\"5)\",\"sort(+thingId)\"],\"namespaces\":[\"foo.bar\",\"org.eclipse.ditto\"]}");
    }

    @Test
    public void searchThingsShouldGetFilter() {
        final var form = "and(and(like(definition,\"*test*\")))";

        final var result = underTest.run(
                HttpRequest.GET("/search/things?filter=and(like(definition,\"*test*\"))&option=sort(+thingId)&option=limit(0,5)&namespaces=org.eclipse.ditto,foo.bar"));

        result.assertStatusCode(StatusCodes.OK);

        assertThat(JsonObject.of(result.entityString()))
                .contains(
                        JsonKey.of("filter"),
                        form
                );
    }

    @Test
    public void countThingsShouldGetFilterFromBody() {
        final var formData = FormData.create(
                List.of(
                        new Pair<>("filter", "and(like(definition,\"*test*\"))"),
                        new Pair<>("option", "sort(+thingId)"),
                        new Pair<>("option","limit(0,5)"),
                        new Pair<>("namespaces","org.eclipse.ditto,foo.bar")
                ));
        final var result = underTest.run(HttpRequest.POST("/search/things/count")
                .withEntity(formData.toEntity()));

        result.assertStatusCode(StatusCodes.OK);
        assertThat(JsonObject.of(result.entityString()))
                .contains(
                        JsonKey.of("filter"),
                        "and(and(like(definition,\"*test*\")))"
                );
    }

    @Test
    public void countThingsShouldAssertBadRequest() {
        final var result = underTest.run(HttpRequest.POST("/search/things/count"));
        result.assertStatusCode(StatusCodes.UNSUPPORTED_MEDIA_TYPE);
    }
}
