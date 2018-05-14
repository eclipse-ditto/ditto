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
package org.eclipse.ditto.services.gateway.endpoints.directives;

import static org.eclipse.ditto.services.gateway.endpoints.EndpointTestConstants.KNOWN_CORRELATION_ID;
import static org.eclipse.ditto.services.gateway.endpoints.directives.ResponseRewritingDirective.INTERNAL_SERVER_ERROR_RESULT;
import static org.eclipse.ditto.services.gateway.endpoints.directives.ResponseRewritingDirective.UNAVAILABLE_ROUTE_RESULT;
import static org.eclipse.ditto.services.gateway.endpoints.directives.ResponseRewritingDirective.rewriteResponse;

import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.services.gateway.endpoints.EndpointTestBase;
import org.junit.After;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorSystem;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCode;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.Route;
import akka.http.javadsl.testkit.TestRoute;
import akka.http.javadsl.testkit.TestRouteResult;
import akka.stream.ActorMaterializer;
import akka.testkit.javadsl.TestKit;

/**
 * Tests {@link ResponseRewritingDirective}.
 */
public class ResponseRewritingDirectiveTest extends EndpointTestBase {

    private static final String PATH = "/";

    private TestRoute testRoute;
    private ActorSystem actorSystem;
    private ActorMaterializer actorMaterializer;

    private void setUp(final StatusCode statusCode) {
        setUp(statusCode, DEFAULT_DUMMY_ENTITY);
    }

    private void setUp(final StatusCode statusCode, final String entity) {
        final Config config = ConfigFactory.load("test");
        actorSystem = ActorSystem.create("AkkaTestSystem", config);
        actorMaterializer = ActorMaterializer.create(actorSystem);
        final Route root = route(get(
                () -> complete(HttpResponse.create().withEntity(entity).withStatus(statusCode))));
        final Route wrappedRoute = rewriteResponse(actorMaterializer, KNOWN_CORRELATION_ID, () -> root);
        testRoute = testRoute(wrappedRoute);
    }

    @After
    public void tearDown() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem);
            actorSystem = null;
            actorMaterializer = null;
        }
    }

    @Test
    public void status200IsNotConverted() {
        setUp(StatusCodes.OK);
        final TestRouteResult result = testRoute.run(HttpRequest.GET(PATH));
        assertResult(result, StatusCodes.OK, DEFAULT_DUMMY_ENTITY);
    }

    @Test
    public void status404IsNotConverted() {
        setUp(StatusCodes.NOT_FOUND);
        final TestRouteResult result = testRoute.run(HttpRequest.GET(PATH));
        assertResult(result, StatusCodes.NOT_FOUND, DEFAULT_DUMMY_ENTITY);
    }

    @Test
    public void status500IsConvertedTo503() {
        setUp(StatusCodes.INTERNAL_SERVER_ERROR);
        final TestRouteResult result = testRoute.run(HttpRequest.GET(PATH));
        assertResult(result, StatusCodes.SERVICE_UNAVAILABLE,
                entityToString(UNAVAILABLE_ROUTE_RESULT.getResponse().entity()));
    }

    @Test
    public void status501IsNotConverted() {
        setUp(StatusCodes.NOT_IMPLEMENTED);
        final TestRouteResult result = testRoute.run(HttpRequest.GET(PATH));
        assertResult(result, StatusCodes.NOT_IMPLEMENTED, DEFAULT_DUMMY_ENTITY);
    }

    @Test
    public void status502IsNotConverted() {
        setUp(StatusCodes.BAD_GATEWAY);
        final TestRouteResult result = testRoute.run(HttpRequest.GET(PATH));
        assertResult(result, StatusCodes.BAD_GATEWAY, DEFAULT_DUMMY_ENTITY);
    }

    @Test
    public void status503IsNotConverted() {
        setUp(StatusCodes.SERVICE_UNAVAILABLE);
        final TestRouteResult result = testRoute.run(HttpRequest.GET(PATH));
        assertResult(result, StatusCodes.SERVICE_UNAVAILABLE, DEFAULT_DUMMY_ENTITY);
    }

    @Test
    public void status504IsNotConverted() {
        setUp(StatusCodes.GATEWAY_TIMEOUT);
        final TestRouteResult result = testRoute.run(HttpRequest.GET(PATH));
        assertResult(result, StatusCodes.GATEWAY_TIMEOUT, DEFAULT_DUMMY_ENTITY);
    }

    @Test
    public void status505IsConvertedTo500() {
        setUp(StatusCodes.HTTPVERSION_NOT_SUPPORTED);
        final TestRouteResult result = testRoute.run(HttpRequest.GET(PATH));
        assertResult(result, StatusCodes.INTERNAL_SERVER_ERROR,
                entityToString(INTERNAL_SERVER_ERROR_RESULT.getResponse().entity()));
    }

    @Test
    public void dittoRuntimeExceptionIsNotConverted() {
        final String entity = DittoRuntimeException.newBuilder("loop.detected", HttpStatusCode.LOOP_DETECTED)
                .build()
                .toJson()
                .toString();

        setUp(StatusCodes.LOOP_DETECTED, entity);
        final TestRouteResult result = testRoute.run(HttpRequest.GET(PATH));
        assertResult(result, StatusCodes.LOOP_DETECTED, entity);
    }

    private void assertResult(final TestRouteResult result, final StatusCode expectedStatusCode, final String
            expectedBody) {
        result.assertStatusCode(expectedStatusCode);
        result.assertEntity(expectedBody);
    }

}
