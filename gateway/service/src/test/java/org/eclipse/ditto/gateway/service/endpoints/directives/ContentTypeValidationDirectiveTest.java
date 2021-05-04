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
package org.eclipse.ditto.gateway.service.endpoints.directives;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.eclipse.ditto.gateway.service.endpoints.directives.ContentTypeValidationDirective.ensureValidContentType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.exceptions.UnsupportedMediaTypeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.junit.Test;

import akka.http.javadsl.model.ContentType;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.MediaTypes;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.RequestContext;
import akka.http.javadsl.server.Route;
import akka.http.javadsl.testkit.JUnitRouteTest;
import akka.http.javadsl.testkit.TestRouteResult;

public final class ContentTypeValidationDirectiveTest extends JUnitRouteTest {

    private final Supplier<Route> COMPLETE_OK = () -> complete(StatusCodes.OK);

    @Test
    public void testValidContentType() {
        // Arrange
        final DittoHeaders dittoHeaders = DittoHeaders.empty();
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
        final DittoHeaders dittoHeaders = DittoHeaders.empty();
        final String type = MediaTypes.APPLICATION_JSON.toString();
        final ContentType differentType = ContentTypes.APPLICATION_X_WWW_FORM_URLENCODED;

        final RequestContext mockedCtx = mock(RequestContext.class);
        when(mockedCtx.getRequest())
                .thenReturn(HttpRequest.PUT("someUrl").withEntity(differentType,"something".getBytes()));
        // Act
        final UnsupportedMediaTypeException result =
                catchThrowableOfType(() -> ensureValidContentType(Set.of(type), mockedCtx, dittoHeaders, COMPLETE_OK),
                        UnsupportedMediaTypeException.class);

        // Assert
        assertThat(result).hasMessageContaining(differentType.toString());
        assertThat(result.getHttpStatus()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    public void testWithContentTypeWithoutCharset() {
        // Arrange
        final DittoHeaders dittoHeaders = DittoHeaders.empty();
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
        final DittoHeaders dittoHeaders = DittoHeaders.empty();
        final String type = ContentTypes.APPLICATION_JSON.mediaType().toString();

        final RequestContext mockedCtx = mock(RequestContext.class);
        when(mockedCtx.getRequest()).thenReturn(HttpRequest.PUT("someUrl"));

        // Act
        final UnsupportedMediaTypeException result =
                catchThrowableOfType(() -> ensureValidContentType(Set.of(type), mockedCtx, dittoHeaders, COMPLETE_OK),
                        UnsupportedMediaTypeException.class);

        // Assert
        assertThat(result.getHttpStatus()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    public void testExceptionContainsDittoHeaders() {
        // Arrange
        final DittoHeaders dittoHeaders = DittoHeaders.of(Map.of("someHeaderKey", "someHeaderVal"));
        final String type = ContentTypes.APPLICATION_JSON.mediaType().toString();

        final RequestContext mockedCtx = mock(RequestContext.class);
        when(mockedCtx.getRequest()).thenReturn(HttpRequest.PUT("someUrl"));

        // Act
        final UnsupportedMediaTypeException result =
                catchThrowableOfType(() -> ensureValidContentType(Set.of(type), mockedCtx, dittoHeaders, COMPLETE_OK),
                        UnsupportedMediaTypeException.class);

        // Assert
        assertThat(result.getDittoHeaders()).isEqualTo(dittoHeaders);
    }

    /**
     * When akka can't parse the content-type header, it appears as rawHeader and application/octet-streams is used
     * as default, this behaviour is simulated here.
     */
    @Test
    public void testWithNonParsableContentType() {
        // Arrange
        final DittoHeaders dittoHeaders = DittoHeaders.empty();
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

    @Test
    public void testWithUpperCaseContentTypeHeaderName() {
        // Arrange
        final DittoHeaders dittoHeaders = DittoHeaders.empty();
        final String type = MediaTypes.APPLICATION_JSON.toString();

        // Act
        final TestRouteResult result =
                testRoute(extractRequestContext(
                        ctx -> ensureValidContentType(Set.of(type), ctx, dittoHeaders, COMPLETE_OK)))
                        .run(HttpRequest.PUT("someUrl")
                                .addHeader(HttpHeader.parse("CONTENT-TYPE", type))
                                .withEntity(ContentTypes.APPLICATION_OCTET_STREAM, "something".getBytes()));

        // Assert
        result.assertStatusCode(StatusCodes.OK);
    }

}
