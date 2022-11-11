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
package org.eclipse.ditto.gateway.service.endpoints.routes;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.eclipse.ditto.gateway.service.endpoints.EndpointTestConstants.KNOWN_DOMAIN;
import static org.eclipse.ditto.gateway.service.endpoints.EndpointTestConstants.UNKNOWN_PATH;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.commands.CommandHeaderInvalidException;
import org.eclipse.ditto.edge.service.headers.DittoHeadersValidator;
import org.eclipse.ditto.gateway.api.GatewayDuplicateHeaderException;
import org.eclipse.ditto.gateway.service.endpoints.EndpointTestBase;
import org.eclipse.ditto.gateway.service.endpoints.EndpointTestConstants;
import org.eclipse.ditto.gateway.service.endpoints.directives.auth.DevopsAuthenticationDirectiveFactory;
import org.eclipse.ditto.gateway.service.endpoints.directives.auth.DittoGatewayAuthenticationDirectiveFactory;
import org.eclipse.ditto.gateway.service.endpoints.directives.auth.GatewayAuthenticationDirectiveFactory;
import org.eclipse.ditto.gateway.service.endpoints.routes.cloudevents.CloudEventsRoute;
import org.eclipse.ditto.gateway.service.endpoints.routes.connections.ConnectionsRoute;
import org.eclipse.ditto.gateway.service.endpoints.routes.devops.DevOpsRoute;
import org.eclipse.ditto.gateway.service.endpoints.routes.health.CachingHealthRoute;
import org.eclipse.ditto.gateway.service.endpoints.routes.policies.OAuthTokenIntegrationSubjectIdFactory;
import org.eclipse.ditto.gateway.service.endpoints.routes.policies.PoliciesRoute;
import org.eclipse.ditto.gateway.service.endpoints.routes.sse.ThingsSseRouteBuilder;
import org.eclipse.ditto.gateway.service.endpoints.routes.stats.StatsRoute;
import org.eclipse.ditto.gateway.service.endpoints.routes.status.OverallStatusRoute;
import org.eclipse.ditto.gateway.service.endpoints.routes.things.ThingsParameter;
import org.eclipse.ditto.gateway.service.endpoints.routes.things.ThingsRoute;
import org.eclipse.ditto.gateway.service.endpoints.routes.thingsearch.ThingSearchRoute;
import org.eclipse.ditto.gateway.service.endpoints.routes.websocket.WebSocketRoute;
import org.eclipse.ditto.gateway.service.endpoints.routes.whoami.WhoamiRoute;
import org.eclipse.ditto.gateway.service.health.DittoStatusAndHealthProviderFactory;
import org.eclipse.ditto.gateway.service.security.HttpHeader;
import org.eclipse.ditto.gateway.service.security.authentication.jwt.JwtAuthenticationFactory;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;
import org.eclipse.ditto.internal.utils.health.cluster.ClusterStatus;
import org.eclipse.ditto.internal.utils.health.routes.StatusRoute;
import org.eclipse.ditto.internal.utils.http.HttpClientFacade;
import org.eclipse.ditto.internal.utils.protocol.ProtocolAdapterProvider;
import org.eclipse.ditto.internal.utils.tracing.DittoTracingInitResource;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.things.model.ThingIdInvalidException;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import com.typesafe.config.ConfigFactory;

import akka.actor.ActorSystem;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.model.headers.Location;
import akka.http.javadsl.model.headers.RawHeader;
import akka.http.javadsl.testkit.TestRoute;
import akka.http.javadsl.testkit.TestRouteResult;
import akka.stream.SystemMaterializer;

/**
 * Tests {@link RootRoute}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class RootRouteTest extends EndpointTestBase {

    @ClassRule
    public static final DittoTracingInitResource DITTO_TRACING_INIT_RESOURCE =
            DittoTracingInitResource.disableDittoTracing();

    private static final String ROOT_PATH = "/";
    private static final ActorSystem ACTOR_SYSTEM =
            ActorSystem.create(UUID.randomUUID().toString(), ConfigFactory.load("test"));
    private static final String STATUS_SUB_PATH = "status";
    private static final String HEALTH_SUB_PATH = "health";
    private static final String CLUSTER_SUB_PATH = "cluster";
    private static final String OVERALL_STATUS_PATH =
            ROOT_PATH + OverallStatusRoute.PATH_OVERALL + "/" + STATUS_SUB_PATH;
    private static final String HEALTH_PATH = ROOT_PATH + CachingHealthRoute.PATH_HEALTH;
    private static final String STATUS_CLUSTER_PATH = ROOT_PATH + STATUS_SUB_PATH + "/" + CLUSTER_SUB_PATH;
    private static final String STATUS_HEALTH_PATH = ROOT_PATH + STATUS_SUB_PATH + "/" + HEALTH_SUB_PATH;
    private static final String THINGS_2_PATH = ROOT_PATH + RootRoute.HTTP_PATH_API_PREFIX + "/" +
            JsonSchemaVersion.V_2.toInt() + "/" + ThingsRoute.PATH_THINGS;
    private static final String THING_SEARCH_2_PATH = ROOT_PATH + RootRoute.HTTP_PATH_API_PREFIX + "/" +
            JsonSchemaVersion.V_2.toInt() + "/" + ThingSearchRoute.PATH_SEARCH + "/" + ThingSearchRoute.PATH_THINGS;
    private static final String CONNECTIONS_2_PATH = ROOT_PATH + RootRoute.HTTP_PATH_API_PREFIX + "/" +
            JsonSchemaVersion.V_2.toInt() + "/" + ConnectionsRoute.PATH_CONNECTIONS;
    private static final String WHOAMI_PATH = ROOT_PATH + RootRoute.HTTP_PATH_API_PREFIX + "/" +
            JsonSchemaVersion.V_2.toInt() + "/" + WhoamiRoute.PATH_WHOAMI;
    private static final String UNKNOWN_SEARCH_PATH =
            ROOT_PATH + RootRoute.HTTP_PATH_API_PREFIX + "/" + JsonSchemaVersion.V_2.toInt() + "/" +
                    ThingSearchRoute.PATH_SEARCH + "/foo";
    private static final String THINGS_2_PATH_WITH_IDS =
            THINGS_2_PATH + "?" + ThingsParameter.IDS + "=namespace:bumlux";
    private static final String WS_2_PATH = ROOT_PATH + RootRoute.WS_PATH_PREFIX + "/" + JsonSchemaVersion.V_2.toInt();

    private static final String HTTPS = "https";

    private TestRoute rootTestRoute;

    @Mock
    private HttpClientFacade httpClientFacade;

    @Before
    public void setUp() {
        Mockito.when(httpClientFacade.getActorSystem()).thenReturn(routeBaseProperties.getActorSystem());
        final var jwtAuthenticationFactory = JwtAuthenticationFactory.newInstance(authConfig.getOAuthConfig(),
                cacheConfig,
                httpClientFacade,
                ACTOR_SYSTEM);
        final GatewayAuthenticationDirectiveFactory authenticationDirectiveFactory =
                new DittoGatewayAuthenticationDirectiveFactory(routeBaseProperties.getActorSystem(),
                        ConfigFactory.empty());

        final Supplier<ClusterStatus> clusterStatusSupplier = createClusterStatusSupplierMock();
        final var statusAndHealthProvider = DittoStatusAndHealthProviderFactory.of(routeBaseProperties.getActorSystem(),
                clusterStatusSupplier,
                healthCheckConfig);
        final var devopsAuthenticationDirectiveFactory =
                DevopsAuthenticationDirectiveFactory.newInstance(jwtAuthenticationFactory,
                        authConfig.getDevOpsConfig());
        final var devOpsAuthenticationDirective = devopsAuthenticationDirectiveFactory.devops();
        final var dittoExtensionConfig =
                ScopedConfig.dittoExtension(routeBaseProperties.getActorSystem().settings().config());
        final var rootRoute = RootRoute.getBuilder(httpConfig)
                .statsRoute(new StatsRoute(routeBaseProperties, devOpsAuthenticationDirective))
                .statusRoute(new StatusRoute(clusterStatusSupplier,
                        createHealthCheckingActorMock(),
                        routeBaseProperties.getActorSystem()))
                .connectionsRoute(new ConnectionsRoute(routeBaseProperties, devOpsAuthenticationDirective))
                .overallStatusRoute(new OverallStatusRoute(clusterStatusSupplier,
                        statusAndHealthProvider,
                        devopsAuthenticationDirectiveFactory.status()))
                .cachingHealthRoute(new CachingHealthRoute(statusAndHealthProvider, publicHealthConfig))
                .devopsRoute(new DevOpsRoute(routeBaseProperties, devOpsAuthenticationDirective))
                .policiesRoute(new PoliciesRoute(routeBaseProperties,
                        OAuthTokenIntegrationSubjectIdFactory.of(authConfig.getOAuthConfig())))
                .sseThingsRoute(ThingsSseRouteBuilder.getInstance(routeBaseProperties.getActorSystem(),
                        routeBaseProperties.getProxyActor(),
                        streamingConfig,
                        routeBaseProperties.getProxyActor()))
                .thingsRoute(new ThingsRoute(routeBaseProperties, messageConfig, claimMessageConfig))
                .thingSearchRoute(new ThingSearchRoute(routeBaseProperties))
                .whoamiRoute(new WhoamiRoute(routeBaseProperties))
                .cloudEventsRoute(new CloudEventsRoute(routeBaseProperties, cloudEventsConfig))
                .websocketRoute(WebSocketRoute.getInstance(routeBaseProperties.getActorSystem(),
                        routeBaseProperties.getProxyActor(),
                        streamingConfig,
                        SystemMaterializer.get(system()).materializer()))
                .supportedSchemaVersions(httpConfig.getSupportedSchemaVersions())
                .protocolAdapterProvider(ProtocolAdapterProvider.load(protocolConfig,
                        routeBaseProperties.getActorSystem()))
                .headerTranslator(httpHeaderTranslator)
                .httpAuthenticationDirective(
                        authenticationDirectiveFactory.buildHttpAuthentication(jwtAuthenticationFactory))
                .wsAuthenticationDirective(
                        authenticationDirectiveFactory.buildWsAuthentication(jwtAuthenticationFactory))
                .dittoHeadersValidator(
                        DittoHeadersValidator.get(routeBaseProperties.getActorSystem(), dittoExtensionConfig))
                .customApiRoutesProvider(
                        CustomApiRoutesProvider.get(routeBaseProperties.getActorSystem(), dittoExtensionConfig),
                        routeBaseProperties)
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
    public void getStatusWithStatusAuth() {
        final TestRouteResult result =
                rootTestRoute.run(withHttps(withStatusCredentials(HttpRequest.GET(OVERALL_STATUS_PATH))));

        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
    }

    @Test
    public void getStatusWithDevopsAuth() {
        final TestRouteResult result =
                rootTestRoute.run(withHttps(withDevopsCredentials(HttpRequest.GET(OVERALL_STATUS_PATH))));

        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
    }
    @Test
    public void getConnectionsWithDevopsAuth() {
        final TestRouteResult result =
                rootTestRoute.run(withHttps(withDevopsCredentials(HttpRequest.GET(CONNECTIONS_2_PATH))));

        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
    }

    @Test
    public void getConnectionsWithPreAuthenticated() {
        final TestRouteResult result =
                rootTestRoute.run(withHttps(withPreAuthenticatedAuthentication(HttpRequest.GET(CONNECTIONS_2_PATH))));

        result.assertStatusCode(StatusCodes.UNAUTHORIZED);
    }

    @Test
    public void getConnectionsUnAuthenticated() {
        final TestRouteResult result =
                rootTestRoute.run(withHttps(HttpRequest.GET(CONNECTIONS_2_PATH)));

        result.assertStatusCode(StatusCodes.UNAUTHORIZED);
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
                rootTestRoute.run(withHttps(withPreAuthenticatedAuthentication(HttpRequest.GET(THINGS_2_PATH))));

        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
    }

    @Test
    public void getThingsUrlWithIds() {
        final TestRouteResult result =
                rootTestRoute.run(
                        withHttps(withPreAuthenticatedAuthentication(HttpRequest.GET(THINGS_2_PATH_WITH_IDS))));

        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
    }

    @Test
    public void getThings1UrlWithoutHttps() {
        final TestRouteResult result = rootTestRoute.run(HttpRequest.GET(THINGS_2_PATH));

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
    public void getThingWithResponseRequiredFalse() {
        final HttpRequest request = withHttps(withPreAuthenticatedAuthentication(
                HttpRequest.GET(THINGS_2_PATH + "/org.eclipse.ditto%3Adummy")
        )).addHeader(akka.http.javadsl.model.HttpHeader.parse("response-required", "false"));
        final TestRouteResult result = rootTestRoute.run(request);
        result.assertStatusCode(StatusCodes.BAD_REQUEST);

        final String headerKey = DittoHeaderDefinition.RESPONSE_REQUIRED.getKey();
        final CommandHeaderInvalidException expectedException =
                CommandHeaderInvalidException.newBuilder(headerKey)
                        .message(MessageFormat.format(
                                "Query commands must not have the header ''{0}'' set to 'false'", headerKey)
                        )
                        .description(MessageFormat.format(
                                "Set the header ''{0}'' to 'true' instead in order to receive a response to your " +
                                        "query command.", headerKey))
                        .build();
        final JsonObject expectedResponsePayload = expectedException.toJson();
        result.assertEntity(expectedResponsePayload.toString());
    }

    @Test
    public void getThingSearchUrl() {
        final HttpRequest request = withHttps(withPreAuthenticatedAuthentication(HttpRequest.GET(THING_SEARCH_2_PATH)));

        final TestRouteResult result = rootTestRoute.run(request);

        result.assertStatusCode(EndpointTestConstants.DUMMY_COMMAND_SUCCESS);
    }

    @Test
    public void getWhoamiUrl() {
        final HttpRequest request = withHttps(withPreAuthenticatedAuthentication(HttpRequest.GET(WHOAMI_PATH)));

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
    public void getExceptionForDuplicateHeaderFields() {
        final HttpRequest httpRequest = HttpRequest.GET(THINGS_2_PATH_WITH_IDS)
                .addHeader(RawHeader.create("x-correlation-id", UUID.randomUUID().toString()))
                .addHeader(RawHeader.create("x-correlation-id", UUID.randomUUID().toString()));

        final TestRouteResult result = rootTestRoute.run(withHttps(withPreAuthenticatedAuthentication(httpRequest)));

        result.assertStatusCode(StatusCodes.BAD_REQUEST);
    }

    @Test
    public void getExceptionForDuplicationHeaderAndQueryParameter() {
        final String headerKey = DittoHeaderDefinition.TIMEOUT.getKey();
        HttpRequest httpRequest = HttpRequest.GET(THINGS_2_PATH_WITH_IDS + "&" + headerKey + "=32s");
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
        final String hugeSubjects = IntStream.range(0, 101)
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

    /**
     * Make sure header is RFC 7230 conform
     */
    @Test
    public void getExceptionDueToInvalidHeaderKey() {
        assertThatExceptionOfType(akka.http.scaladsl.model.IllegalHeaderException.class).isThrownBy(() -> {
                    akka.http.javadsl.model.HttpHeader.parse("(),/:;<=>?@[\\]{}", "lol");
                }
        );
    }

    @Test
    public void getExceptionDueToInvalidHeaderValue() {
        assertThatExceptionOfType(akka.http.scaladsl.model.IllegalHeaderException.class).isThrownBy(() -> {
                    akka.http.javadsl.model.HttpHeader.parse("x-correlation-id", "\n");
                }
        );
    }

    private static HttpRequest withHttps(final HttpRequest httpRequest) {
        return httpRequest.withUri(httpRequest.getUri().scheme("https"));
    }

    private static HttpRequest withPreAuthenticatedAuthentication(final HttpRequest httpRequest, final String subject) {
        return httpRequest.addHeader(RawHeader.create(HttpHeader.X_DITTO_PRE_AUTH.getName(), subject));

    }

    private static HttpRequest withPreAuthenticatedAuthentication(final HttpRequest httpRequest) {
        return withPreAuthenticatedAuthentication(httpRequest, "some-issuer:foo");
    }

}
