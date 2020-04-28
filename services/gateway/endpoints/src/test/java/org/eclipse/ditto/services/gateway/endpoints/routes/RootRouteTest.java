/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.gateway.endpoints.routes;

import static org.eclipse.ditto.services.gateway.endpoints.EndpointTestConstants.KNOWN_DOMAIN;
import static org.eclipse.ditto.services.gateway.endpoints.EndpointTestConstants.UNKNOWN_PATH;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.eclipse.ditto.model.base.headers.DittoHeaderDefinition;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.DittoHeadersSizeChecker;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.things.ThingIdInvalidException;
import org.eclipse.ditto.protocoladapter.HeaderTranslator;
import org.eclipse.ditto.services.gateway.endpoints.EndpointTestBase;
import org.eclipse.ditto.services.gateway.endpoints.EndpointTestConstants;
import org.eclipse.ditto.services.gateway.endpoints.directives.HttpsEnsuringDirective;
import org.eclipse.ditto.services.gateway.endpoints.directives.auth.DittoGatewayAuthenticationDirectiveFactory;
import org.eclipse.ditto.services.gateway.endpoints.directives.auth.GatewayAuthenticationDirectiveFactory;
import org.eclipse.ditto.services.gateway.endpoints.routes.devops.DevOpsRoute;
import org.eclipse.ditto.services.gateway.endpoints.routes.health.CachingHealthRoute;
import org.eclipse.ditto.services.gateway.endpoints.routes.policies.PoliciesRoute;
import org.eclipse.ditto.services.gateway.endpoints.routes.sse.ThingsSseRouteBuilder;
import org.eclipse.ditto.services.gateway.endpoints.routes.stats.StatsRoute;
import org.eclipse.ditto.services.gateway.endpoints.routes.status.OverallStatusRoute;
import org.eclipse.ditto.services.gateway.endpoints.routes.things.ThingsParameter;
import org.eclipse.ditto.services.gateway.endpoints.routes.things.ThingsRoute;
import org.eclipse.ditto.services.gateway.endpoints.routes.thingsearch.ThingSearchRoute;
import org.eclipse.ditto.services.gateway.endpoints.routes.websocket.WebSocketRoute;
import org.eclipse.ditto.services.gateway.health.DittoStatusAndHealthProviderFactory;
import org.eclipse.ditto.services.gateway.health.StatusAndHealthProvider;
import org.eclipse.ditto.services.gateway.security.HttpHeader;
import org.eclipse.ditto.services.gateway.security.authentication.jwt.JwtAuthenticationFactory;
import org.eclipse.ditto.services.gateway.security.utils.HttpClientFacade;
import org.eclipse.ditto.services.gateway.util.config.security.DevOpsConfig;
import org.eclipse.ditto.services.utils.health.cluster.ClusterStatus;
import org.eclipse.ditto.services.utils.health.routes.StatusRoute;
import org.eclipse.ditto.services.utils.protocol.ProtocolAdapterProvider;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayDuplicateHeaderException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.typesafe.config.Config;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.model.headers.Location;
import akka.http.javadsl.model.headers.RawHeader;
import akka.http.javadsl.server.Route;
import akka.http.javadsl.testkit.TestRoute;
import akka.http.javadsl.testkit.TestRouteResult;

/**
 * Tests {@link RootRoute}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class RootRouteTest extends EndpointTestBase {

    private static final String ROOT_PATH = "/";
    private static final String STATUS_SUB_PATH = "status";
    private static final String HEALTH_SUB_PATH = "health";
    private static final String CLUSTER_SUB_PATH = "cluster";
    private static final String OVERALL_STATUS_PATH =
            ROOT_PATH + OverallStatusRoute.PATH_OVERALL + "/" + STATUS_SUB_PATH;
    private static final String HEALTH_PATH = ROOT_PATH + CachingHealthRoute.PATH_HEALTH;
    private static final String STATUS_CLUSTER_PATH = ROOT_PATH + STATUS_SUB_PATH + "/" + CLUSTER_SUB_PATH;
    private static final String STATUS_HEALTH_PATH = ROOT_PATH + STATUS_SUB_PATH + "/" + HEALTH_SUB_PATH;
    private static final String THINGS_1_PATH = ROOT_PATH + RootRoute.HTTP_PATH_API_PREFIX + "/" +
            JsonSchemaVersion.V_1.toInt() + "/" + ThingsRoute.PATH_THINGS;
    private static final String THINGS_2_PATH = ROOT_PATH + RootRoute.HTTP_PATH_API_PREFIX + "/" +
            JsonSchemaVersion.V_2.toInt() + "/" + ThingsRoute.PATH_THINGS;
    private static final String THING_SEARCH_2_PATH = ROOT_PATH + RootRoute.HTTP_PATH_API_PREFIX + "/" +
            JsonSchemaVersion.V_2.toInt() + "/" + ThingSearchRoute.PATH_SEARCH + "/" + ThingSearchRoute.PATH_THINGS;
    private static final String UNKNOWN_SEARCH_PATH =
            ROOT_PATH + RootRoute.HTTP_PATH_API_PREFIX + "/" + JsonSchemaVersion.V_2.toInt() + "/" +
                    ThingSearchRoute.PATH_SEARCH + "/foo";
    private static final String THINGS_1_PATH_WITH_IDS =
            THINGS_1_PATH + "?" + ThingsParameter.IDS + "=namespace:bumlux";
    private static final String WS_2_PATH = ROOT_PATH + RootRoute.WS_PATH_PREFIX + "/" + JsonSchemaVersion.V_2.toInt();

    private static final String PATH_WITH_INVALID_ENCODING = ROOT_PATH + RootRoute.HTTP_PATH_API_PREFIX + "/" +
            JsonSchemaVersion.V_1.toInt() + "/" + ThingsRoute.PATH_THINGS +
            "/:bumlux/features?fields=feature-1%2properties";

    private static final String HTTPS = "https";

    private final Executor messageDispatcher;

    private TestRoute rootTestRoute;

    @Mock
    private HttpClientFacade httpClientFacade;

    public RootRouteTest() {
        messageDispatcher = Executors.newFixedThreadPool(8);
    }

    @Before
    public void setUp() {
        final ActorSystem actorSystem = system();
        final Config config = actorSystem.settings().config();
        final ProtocolAdapterProvider protocolAdapterProvider =
                ProtocolAdapterProvider.load(protocolConfig, actorSystem);
        final HeaderTranslator headerTranslator = protocolAdapterProvider.getHttpHeaderTranslator();
        final JwtAuthenticationFactory jwtAuthenticationFactory =
                JwtAuthenticationFactory.newInstance(authConfig.getOAuthConfig(), cacheConfig, httpClientFacade);
        final GatewayAuthenticationDirectiveFactory authenticationDirectiveFactory =
                new DittoGatewayAuthenticationDirectiveFactory(authConfig, jwtAuthenticationFactory, messageDispatcher);

        final ActorRef proxyActor = createDummyResponseActor();

        final Supplier<ClusterStatus> clusterStatusSupplier = createClusterStatusSupplierMock();
        final StatusAndHealthProvider statusAndHealthProvider =
                DittoStatusAndHealthProviderFactory.of(actorSystem, clusterStatusSupplier, healthCheckConfig);
        final DevOpsConfig devOpsConfig = authConfig.getDevOpsConfig();

        final Route rootRoute = RootRoute.getBuilder(httpConfig)
                .statsRoute(new StatsRoute(proxyActor, actorSystem, httpConfig, commandConfig, devOpsConfig,
                        headerTranslator))
                .statusRoute(new StatusRoute(clusterStatusSupplier, createHealthCheckingActorMock(), actorSystem))
                .overallStatusRoute(
                        new OverallStatusRoute(clusterStatusSupplier, statusAndHealthProvider, devOpsConfig))
                .cachingHealthRoute(new CachingHealthRoute(statusAndHealthProvider, publicHealthConfig))
                .devopsRoute(new DevOpsRoute(proxyActor, actorSystem, httpConfig, commandConfig, devOpsConfig,
                        headerTranslator))
                .policiesRoute(new PoliciesRoute(proxyActor, actorSystem, httpConfig, commandConfig, headerTranslator))
                .sseThingsRoute(ThingsSseRouteBuilder.getInstance(proxyActor, streamingConfig, proxyActor))
                .thingsRoute(new ThingsRoute(proxyActor, actorSystem, httpConfig, commandConfig, messageConfig,
                        claimMessageConfig, headerTranslator))
                .thingSearchRoute(
                        new ThingSearchRoute(proxyActor, actorSystem, httpConfig, commandConfig, headerTranslator))
                .websocketRoute(WebSocketRoute.getInstance(proxyActor, streamingConfig))
                .supportedSchemaVersions(httpConfig.getSupportedSchemaVersions())
                .protocolAdapterProvider(protocolAdapterProvider)
                .headerTranslator(headerTranslator)
                .httpAuthenticationDirective(authenticationDirectiveFactory.buildHttpAuthentication())
                .wsAuthenticationDirective(authenticationDirectiveFactory.buildWsAuthentication())
                .dittoHeadersSizeChecker(DittoHeadersSizeChecker.of(4096, 20))
                .build();

        rootTestRoute = testRoute(rootRoute);
    }

    @Test
    public void getRoot() {
        final TestRouteResult result =
                rootTestRoute.run(withHttps(withPreAuthenticatedAuthentication(HttpRequest.GET(ROOT_PATH))));

        result.assertStatusCode(StatusCodes.NOT_FOUND);
    }

    @Test
    public void getHealthWithoutAuthReturnsOK() {
        final TestRouteResult result = rootTestRoute.run(withHttps(HttpRequest.GET(HEALTH_PATH)));

        result.assertStatusCode(StatusCodes.OK);
    }

    @Test
    public void getStatusWithAuth() {
        final TestRouteResult result =
                rootTestRoute.run(withHttps(withDevopsCredentials(HttpRequest.GET(OVERALL_STATUS_PATH))));

        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
    }

    @Test
    public void getStatusWithoutAuth() {
        final TestRouteResult result = rootTestRoute.run(withHttps(HttpRequest.GET(OVERALL_STATUS_PATH)));

        result.assertStatusCode(StatusCodes.UNAUTHORIZED);
    }

    @Test
    public void getStatusUrlWithoutHttps() {
        final TestRouteResult result = rootTestRoute.run(HttpRequest.GET(OVERALL_STATUS_PATH));

        result.assertStatusCode(StatusCodes.NOT_FOUND);
    }

    @Test
    public void getStatusHealth() {
        // If the endpoint /status/health should be secured do it via webserver for example
        final TestRouteResult result = rootTestRoute.run(withHttps(HttpRequest.GET(STATUS_HEALTH_PATH)));

        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
    }

    @Test
    public void getStatusHealthWithoutHttps() {
        final TestRouteResult result = rootTestRoute.run(HttpRequest.GET(STATUS_HEALTH_PATH));

        result.assertStatusCode(StatusCodes.NOT_FOUND);
    }

    @Test
    public void getStatusCluster() {
        // If the endpoint /status/cluster should be secured do it via webserver for example
        final TestRouteResult result = rootTestRoute.run(withHttps(HttpRequest.GET(STATUS_CLUSTER_PATH)));

        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
    }

    @Test
    public void getStatusClusterWithoutHttps() {
        final TestRouteResult result = rootTestRoute.run(HttpRequest.GET(STATUS_CLUSTER_PATH));

        result.assertStatusCode(StatusCodes.NOT_FOUND);
    }

    @Test
    public void getThingWithVeryLongId() {
        final int numberOfUUIDs = 100;
        final StringBuilder pathBuilder = new StringBuilder(THINGS_2_PATH).append("/");
        final StringBuilder idBuilder = new StringBuilder("namespace");
        for (int i = 0; i < numberOfUUIDs; ++i) {
            idBuilder.append(':').append(UUID.randomUUID());
        }
        pathBuilder.append(idBuilder);
        final ThingIdInvalidException expectedEx = ThingIdInvalidException.newBuilder(idBuilder.toString())
                .dittoHeaders(DittoHeaders.empty())
                .build();

        final TestRouteResult result =
                rootTestRoute.run(
                        withHttps(withPreAuthenticatedAuthentication(HttpRequest.GET(pathBuilder.toString()))));

        result.assertEntity(expectedEx.toJsonString());
        result.assertStatusCode(StatusCodes.BAD_REQUEST);
    }

    @Test
    public void getThingsUrlWithoutIds() {
        final TestRouteResult result =
                rootTestRoute.run(withHttps(withPreAuthenticatedAuthentication(HttpRequest.GET(THINGS_1_PATH))));

        result.assertStatusCode(StatusCodes.BAD_REQUEST);
    }

    @Test
    public void getThingsUrlWithIds() {
        final TestRouteResult result =
                rootTestRoute.run(
                        withHttps(withPreAuthenticatedAuthentication(HttpRequest.GET(THINGS_1_PATH_WITH_IDS))));

        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
    }

    @Test
    public void getThings1UrlWithoutHttps() {
        final TestRouteResult result = rootTestRoute.run(HttpRequest.GET(THINGS_1_PATH));

        result.assertStatusCode(StatusCodes.NOT_FOUND);
    }

    @Test
    public void getThings2UrlWithoutHttps() {
        final TestRouteResult result = rootTestRoute.run(HttpRequest.GET(THINGS_2_PATH));

        result.assertStatusCode(StatusCodes.NOT_FOUND);
    }

    @Test
    public void getThingsUrlWithIdsWithWrongVersionNumber() {
        final String thingsUrlWithIdsWithWrongVersionNumber = ROOT_PATH + RootRoute.HTTP_PATH_API_PREFIX + "/" +
                "nan" + "/" + ThingsRoute.PATH_THINGS + "?" +
                ThingsParameter.IDS + "=bumlux";

        final TestRouteResult result = rootTestRoute.run(
                withHttps(withPreAuthenticatedAuthentication(HttpRequest.GET(thingsUrlWithIdsWithWrongVersionNumber))));

        result.assertStatusCode(StatusCodes.NOT_FOUND);
    }

    @Test
    public void getThingsUrlWithIdsWithNonExistingVersionNumber() {
        final int nonExistingVersion = 9999;
        final String thingsUrlWithIdsWithNonExistingVersionNumber = ROOT_PATH + RootRoute.HTTP_PATH_API_PREFIX + "/" +
                nonExistingVersion + "/" + ThingsRoute.PATH_THINGS + "?" +
                ThingsParameter.IDS + "=bumlux";

        final TestRouteResult result = rootTestRoute.run(
                withHttps(withPreAuthenticatedAuthentication(
                        HttpRequest.GET(thingsUrlWithIdsWithNonExistingVersionNumber))));

        result.assertStatusCode(StatusCodes.NOT_FOUND);
    }

    @Test
    public void getThingSearchUrl() {
        final HttpRequest request = withHttps(withPreAuthenticatedAuthentication(HttpRequest.GET(THING_SEARCH_2_PATH)));

        final TestRouteResult result = rootTestRoute.run(request);

        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
    }

    @Test
    public void getNonExistingSearchUrl() {
        final HttpRequest request = withHttps(withPreAuthenticatedAuthentication(HttpRequest.GET(UNKNOWN_SEARCH_PATH)));

        final TestRouteResult result = rootTestRoute.run(request);

        result.assertStatusCode(StatusCodes.NOT_FOUND);
    }

    @Test
    public void getWsUrlWithoutUpgrade() {
        final TestRouteResult result =
                rootTestRoute.run(withHttps(withPreAuthenticatedAuthentication(HttpRequest.GET(WS_2_PATH))));
        assertWebsocketUpgradeExpectedResult(result);
    }

    @Test
    public void getWsUrlWithoutHttps() {
        final TestRouteResult result = rootTestRoute.run(HttpRequest.GET(WS_2_PATH));
        result.assertStatusCode(StatusCodes.NOT_FOUND);
    }

    @Test
    public void getNonExistingToplevelUrl() {
        final TestRouteResult result = rootTestRoute.run(withHttps(HttpRequest.GET(UNKNOWN_PATH)));
        result.assertStatusCode(StatusCodes.NOT_FOUND);
    }

    @Test
    public void getNonExistingToplevelUrlWithoutHttps() {
        final TestRouteResult result = rootTestRoute.run(HttpRequest.GET(UNKNOWN_PATH));
        result.assertStatusCode(StatusCodes.MOVED_PERMANENTLY);
        result.assertHeaderExists(Location.create(HTTPS + "://" + KNOWN_DOMAIN + UNKNOWN_PATH));
    }

    @Test
    public void getWithInvalidEncoding() {
        final TestRouteResult result = rootTestRoute.run(HttpRequest.GET(PATH_WITH_INVALID_ENCODING));
        result.assertStatusCode(StatusCodes.BAD_REQUEST);
    }

    @Test
    public void getExceptionForDuplicateHeaderFields() {
        final HttpRequest httpRequest = HttpRequest.GET(THINGS_1_PATH_WITH_IDS)
                .addHeader(RawHeader.create("x-correlation-id", UUID.randomUUID().toString()))
                .addHeader(RawHeader.create("x-correlation-id", UUID.randomUUID().toString()));

        final TestRouteResult result = rootTestRoute.run(withHttps(withPreAuthenticatedAuthentication(httpRequest)));

        result.assertStatusCode(StatusCodes.BAD_REQUEST);
    }

    @Test
    public void getExceptionForDuplicationHeaderAndQueryParameter() {
        final String headerKey = DittoHeaderDefinition.TIMEOUT.getKey();
        HttpRequest httpRequest = HttpRequest.GET(THINGS_1_PATH_WITH_IDS + "&" + headerKey + "=32s");
        httpRequest = httpRequest.addHeader(akka.http.javadsl.model.HttpHeader.parse(headerKey, "23s"));
        final GatewayDuplicateHeaderException expectedException = GatewayDuplicateHeaderException.newBuilder()
                .message(() -> MessageFormat.format(
                        "<{0}> was provided as header as well as query parameter with divergent values!", headerKey))
                .build();

        final TestRouteResult result = rootTestRoute.run(withHttps(withPreAuthenticatedAuthentication(httpRequest)));

        result.assertStatusCode(StatusCodes.BAD_REQUEST);
        result.assertEntity(expectedException.toJsonString());
    }

    @Test
    public void getExceptionDueToTooManyAuthSubjects() {
        final String hugeSubjects = IntStream.range(0, 22)
                .mapToObj(i -> "i:foo" + i)
                .collect(Collectors.joining(","));
        final HttpRequest request =
                withPreAuthenticatedAuthentication(withHttps(HttpRequest.GET(THING_SEARCH_2_PATH)), hugeSubjects);
        final TestRouteResult result = rootTestRoute.run(request);

        result.assertStatusCode(StatusCodes.REQUEST_HEADER_FIELDS_TOO_LARGE);
    }

    @Test
    public void acceptMaximumNumberOfAuthSubjects() {
        final String hugeSubjects = IntStream.range(0, 10)
                .mapToObj(i -> "i:foo" + i)
                .collect(Collectors.joining(","));
        final HttpRequest request =
                withPreAuthenticatedAuthentication(withHttps(HttpRequest.GET(THING_SEARCH_2_PATH)), hugeSubjects);
        final TestRouteResult result = rootTestRoute.run(request);

        result.assertStatusCode(StatusCodes.OK);
    }

    @Test
    public void getExceptionDueToLargeHeaders() {
        final char[] chars = new char[8192];
        Arrays.fill(chars, 'x');
        final String largeString = new String(chars);

        final HttpRequest request =
                withPreAuthenticatedAuthentication(withHttps(HttpRequest.GET(THING_SEARCH_2_PATH)
                        .withHeaders(Collections.singleton(
                                akka.http.javadsl.model.HttpHeader.parse("x-correlation-id", largeString)))));
        final TestRouteResult result = rootTestRoute.run(request);

        result.assertStatusCode(StatusCodes.REQUEST_HEADER_FIELDS_TOO_LARGE);
    }

    private static HttpRequest withHttps(final HttpRequest httpRequest) {
        return httpRequest.addHeader(RawHeader.create(HttpsEnsuringDirective.X_FORWARDED_PROTO_LBAAS, HTTPS));
    }

    private static HttpRequest withPreAuthenticatedAuthentication(final HttpRequest httpRequest, final String subject) {
        return httpRequest.addHeader(RawHeader.create(HttpHeader.X_DITTO_PRE_AUTH.getName(), subject));

    }

    private static HttpRequest withPreAuthenticatedAuthentication(final HttpRequest httpRequest) {
        return withPreAuthenticatedAuthentication(httpRequest, "some-issuer:foo");
    }

}
