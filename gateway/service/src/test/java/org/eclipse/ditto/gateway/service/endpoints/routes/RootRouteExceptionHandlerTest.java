/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.gateway.service.endpoints.routes;

import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.concurrent.CompletionException;
import java.util.function.Function;
import java.util.function.Supplier;

import org.eclipse.ditto.base.model.exceptions.DittoJsonException;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.gateway.api.GatewayMethodNotAllowedException;
import org.eclipse.ditto.json.JsonMissingFieldException;
import org.eclipse.ditto.json.JsonRuntimeException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.MediaTypes;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.Directives;
import akka.http.javadsl.server.ExceptionHandler;
import akka.http.javadsl.server.RejectionHandler;
import akka.http.javadsl.server.Route;
import akka.http.javadsl.testkit.JUnitRouteTest;
import akka.http.javadsl.testkit.TestRoute;

/**
 * Unit test for {@link RootRouteExceptionHandler}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class RootRouteExceptionHandlerTest extends JUnitRouteTest {

    private static final String ROUTE_PATH = "foo";
    private static final HttpRequest HTTP_REQUEST = HttpRequest.GET("/" + ROUTE_PATH);

    @Rule
    public final TestName testName = new TestName();

    @Mock
    private Function<DittoRuntimeException, HttpResponse> dreToHttpResponse;

    private ExceptionHandler underTest;

    @Before
    public void setUp() {
        underTest = RootRouteExceptionHandler.getInstance(dreToHttpResponse);
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(RootRouteExceptionHandler.class,
                areImmutable(),
                provided(Function.class).isAlsoImmutable());
    }

    @Test
    public void tryToGetInstanceWithNullFunction() {
        assertThatNullPointerException()
                .isThrownBy(() -> RootRouteExceptionHandler.getInstance(null))
                .withMessage("The dittoRuntimeExceptionToHttpResponse must not be null!")
                .withNoCause();
    }

    @Test
    public void handleDittoRuntimeException() {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .correlationId(testName.getMethodName())
                .build();
        final GatewayMethodNotAllowedException exception = GatewayMethodNotAllowedException.newBuilder("GET")
                .dittoHeaders(dittoHeaders)
                .build();
        final TestRoute testRoute = getTestRoute(() -> {
            throw exception;
        });

        testRoute.run(HTTP_REQUEST);

        Mockito.verify(dreToHttpResponse).apply(Mockito.eq(exception));
    }

    @Test
    public void handleJsonRuntimeException() {
        final JsonRuntimeException jsonRuntimeException = JsonMissingFieldException.newBuilder()
                .fieldName("myField")
                .build();
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .correlationId(testName.getMethodName())
                .build();
        final DittoJsonException dittoJsonException = new DittoJsonException(jsonRuntimeException, dittoHeaders);
        final TestRoute testRoute = getTestRoute(() -> {
            throw jsonRuntimeException;
        });

        testRoute.run(HTTP_REQUEST);

        Mockito.verify(dreToHttpResponse).apply(Mockito.eq(dittoJsonException));
    }

    @Test
    public void handleCompletionExceptionWithUnhandledRuntimeExceptionAsCause() {
        final NumberFormatException numberFormatException = new NumberFormatException("42");
        final CompletionException completionException = new CompletionException(numberFormatException);
        final TestRoute testRoute = getTestRoute(() -> {
            throw completionException;
        });

        testRoute.run(HTTP_REQUEST)
                .assertStatusCode(StatusCodes.INTERNAL_SERVER_ERROR)
                .assertMediaType(MediaTypes.TEXT_PLAIN);
        Mockito.verifyNoInteractions(dreToHttpResponse);
    }

    @Test
    public void handleCompletionExceptionWithDittoRuntimeExceptionAsCause() {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .correlationId(testName.getMethodName())
                .build();
        final GatewayMethodNotAllowedException gatewayMethodNotAllowedException =
                GatewayMethodNotAllowedException.newBuilder("GET")
                        .dittoHeaders(dittoHeaders)
                        .build();
        final CompletionException completionException = new CompletionException(gatewayMethodNotAllowedException);
        final TestRoute testRoute = getTestRoute(() -> {
            throw completionException;
        });

        testRoute.run(HTTP_REQUEST);

        Mockito.verify(dreToHttpResponse).apply(Mockito.eq(gatewayMethodNotAllowedException));
    }

    @Test
    public void handleCompletionExceptionWithJsonRuntimeExceptionAsCause() {
        final JsonRuntimeException jsonRuntimeException = JsonMissingFieldException.newBuilder()
                .fieldName("myField")
                .build();
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .correlationId(testName.getMethodName())
                .build();
        final DittoJsonException dittoJsonException = new DittoJsonException(jsonRuntimeException, dittoHeaders);
        final CompletionException completionException = new CompletionException(jsonRuntimeException);
        final TestRoute testRoute = getTestRoute(() -> {
            throw completionException;
        });

        testRoute.run(HTTP_REQUEST);

        Mockito.verify(dreToHttpResponse).apply(Mockito.eq(dittoJsonException));
    }

    @Test
    public void handleUnknownExceptionAsInternalServerError() {
        final TestRoute testRoute = getTestRoute(() -> {
            throw new NumberFormatException("42");
        });

        testRoute.run(HTTP_REQUEST)
                .assertStatusCode(StatusCodes.INTERNAL_SERVER_ERROR)
                .assertMediaType(MediaTypes.TEXT_PLAIN);
        Mockito.verifyNoInteractions(dreToHttpResponse);
    }

    @Test
    public void handleUnknownErrorAsInternalServerError() {
        final TestRoute testRoute = getTestRoute(() -> {
            throw new AssertionError();
        });

        testRoute.run(HTTP_REQUEST)
                .assertStatusCode(StatusCodes.INTERNAL_SERVER_ERROR)
                .assertMediaType(MediaTypes.TEXT_PLAIN);
        Mockito.verifyNoInteractions(dreToHttpResponse);
    }

    private TestRoute getTestRoute(final Supplier<Route> innerRouteSupplier) {
        final Route dummyRoute = Directives.get(() -> Directives.path(ROUTE_PATH, innerRouteSupplier));
        return testRoute(dummyRoute.seal(RejectionHandler.defaultHandler(), underTest));
    }

}
