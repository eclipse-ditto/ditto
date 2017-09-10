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
package org.eclipse.ditto.services.gateway.endpoints.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Collections;

import org.junit.Test;

import akka.http.javadsl.model.HttpRequest;
import akka.http.scaladsl.model.HttpHeader;
import akka.http.scaladsl.model.HttpMethod;
import akka.http.scaladsl.model.HttpMethods;
import akka.http.scaladsl.model.HttpProtocols;
import akka.http.scaladsl.model.RequestEntity;
import akka.http.scaladsl.model.Uri;
import scala.collection.JavaConverters;
import scala.collection.immutable.Seq;

/**
 * Unit test for {@link TraceUtils}.
 */
public final class TraceUtilsTest {

    private static final int KNOWN_STATUS_CODE = 222;
    private static final HttpMethod KNOWN_REQUEST_METHOD_SCALA = HttpMethods.GET();
    private static final String KNOWN_REQUEST_METHOD = KNOWN_REQUEST_METHOD_SCALA.value();
    public static final String REQUEST_PARAMS_AND_FRAGMENT = "?foo=bar#fragnent";

    @Test
    public void assertImmutability() {
        assertInstancesOf(TraceUtils.class, areImmutable());
    }

    @Test
    public void testTraceNameWithExactPath() {
        final String requestPath = "/status/health";

        assertTraceName(requestPath, createExpectedTraceName(requestPath));
    }

    @Test
    public void testTraceNameWithExactPathIgnoresParamsAndFragment() {
        final String requestPath = "/status/health";
        final String requestUri = requestPath + REQUEST_PARAMS_AND_FRAGMENT;

        assertTraceName(requestUri, createExpectedTraceName(requestPath));
    }

    @Test
    public void testTraceNameWithShortenedPath() {
        final String requestUri = "/api/2/things/myns:myid/attributes";
        final String expectedTraceUri = "/api/2/things/x";
        assertTraceName(requestUri, createExpectedTraceName(expectedTraceUri));
    }

    @Test
    public void testTraceNameWithShortenedPathIgnoresParamsAndFragment() {
        final String requestPath = "/api/2/things/myns:myid/attributes";
        final String requestUri = requestPath + REQUEST_PARAMS_AND_FRAGMENT;
        final String expectedTraceUri = "/api/2/things/x";

        assertTraceName(requestUri, createExpectedTraceName(expectedTraceUri));
    }

    @Test
    public void testTraceNameWithFallbackPath() {
        final String requestUri = "/status/health1";
        assertTraceName(requestUri, createExpectedTraceName(TraceUriGenerator.FALLBACK_PATH));
    }

    @Test
    public void testTraceNameWithFallbackPathIgnoresParamsAndFragment() {
        final String requestPath = "/status/health1";
        final String requestUri = requestPath + REQUEST_PARAMS_AND_FRAGMENT;

        assertTraceName(requestUri, createExpectedTraceName(TraceUriGenerator.FALLBACK_PATH));
    }

    private static String createExpectedTraceName(final String requestUri) {
        return "roundtrip.http" + requestUri + "." + KNOWN_REQUEST_METHOD + "." + KNOWN_STATUS_CODE;
    }

    private static void assertTraceName(final String requestUri, final String expectedTraceName) {
        final HttpRequest request = createRequest(requestUri);
        assertThat(TraceUtils.determineTraceName(request, KNOWN_STATUS_CODE))
                .isEqualTo(expectedTraceName);
    }

    private static HttpRequest createRequest(final String requestUri) {
        final Seq<HttpHeader> emptyHeaders =
                JavaConverters.<HttpHeader>asScalaBufferConverter(Collections.emptyList()).asScala().toList();
        return new akka.http.scaladsl.model.HttpRequest(KNOWN_REQUEST_METHOD_SCALA, Uri
                .apply(requestUri), emptyHeaders,
                mock(RequestEntity.class),
                // in Scala: HttpProtocols.`HTTP/1.1`, the $u002E is the dot.
                HttpProtocols.HTTP$div1$u002E1());
    }

}
