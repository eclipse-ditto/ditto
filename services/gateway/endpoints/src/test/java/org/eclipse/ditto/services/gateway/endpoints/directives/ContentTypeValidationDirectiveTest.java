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


import static org.eclipse.ditto.services.gateway.endpoints.directives.ContentTypeValidationDirective.ensureValidContentType;

import java.util.Set;
import java.util.function.Supplier;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.junit.Test;

import akka.http.javadsl.model.ContentType;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpEntities;
import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.HttpMessage;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.MediaTypes;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.Route;
import akka.http.javadsl.testkit.JUnitRouteTest;
import akka.http.javadsl.testkit.TestRouteResult;

public class ContentTypeValidationDirectiveTest extends JUnitRouteTest {

    private final Supplier<Route> COMPLETE_OK = () -> complete(StatusCodes.OK);

    @Test
    public void testValidContentType() {
        // Arrange
        DittoHeaders dittoHeaders = DittoHeaders.empty();
        final ContentType type = ContentTypes.APPLICATION_JSON;

        // Act
        final TestRouteResult result =
                testRoute(extractRequestContext(
                        ctx -> ensureValidContentType(Set.of(type.mediaType().toString()), ctx, dittoHeaders,
                                COMPLETE_OK)))
                        .run(HttpRequest.PUT("someUrl").withEntity(type, "something".getBytes()));

        // Assert
        result.assertStatusCode(StatusCodes.OK);
    }

    @Test
    public void testNonValidContentType() {
        // Arrange
        DittoHeaders dittoHeaders = DittoHeaders.empty();
        final String type = MediaTypes.APPLICATION_JSON.toString();
        final ContentType differentType = ContentTypes.APPLICATION_X_WWW_FORM_URLENCODED;

        // Act
        final TestRouteResult result =
                testRoute(extractRequestContext(
                        ctx -> ensureValidContentType(Set.of(type), ctx, dittoHeaders, COMPLETE_OK)))
                        .run(HttpRequest.PUT("someUrl").withEntity(differentType, "something".getBytes()));

        // Assert
        result.assertStatusCode(StatusCodes.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    public void testWithContentTypeWithoutCharset() {
        // Arrange
        DittoHeaders dittoHeaders = DittoHeaders.empty();
        final String type = MediaTypes.TEXT_PLAIN.toString();
        final ContentType typeMissingCharset = MediaTypes.TEXT_PLAIN.toContentTypeWithMissingCharset();

        // Act
        final TestRouteResult result =
                testRoute(extractRequestContext(
                        ctx -> ensureValidContentType(Set.of(type), ctx, dittoHeaders, COMPLETE_OK)))
                        .run(HttpRequest.PUT("someUrl").withEntity(typeMissingCharset, "something".getBytes()));

        // Assert
        result.assertStatusCode(StatusCodes.OK);
    }

    @Test
    public void testWithoutEntityNoNPEExpected() {
        // Arrange
        DittoHeaders dittoHeaders = DittoHeaders.empty();
        final String type = ContentTypes.APPLICATION_JSON.mediaType().toString();

        // Act
        final TestRouteResult result =
                testRoute(extractRequestContext(
                        ctx -> ensureValidContentType(Set.of(type), ctx, dittoHeaders, COMPLETE_OK)))
                        .run(HttpRequest.PUT("someUrl"));

        // Assert
        result.assertStatusCode(StatusCodes.UNSUPPORTED_MEDIA_TYPE);
    }

    /**
     * When akka can't parse the content-type header, it appears as rawHeader and application/octet-streams is used
     * as default, this behaviour is simulated here.
     */
    @Test
    public void testWithNonParsableContentType() {
        // Arrange
        DittoHeaders dittoHeaders = DittoHeaders.empty();
        final String nonParsableMediaType = "application-json";

        // Act
        final TestRouteResult result =
                testRoute(extractRequestContext(
                        ctx -> ensureValidContentType(Set.of(nonParsableMediaType), ctx, dittoHeaders, COMPLETE_OK)))
                        .run(HttpRequest.PUT("someUrl")
                                .addHeader(HttpHeader.parse("content-type", nonParsableMediaType))
                                .withEntity(ContentTypes.APPLICATION_OCTET_STREAM, "something".getBytes()));

        // Assert
        result.assertStatusCode(StatusCodes.OK);
    }

}