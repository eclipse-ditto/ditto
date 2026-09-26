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
package org.eclipse.ditto.gateway.service.endpoints.routes.timeseries;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.StatusCodes;
import org.apache.pekko.http.javadsl.server.Route;
import org.apache.pekko.http.javadsl.testkit.TestRoute;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.gateway.service.endpoints.EndpointTestBase;
import org.eclipse.ditto.gateway.service.security.authorization.NamespaceAccessValidatorFactory;
import org.eclipse.ditto.gateway.service.util.config.security.DefaultNamespaceAccessConfig;
import org.eclipse.ditto.gateway.service.util.config.security.NamespaceAccessConfig;

import com.typesafe.config.ConfigFactory;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests the cross-Thing aggregation route {@code GET /timeseries/things}.
 * <p>
 * The focus is the HTTP edge: which requests are accepted, which are rejected before a command is
 * ever built, and that an accepted request produces a {@code retrieveAggregatedTimeseries} command
 * carrying the parsed query. The semantic rules themselves live on
 * {@code CrossThingTimeseriesQuery} and are covered by its own tests; here we only check that the
 * route surfaces them as {@code 400}s rather than {@code 500}s.
 */
public final class TimeseriesRouteCrossThingTest extends EndpointTestBase {

    private static final String BASE = "/timeseries/things";
    private static final String NAMESPACE = "io.beyonnex.smartheating";
    private static final String PATH = "/features/circuit/properties/flowTemperature";

    private static final Function<Jsonifiable<?>, Optional<Object>> ECHO_RESPONSE_PROVIDER =
            m -> DummyThingModifyCommandResponse.echo((Jsonifiable<JsonValue>) () -> {
                if (m instanceof WithDittoHeaders) {
                    return JsonObject.newBuilder().set("payload", m.toJson()).build();
                }
                return m.toJson();
            });

    private TestRoute underTest;

    @Override
    protected Function<Jsonifiable<?>, Optional<Object>> getResponseProvider() {
        return ECHO_RESPONSE_PROVIDER;
    }

    @Before
    public void setUp() {
        // Wire the namespace access validator exactly as GatewayRootActor does. Constructing the
        // route without it (the one-arg overload passes null) would disable namespace access
        // control for every case below, so the endpoint's first authorization layer would go
        // unexercised while the tests still passed.
        underTest = testRouteWithNamespaceAccess(List.of(NAMESPACE));
    }

    private TestRoute testRouteWithNamespaceAccess(final List<String> allowedNamespaces) {
        final NamespaceAccessValidatorFactory validatorFactory =
                new NamespaceAccessValidatorFactory(List.of(namespaceAccessConfig(allowedNamespaces)));
        final TimeseriesRoute timeseriesRoute = new TimeseriesRoute(routeBaseProperties, validatorFactory);
        final Route route =
                extractRequestContext(ctx -> timeseriesRoute.buildTimeseriesRoute(ctx, dittoHeaders));
        return testRoute(handleExceptions(() -> route));
    }

    private static NamespaceAccessConfig namespaceAccessConfig(final List<String> allowedNamespaces) {
        return DefaultNamespaceAccessConfig.of(ConfigFactory.parseString(String.format(
                "{ conditions = [], allowed-namespaces = [%s], blocked-namespaces = [] }",
                allowedNamespaces.stream()
                        .map(item -> "\"" + item + "\"")
                        .collect(Collectors.joining(", ")))));
    }

    private static String url(final String query) {
        return BASE + "?" + query;
    }

    private static String validQuery() {
        return "namespaces=" + NAMESPACE + "&paths=" + PATH
                + "&from=2026-07-01T00:00:00Z&to=2026-07-02T00:00:00Z&step=1h&agg=avg";
    }

    @Test
    public void validRequestBuildsAggregatedCommand() {
        final var result = underTest.run(HttpRequest.GET(url(validQuery())));

        result.assertStatusCode(StatusCodes.OK);
        result.assertEntity(JsonObject.newBuilder()
                .set("payload", JsonObject.newBuilder()
                        .set("type", "timeseries.commands:retrieveAggregatedTimeseries")
                        .set("query", JsonObject.newBuilder()
                                .set("namespace", NAMESPACE)
                                .set("paths", JsonArray.newBuilder()
                                        .add(PATH)
                                        .build())
                                .set("from", "2026-07-01T00:00:00Z")
                                .set("to", "2026-07-02T00:00:00Z")
                                .set("step", "PT1H")
                                .set("aggregation", "avg")
                                .build())
                        .build())
                .build()
                .toString());
    }

    @Test
    public void groupByAndRqlFilterAreParsed() {
        final var result = underTest.run(HttpRequest.GET(
                url(validQuery() + "&groupBy=attributes/building,thingId"
                        + "&filter=" + urlEncode("eq(attributes/building,'A')"))));

        result.assertStatusCode(StatusCodes.OK);
        final String entity = result.entityString();
        assertThat(entity).contains("\"groupBy\"");
        // A tag dimension is named by its Thing path; the wire form keeps the tag: discriminator.
        assertThat(entity).contains("attributes/building");
        assertThat(entity).contains("thingId");
        assertThat(entity).contains("\"filter\"");
    }

    private static String urlEncode(final String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    @Test
    public void relativeTimeExpressionsAreAccepted() {
        final var result = underTest.run(HttpRequest.GET(url(
                "namespaces=" + NAMESPACE + "&paths=" + PATH
                        + "&from=now-24h&to=now&step=1h&agg=avg")));

        result.assertStatusCode(StatusCodes.OK);
    }

    @Test
    public void missingNamespacesIsBadRequest() {
        final var result = underTest.run(HttpRequest.GET(url(
                "paths=" + PATH + "&from=2026-07-01T00:00:00Z&to=2026-07-02T00:00:00Z"
                        + "&step=1h&agg=avg")));

        result.assertStatusCode(StatusCodes.BAD_REQUEST);
    }

    @Test
    public void missingStepIsBadRequest() {
        // step is required here (unlike the single-Thing endpoints) because a cross-Thing raw read
        // would be unbounded.
        final var result = underTest.run(HttpRequest.GET(url(
                "namespaces=" + NAMESPACE + "&paths=" + PATH
                        + "&from=2026-07-01T00:00:00Z&to=2026-07-02T00:00:00Z&agg=avg")));

        result.assertStatusCode(StatusCodes.BAD_REQUEST);
    }

    @Test
    public void missingAggIsBadRequest() {
        final var result = underTest.run(HttpRequest.GET(url(
                "namespaces=" + NAMESPACE + "&paths=" + PATH
                        + "&from=2026-07-01T00:00:00Z&to=2026-07-02T00:00:00Z&step=1h")));

        result.assertStatusCode(StatusCodes.BAD_REQUEST);
    }

    @Test
    public void multipleNamespacesAreRejected() {
        // Silently reading only the first namespace would be worse than refusing.
        final var result = underTest.run(HttpRequest.GET(url(
                "namespaces=" + NAMESPACE + ",other.namespace&paths=" + PATH
                        + "&from=2026-07-01T00:00:00Z&to=2026-07-02T00:00:00Z&step=1h&agg=avg")));

        result.assertStatusCode(StatusCodes.BAD_REQUEST);
    }

    @Test
    public void windowFunctionAggregationIsRejected() {
        final var result = underTest.run(HttpRequest.GET(url(
                "namespaces=" + NAMESPACE + "&paths=" + PATH
                        + "&from=2026-07-01T00:00:00Z&to=2026-07-02T00:00:00Z&step=1h&agg=derivative")));

        result.assertStatusCode(StatusCodes.BAD_REQUEST);
    }

    @Test
    /**
     * A non-reserved groupBy value is a tag dimension named by its Thing path, so it is accepted at the
     * route and authorized in the service (which requires READ on the field). What used to be an
     * "unknown dimension" 400 is therefore no longer one; blank tokens are skipped rather than
     * rejected, so a groupBy of only separators simply means "no grouping".
     */
    public void blankGroupByTokensAreIgnored() {
        final var result = underTest.run(HttpRequest.GET(url(validQuery() + "&groupBy=,")));

        result.assertStatusCode(StatusCodes.OK);
        assertThat(result.entityString()).doesNotContain("\"groupBy\"");
    }

    @Test
    public void maxGroupsAboveCeilingIsRejected() {
        final var result = underTest.run(HttpRequest.GET(url(validQuery() + "&maxGroups=99999")));

        result.assertStatusCode(StatusCodes.BAD_REQUEST);
    }

    @Test
    public void unparseableFromIsRejected() {
        final var result = underTest.run(HttpRequest.GET(url(
                "namespaces=" + NAMESPACE + "&paths=" + PATH
                        + "&from=yesterday&to=now&step=1h&agg=avg")));

        result.assertStatusCode(StatusCodes.BAD_REQUEST);
    }

    /**
     * The collection route must not shadow the existing single-Thing route, which lives one segment
     * deeper and requires {@code paths} but no {@code step}/{@code agg}.
     */
    @Test
    public void crossThingRejectsNamespaceOutsideTheAccessAllowList() {
        // The caller-supplied `namespaces` parameter is enumerating, so it is guarded the same way
        // ThingSearchRoute guards its own. Checked at the route, before any command is dispatched.
        final TestRoute restricted = testRouteWithNamespaceAccess(List.of("org.eclipse.allowed"));

        final var result = restricted.run(HttpRequest.GET(url(validQuery())));

        result.assertStatusCode(StatusCodes.FORBIDDEN);
        assertThat(result.entityString()).contains("gateway:namespace.notaccessible");
    }

    @Test
    public void crossThingPassesThroughNamespaceInsideTheAccessAllowList() {
        final TestRoute permitted = testRouteWithNamespaceAccess(List.of(NAMESPACE));

        final var result = permitted.run(HttpRequest.GET(url(validQuery())));

        result.assertStatusCode(StatusCodes.OK);
        assertThat(result.entityString())
                .contains("timeseries.commands:retrieveAggregatedTimeseries");
    }

    @Test
    public void singleThingRouteStillResolves() {
        final var result = underTest.run(HttpRequest.GET(
                "/timeseries/things/" + NAMESPACE + ":heatsource-1?paths=" + PATH
                        + "&from=2026-07-01T00:00:00Z&to=2026-07-02T00:00:00Z"));

        result.assertStatusCode(StatusCodes.OK);
        assertThat(result.entityString())
                .contains("timeseries.commands:retrieveTimeseries");
    }
}
