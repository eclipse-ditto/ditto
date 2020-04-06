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
package org.eclipse.ditto.services.gateway.endpoints.directives;

import static org.eclipse.ditto.services.gateway.endpoints.directives.ContentTypeValidationDirective.ensureContentTypeAndExtractDataBytes;
import static org.eclipse.ditto.services.gateway.endpoints.directives.ContentTypeValidationDirective.ensureValidContentType;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.junit.Test;

import akka.actor.ActorSystem;
import akka.http.javadsl.model.ContentType;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.Route;
import akka.http.javadsl.testkit.JUnitRouteTest;
import akka.http.javadsl.testkit.TestRouteResult;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import akka.stream.javadsl.Source;
import akka.util.ByteString;

public class ContentTypeValidationDirectiveTest extends JUnitRouteTest {

    private final Supplier<Route> COMPLETE_OK = () -> complete(StatusCodes.OK);

    private final Materializer MAT = ActorMaterializer.create(ActorSystem.create("QuickStart"));
    private final String PAYLOAD_STRING = "something";
    private final Function<Source<ByteString, Object>, Route> OK_WHEN_PAYLOAD_MATCHES = payload -> {
        final String payloadAsString = payload
                .map(ByteString::utf8String)
                .runReduce(String::concat, MAT)
                .toCompletableFuture()
                .join();

        return payloadAsString.equals(PAYLOAD_STRING) ? complete(StatusCodes.OK) :
                complete(StatusCodes.BAD_REQUEST);
    };

    @Test
    public void testValidContentType() {
        // Arrange
        DittoHeaders dittoHeaders = DittoHeaders.empty();
        final ContentType type = ContentTypes.APPLICATION_JSON;

        // Act
        final TestRouteResult result =
                testRoute(extractRequestContext(
                        ctx -> ensureValidContentType(List.of(type), ctx, dittoHeaders, COMPLETE_OK)))
                        .run(HttpRequest.PUT("someUrl").withEntity(type, "something".getBytes()));

        // Assert
        result.assertStatusCode(StatusCodes.OK);
    }

    @Test
    public void testNonValidContentType() {
        // Arrange
        DittoHeaders dittoHeaders = DittoHeaders.empty();
        final ContentType type = ContentTypes.APPLICATION_JSON;
        final ContentType differentType = ContentTypes.APPLICATION_X_WWW_FORM_URLENCODED;

        // Act
        final TestRouteResult result =
                testRoute(extractRequestContext(
                        ctx -> ensureValidContentType(List.of(type), ctx, dittoHeaders, COMPLETE_OK)))
                        .run(HttpRequest.PUT("someUrl").withEntity(differentType, "something".getBytes()));

        // Assert
        result.assertStatusCode(StatusCodes.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    public void testWithoutEntityNoNPEExpected() {
        // Arrange
        DittoHeaders dittoHeaders = DittoHeaders.empty();
        final ContentType type = ContentTypes.APPLICATION_JSON;

        // Act
        final TestRouteResult result =
                testRoute(extractRequestContext(
                        ctx -> ensureValidContentType(List.of(type), ctx, dittoHeaders, COMPLETE_OK)))
                        .run(HttpRequest.PUT("someUrl"));

        // Assert
        result.assertStatusCode(StatusCodes.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    public void testComposedDirective() {
        // Arrange
        DittoHeaders dittoHeaders = DittoHeaders.empty();
        final ContentType type = ContentTypes.APPLICATION_JSON;

        // Act
        final TestRouteResult result =
                testRoute(extractRequestContext(
                        ctx -> ensureContentTypeAndExtractDataBytes(List.of(type), ctx, dittoHeaders,
                                OK_WHEN_PAYLOAD_MATCHES)))
                        .run(HttpRequest.PUT("someUrl").withEntity(type, PAYLOAD_STRING.getBytes()));

        // Assert
        result.assertStatusCode(StatusCodes.OK);
    }


}