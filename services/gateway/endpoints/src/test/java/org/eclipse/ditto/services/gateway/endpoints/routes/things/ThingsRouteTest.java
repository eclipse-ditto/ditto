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
package org.eclipse.ditto.services.gateway.endpoints.routes.things;

import java.util.UUID;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.services.gateway.endpoints.EndpointTestBase;
import org.eclipse.ditto.services.utils.protocol.ProtocolAdapterProvider;
import org.eclipse.ditto.signals.commands.things.exceptions.AttributePointerInvalidException;
import org.eclipse.ditto.signals.commands.things.exceptions.MissingThingIdsException;
import org.junit.Before;
import org.junit.Test;

import akka.actor.ActorSystem;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpEntities;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.RequestEntity;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.Route;
import akka.http.javadsl.testkit.TestRoute;
import akka.http.javadsl.testkit.TestRouteResult;

/**
 * Tests {@link ThingsRoute}.
 */
public final class ThingsRouteTest extends EndpointTestBase {

    private ThingsRoute thingsRoute;

    private TestRoute underTest;

    @Before
    public void setUp() {
        final ActorSystem actorSystem = system();
        final ProtocolAdapterProvider adapterProvider = ProtocolAdapterProvider.load(protocolConfig, actorSystem);

        thingsRoute = new ThingsRoute(createDummyResponseActor(), actorSystem, messageConfig, claimMessageConfig,
                httpConfig, adapterProvider.getHttpHeaderTranslator());

        final Route route = extractRequestContext(ctx -> thingsRoute.buildThingsRoute(ctx, DittoHeaders.empty()));
        underTest = testRoute(route);
    }

    @Test
    public void postFeaturesReturnsMethodNotAllowed() {
        final TestRouteResult result = underTest.run(HttpRequest.POST("/things/org.eclipse.ditto%3Adummy/features"));
        result.assertStatusCode(StatusCodes.METHOD_NOT_ALLOWED);
    }

    @Test
    public void getThingWithVeryLongId() {
        final int numberOfUUIDs = 100;
        final StringBuilder pathBuilder = new StringBuilder("/things/").append("namespace");
        for (int i = 0; i < numberOfUUIDs; ++i) {
            pathBuilder.append(':').append(UUID.randomUUID());
        }
        final TestRouteResult result = underTest.run(HttpRequest.GET(pathBuilder.toString()));
        result.assertStatusCode(StatusCodes.OK);
    }

    @Test
    public void createThingWithInvalidInitialPolicy() {
        final String body = "{\"_policy\"org.eclipse.ditto:1234}";
        final RequestEntity requestEntity = HttpEntities.create(ContentTypes.APPLICATION_JSON, body);
        final TestRouteResult result = underTest.run(HttpRequest.POST("/things").withEntity(requestEntity));
        result.assertStatusCode(StatusCodes.BAD_REQUEST);
    }

    @Test
    public void getThingsWithEmptyIdsList() {
        final TestRouteResult result = underTest.run(HttpRequest.GET("/things?ids="));
        result.assertStatusCode(StatusCodes.BAD_REQUEST);
        final MissingThingIdsException expectedEx = MissingThingIdsException.newBuilder()
                .dittoHeaders(DittoHeaders.empty())
                .build();
        result.assertEntity(expectedEx.toJsonString());
    }

    @Test
    public void putAttributeWithEmptyPointer() {
        final String body = "\"bumlux\"";
        final HttpRequest request = HttpRequest.PUT("/things/org.eclipse.ditto%3Adummy/attributes//")
                .withEntity(HttpEntities.create(ContentTypes.APPLICATION_JSON, body));
        final TestRouteResult result =
                underTest.run(request);
        result.assertStatusCode(StatusCodes.BAD_REQUEST);
        final AttributePointerInvalidException expected =
                AttributePointerInvalidException.newBuilder(JsonPointer.empty())
                        .dittoHeaders(DittoHeaders.empty())
                        .build();
        result.assertEntity(expected.toJsonString());
    }

}
