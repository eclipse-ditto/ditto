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
package org.eclipse.ditto.gateway.service.endpoints.directives;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.gateway.service.endpoints.directives.DotSegmentRejectingDirective.containsDotSegment;
import static org.eclipse.ditto.gateway.service.endpoints.directives.DotSegmentRejectingDirective.rejectDotSegments;

import java.util.List;

import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.StatusCodes;
import org.apache.pekko.http.javadsl.model.headers.RawRequestURI;
import org.apache.pekko.http.javadsl.testkit.JUnitRouteTest;
import org.apache.pekko.http.javadsl.testkit.TestRoute;
import org.junit.Test;

/**
 * Tests {@link DotSegmentRejectingDirective}.
 */
public final class DotSegmentRejectingDirectiveTest extends JUnitRouteTest {

    /**
     * The path from the vulnerability report: nginx resolves it to {@code /api/2/connections}, the gateway to
     * {@code /api/2/things/victim:thing1/}.
     */
    private static final String CONNECTIONS_CONFUSION_PATH =
            "/api/2/things/victim:thing1/x%2f..%2f..%2f..%2fconnections%2fy/..";

    @Test
    public void detectsDotSegments() {
        final List<String> withDotSegments = List.of(
                CONNECTIONS_CONFUSION_PATH,
                "/api/2/things/victim:thing1/x%2f..%2f..%2f..%2f..%2f..%2fdevops%2fy/..",
                "/api/2/things/victim:thing1/x%2F..%2F..%2F..%2Fconnections%2Fy/..",
                "/api/2/things/a/../b",
                "/api/2/things/a/./b",
                "/api/2/things/a/..",
                "/api/2/things/a/.",
                "/api/2/things/a/../",
                "/..",
                "/.",
                "/api/2/things/a/%2e%2e/b",
                "/api/2/things/a/%2E%2E/b",
                "/api/2/things/a/.%2e/b",
                "/api/2/things/a/%2e./b",
                "/api/2/things/a/%2e/b",
                "/api/2/things/a/..?x=y",
                "/api/2/things/a/..#fragment",
                "http://localhost:8080/api/2/things/a/../b",
                "https://user@ditto.example.com:443/api/2/things/a/%2e%2e",
                // dot segments only delimited by encoded slashes: nginx resolves them, the gateway does not
                "/api/2/things/V/attributes/a%2f..%2f..%2f..%2f..%2fconnections",
                "/api/2/things/V/inbox/messages/x%2f..%2f..%2f..%2f..%2f..%2fconnections",
                "/api/2/things/V/attributes/a%2F..%2Fb",
                "/api/2/things/V/attributes/a%2f%2e%2e%2fb",
                "/api/2/things/V/attributes/a%2f.",
                "/api/2/things/V/attributes/..%2fa",
                "/api/2/things/org.eclipse.ditto:thing-1/inbox/messages/a%2f..%2fb"
        );

        assertThat(withDotSegments).allSatisfy(uri -> assertThat(containsDotSegment(uri))
                .as("dot segment in <%s>", uri)
                .isTrue());
    }

    @Test
    public void acceptsPathsWithoutDotSegments() {
        final List<String> withoutDotSegments = List.of(
                "/",
                "",
                "/api/2/things",
                "/api/2/things/",
                "/api/2/things/org.eclipse.ditto:thing-1",
                "/api/2/things/org.eclipse.ditto:thing-1/attributes/a.b/c..d",
                "/api/2/things/org.eclipse.ditto:thing-1/features/...",
                "/api/2/things/org.eclipse.ditto:thing-1/features/.f/properties/..x",
                "/api/2/things/org.eclipse.ditto:thing-1/attributes/%2e%2e%2e",
                "/api/2/things/org.eclipse.ditto:thing-1/inbox/messages/a%2fb",
                "/api/2/things/org.eclipse.ditto:thing-1/attributes/a%2f...%2fb",
                "/api/2/things/org.eclipse.ditto:thing-1/attributes/a%252f..%252fb",
                "/api/2/things?ids=a,b&fields=thingId/..",
                "/api/2/things#/..",
                "/api/2/search/things?filter=eq(attributes/x,%22..%22)",
                "http://localhost:8080/api/2/things",
                "http://..:8080/api/2/things",
                "//api/2/things"
        );

        assertThat(withoutDotSegments).allSatisfy(uri -> assertThat(containsDotSegment(uri))
                .as("no dot segment in <%s>", uri)
                .isFalse());
    }

    @Test
    public void rejectsRequestWithDotSegmentsInRawRequestUri() {
        final TestRoute route = testRoute(rejectDotSegments(() -> complete(StatusCodes.OK)));

        route.run(HttpRequest.GET("/api/2/things/victim:thing1/")
                        .addHeader(RawRequestURI.create(CONNECTIONS_CONFUSION_PATH)))
                .assertStatusCode(StatusCodes.BAD_REQUEST)
                .assertEntity("URI '" + CONNECTIONS_CONFUSION_PATH +
                        "' contains dot segments ('.' or '..') in its path, which are not supported.");
    }

    @Test
    public void passesRequestWithoutDotSegmentsInRawRequestUri() {
        final TestRoute route = testRoute(rejectDotSegments(() -> complete(StatusCodes.OK)));

        route.run(HttpRequest.GET("/api/2/things/org.eclipse.ditto:thing-1")
                        .addHeader(RawRequestURI.create("/api/2/things/org.eclipse.ditto:thing-1?fields=thingId")))
                .assertStatusCode(StatusCodes.OK);
    }

    @Test
    public void fallsBackToRequestUriWithoutRawRequestUriHeader() {
        final TestRoute route = testRoute(rejectDotSegments(() -> complete(StatusCodes.OK)));

        route.run(HttpRequest.GET("/api/2/things/org.eclipse.ditto:thing-1"))
                .assertStatusCode(StatusCodes.OK);
    }

    @Test
    public void withoutRawRequestUriHeaderOnlyTheNormalizedRequestUriIsChecked() {
        // pekko removes dot segments when building the effective request URI, so without the Raw-Request-URI header
        // (pekko.http.server.raw-request-uri-header = off) the directive cannot detect them anymore
        final TestRoute route = testRoute(rejectDotSegments(() -> complete(StatusCodes.OK)));

        route.run(HttpRequest.GET("/api/2/things/org.eclipse.ditto:thing-1/x/.."))
                .assertStatusCode(StatusCodes.OK);
    }

}
