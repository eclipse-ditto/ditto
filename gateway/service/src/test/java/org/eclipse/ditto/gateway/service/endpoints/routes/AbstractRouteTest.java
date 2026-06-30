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
package org.eclipse.ditto.gateway.service.endpoints.routes;

import static org.eclipse.ditto.gateway.service.endpoints.routes.AbstractRoute.rawPathPrefixSegment;

import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.StatusCodes;
import org.apache.pekko.http.javadsl.server.Directives;
import org.apache.pekko.http.javadsl.testkit.JUnitRouteTest;
import org.apache.pekko.http.javadsl.testkit.TestRoute;
import org.junit.Test;

/**
 * Unit test for {@link AbstractRoute#rawPathPrefixSegment(String, java.util.function.Supplier)}.
 * <p>
 * The contract is strict, full-segment matching (not prefix matching): {@code /<segment>} is only matched when the
 * next path segment equals the constant <em>in its entirety</em>, and a non-match rejects so that sibling routes
 * still get a chance to match the unconsumed path.
 */
public final class AbstractRouteTest extends JUnitRouteTest {

    private static final String SEGMENT = "logging";

    @Test
    public void exactSegmentMatchesAndDelegatesToInner() {
        final TestRoute underTest = testRoute(
                rawPathPrefixSegment(SEGMENT, () -> Directives.complete("matched")));

        underTest.run(HttpRequest.GET("/logging"))
                .assertStatusCode(StatusCodes.OK)
                .assertEntity("matched");
    }

    @Test
    public void exactSegmentWithTrailingSlashStillMatches() {
        final TestRoute underTest = testRoute(
                rawPathPrefixSegment(SEGMENT, () -> Directives.pathEndOrSingleSlash(
                        () -> Directives.complete("matched"))));

        underTest.run(HttpRequest.GET("/logging/"))
                .assertStatusCode(StatusCodes.OK)
                .assertEntity("matched");
    }

    @Test
    public void segmentWithTrailingSuffixIsRejected() {
        // "loggingh" must NOT be matched as a prefix of "logging"; the request must be rejected (404) instead of
        // being silently routed to the "logging" handler with the trailing characters dangling.
        final TestRoute underTest = testRoute(
                rawPathPrefixSegment(SEGMENT, () -> Directives.complete("matched")));

        underTest.run(HttpRequest.GET("/loggingh"))
                .assertStatusCode(StatusCodes.NOT_FOUND);
    }

    @Test
    public void rejectionBacktracksSoSiblingRouteCanMatch() {
        // A non-matching segment must leave the path untouched so a sibling route whose constant is a *superstring*
        // of the first still matches. With prefix matching the first route would wrongly swallow "/loggingh".
        final TestRoute underTest = testRoute(Directives.concat(
                rawPathPrefixSegment("logging", () -> Directives.complete("logging-route")),
                rawPathPrefixSegment("loggingh", () -> Directives.complete("loggingh-route"))));

        underTest.run(HttpRequest.GET("/logging"))
                .assertStatusCode(StatusCodes.OK)
                .assertEntity("logging-route");
        underTest.run(HttpRequest.GET("/loggingh"))
                .assertStatusCode(StatusCodes.OK)
                .assertEntity("loggingh-route");
    }

    @Test
    public void innerReceivesTheRemainingPath() {
        final TestRoute underTest = testRoute(
                rawPathPrefixSegment("things", () ->
                        rawPathPrefixSegment("foo", () -> Directives.complete("nested"))));

        // exact nested match
        underTest.run(HttpRequest.GET("/things/foo"))
                .assertStatusCode(StatusCodes.OK)
                .assertEntity("nested");
        // a typo in the outer segment must not leak into the nested matcher
        underTest.run(HttpRequest.GET("/thingsX/foo"))
                .assertStatusCode(StatusCodes.NOT_FOUND);
        // a typo in the inner segment must be rejected as well
        underTest.run(HttpRequest.GET("/things/foobar"))
                .assertStatusCode(StatusCodes.NOT_FOUND);
    }

    @Test
    public void innerReceivesExactUnmatchedRemainderWithDeeperPath() {
        // Exactly one segment plus its leading slash must be consumed; everything after the matched segment is
        // handed to the inner verbatim. This is the contract the production consumers rely on (policy "resources"
        // -> extractUnmatchedPath, "subjects" -> remaining()).
        final TestRoute underTest = testRoute(
                rawPathPrefixSegment("resources", () ->
                        Directives.extractUnmatchedPath(Directives::complete)));

        underTest.run(HttpRequest.GET("/resources/a/b"))
                .assertStatusCode(StatusCodes.OK)
                .assertEntity("/a/b");
    }

    @Test
    public void innerReceivesEmptyRemainderWhenSegmentIsWholePath() {
        final TestRoute underTest = testRoute(
                rawPathPrefixSegment("resources", () ->
                        Directives.extractUnmatchedPath(Directives::complete)));

        underTest.run(HttpRequest.GET("/resources"))
                .assertStatusCode(StatusCodes.OK)
                .assertEntity("");
    }

    @Test
    public void innerReceivesSingleSlashRemainderForTrailingSlash() {
        final TestRoute underTest = testRoute(
                rawPathPrefixSegment("resources", () ->
                        Directives.extractUnmatchedPath(Directives::complete)));

        underTest.run(HttpRequest.GET("/resources/"))
                .assertStatusCode(StatusCodes.OK)
                .assertEntity("/");
    }

    @Test
    public void segmentMatchIsCaseSensitive() {
        // The match uses String.equals, so a differently-cased segment must not match.
        final TestRoute underTest = testRoute(
                rawPathPrefixSegment(SEGMENT, () -> Directives.complete("matched")));

        underTest.run(HttpRequest.GET("/LOGGING"))
                .assertStatusCode(StatusCodes.NOT_FOUND);
    }

    @Test
    public void rootPathDoesNotMatchSegment() {
        // The root path carries no segment to capture, so the matcher must reject.
        final TestRoute underTest = testRoute(
                rawPathPrefixSegment(SEGMENT, () -> Directives.complete("matched")));

        underTest.run(HttpRequest.GET("/"))
                .assertStatusCode(StatusCodes.NOT_FOUND);
    }

    @Test
    public void shorterRequestThanConstantIsRejected() {
        // Symmetric to segmentWithTrailingSuffixIsRejected: a request shorter than the constant must not match.
        final TestRoute underTest = testRoute(
                rawPathPrefixSegment(SEGMENT, () -> Directives.complete("matched")));

        underTest.run(HttpRequest.GET("/log"))
                .assertStatusCode(StatusCodes.NOT_FOUND);
    }

    @Test
    public void encodedSegmentIsDecodedBeforeComparison() {
        // Characterization test: PathMatchers.segment() yields the decoded segment, so "%67" (== 'g') decodes to
        // "logging" and matches. This documents the (decode-then-compare) behavior of the new matcher.
        final TestRoute underTest = testRoute(
                rawPathPrefixSegment(SEGMENT, () -> Directives.complete("matched")));

        underTest.run(HttpRequest.GET("/loggin%67"))
                .assertStatusCode(StatusCodes.OK)
                .assertEntity("matched");
    }

}
