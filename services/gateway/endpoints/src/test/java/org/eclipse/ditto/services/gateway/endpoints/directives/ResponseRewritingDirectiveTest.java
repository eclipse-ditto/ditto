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
import static org.eclipse.ditto.services.gateway.endpoints.directives.ResponseRewritingDirective.UNAVAILABLE_ROUT_RESULT;
import static org.eclipse.ditto.services.gateway.endpoints.directives.ResponseRewritingDirective.rewriteResponse;

import org.eclipse.ditto.services.gateway.endpoints.EndpointTestBase;
import org.junit.Test;

import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCode;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.Route;
import akka.http.javadsl.testkit.TestRoute;
import akka.http.javadsl.testkit.TestRouteResult;

/**
 * Tests {@link ResponseRewritingDirective}.
 */
public class ResponseRewritingDirectiveTest extends EndpointTestBase {

    private static final String PATH = "/";

    private TestRoute testRoute;

    private void setUp(final StatusCode statusCode) {
        final Route root = route(get(
                () -> complete(HttpResponse.create().withEntity(DEFAULT_DUMMY_ENTITY).withStatus(statusCode))));
        final Route wrappedRoute = rewriteResponse(KNOWN_CORRELATION_ID, () -> root);
        testRoute = testRoute(wrappedRoute);
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
                entityToString(UNAVAILABLE_ROUT_RESULT.getResponse().entity()));
    }

    @Test
    public void status501IsConvertedTo500() {
        setUp(StatusCodes.NOT_IMPLEMENTED);
        final TestRouteResult result = testRoute.run(HttpRequest.GET(PATH));
        assertResult(result, StatusCodes.INTERNAL_SERVER_ERROR,
                entityToString(INTERNAL_SERVER_ERROR_RESULT.getResponse().entity()));
    }

    @Test
    public void status503IsNotConverted() {
        setUp(StatusCodes.SERVICE_UNAVAILABLE);
        final TestRouteResult result = testRoute.run(HttpRequest.GET(PATH));
        assertResult(result, StatusCodes.SERVICE_UNAVAILABLE, DEFAULT_DUMMY_ENTITY);
    }

    private void assertResult(final TestRouteResult result, final StatusCode expectedStatusCode, final String
            expectedBody) {
        result.assertStatusCode(expectedStatusCode);
        result.assertEntity(expectedBody);
    }

}
