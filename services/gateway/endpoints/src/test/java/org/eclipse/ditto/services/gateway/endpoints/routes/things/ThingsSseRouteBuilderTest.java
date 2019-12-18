/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.gateway.endpoints.routes.things;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.assertj.core.util.Lists;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.model.base.auth.AuthorizationModelFactory;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.services.gateway.endpoints.EndpointTestBase;
import org.eclipse.ditto.services.gateway.endpoints.routes.sse.SseConnectionSupervisor;
import org.eclipse.ditto.services.gateway.streaming.CloseStreamExceptionally;
import org.eclipse.ditto.services.gateway.streaming.Connect;
import org.eclipse.ditto.services.gateway.streaming.StartStreaming;
import org.eclipse.ditto.services.models.concierge.streaming.StreamingType;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayServiceUnavailableException;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.MediaTypes;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.Route;
import akka.http.javadsl.testkit.TestRoute;
import akka.http.javadsl.testkit.TestRouteResult;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;

/**
 * Unit test for the route built with {@link ThingsSseRouteBuilderTest}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class ThingsSseRouteBuilderTest extends EndpointTestBase {

    private static final String THINGS_ROUTE = "/things";

    private static ActorSystem actorSystem;
    private static HttpHeader acceptHeader;

    @Rule
    public final TestName testName = new TestName();

    private final AtomicReference<ActorRef> publisherActorReference = new AtomicReference<>();

    private String connectionCorrelationId;
    private TestProbe streamingActor;
    private TestRoute underTest;

    @BeforeClass
    public static void setUpClass() {
        actorSystem = ActorSystem.create(ThingsSseRouteBuilderTest.class.getSimpleName());
        acceptHeader = HttpHeader.parse("Accept", "text/event-stream");
    }

    @AfterClass
    public static void tearDownClass() {
        TestKit.shutdownActorSystem(actorSystem);
    }

    @Before
    public void setUp() {
        streamingActor = TestProbe.apply(actorSystem);

        final SseConnectionSupervisor sseConnectionSupervisor =
                (sseConnectionActor, connectionCorrelationId, dittoHeaders) -> publisherActorReference.set(
                        sseConnectionActor);

        connectionCorrelationId = testName.getMethodName();

        final Supplier<CompletionStage<DittoHeaders>> dittoHeadersSupplier = () -> {
            final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                    .correlationId(connectionCorrelationId)
                    .build();
            return CompletableFuture.completedFuture(dittoHeaders);
        };

        final ThingsSseRouteBuilder sseRouteBuilder = ThingsSseRouteBuilder.getInstance(streamingActor.ref());
        sseRouteBuilder.withSseConnectionSupervisor(sseConnectionSupervisor);
        final Route sseRoute = extractRequestContext(ctx -> sseRouteBuilder.build(ctx, dittoHeadersSupplier));
        underTest = testRoute(sseRoute);
    }

    @Test
    public void getWithoutAcceptHeaderFails() {
        final TestRouteResult testResult = underTest.run(HttpRequest.GET(THINGS_ROUTE));

        testResult.assertStatusCode(StatusCodes.NOT_FOUND);
    }

    @Test
    public void putWithAcceptHeaderFails() {
        final TestRouteResult testResult = underTest.run(HttpRequest.PUT(THINGS_ROUTE).addHeader(acceptHeader));

        testResult.assertStatusCode(StatusCodes.METHOD_NOT_ALLOWED);
    }

    @Test
    public void postWithAcceptHeaderFails() {
        final TestRouteResult testResult = underTest.run(HttpRequest.POST(THINGS_ROUTE).addHeader(acceptHeader));

        testResult.assertStatusCode(StatusCodes.METHOD_NOT_ALLOWED);
    }

    @Test
    public void deleteWithAcceptHeaderFails() {
        final TestRouteResult testResult = underTest.run(HttpRequest.DELETE(THINGS_ROUTE).addHeader(acceptHeader));

        testResult.assertStatusCode(StatusCodes.METHOD_NOT_ALLOWED);
    }

    @Test
    public void getWithAcceptHeaderAndNoQueryParametersOpensSseConnection() {
        executeRouteTest(HttpRequest.GET(THINGS_ROUTE).addHeader(acceptHeader),
                StartStreaming.getBuilder(StreamingType.EVENTS, connectionCorrelationId,
                        AuthorizationModelFactory.newAuthContext(Collections.emptySet())).build());
    }

    @Test
    public void getWithAcceptHeaderAndFieldsParameterOpensSseConnection() {
        final String filter = "eq(attributes/manufacturer,\"ACME\")";

        final String requestUrl = THINGS_ROUTE + "?filter=" + filter;

        executeRouteTest(HttpRequest.GET(requestUrl).addHeader(acceptHeader),
                StartStreaming.getBuilder(StreamingType.EVENTS, connectionCorrelationId,
                        AuthorizationModelFactory.newAuthContext(Collections.emptySet()))
                        .withFilter(filter)
                        .build());
    }

    @Test
    public void getWithAcceptHeaderAndNamespacesParameterOpensSseConnection() {
        final Collection<String> namespaces = Lists.list("john", "frum", "tanna");

        final String requestUrl = THINGS_ROUTE + "?namespaces=" + String.join(",", namespaces);

        executeRouteTest(HttpRequest.GET(requestUrl).addHeader(acceptHeader),
                StartStreaming.getBuilder(StreamingType.EVENTS, connectionCorrelationId,
                        AuthorizationModelFactory.newAuthContext(Collections.emptySet()))
                .withNamespaces(namespaces)
                .build());
    }

    @Test
    public void getWithAcceptHeaderAndExtraFieldsParameterOpensSseConnection() {
        final JsonFieldSelector extraFields = JsonFieldSelector.newInstance("attributes", "features/location");

        final String requestUrl = THINGS_ROUTE + "?extraFields=" + extraFields;

        executeRouteTest(HttpRequest.GET(requestUrl).addHeader(acceptHeader),
                StartStreaming.getBuilder(StreamingType.EVENTS, connectionCorrelationId,
                        AuthorizationModelFactory.newAuthContext(Collections.emptySet()))
                        .withExtraFields(extraFields)
                        .build());
    }

    /*
     * Please change this method only if you know exactly what you do.
     * The order of statements and everything else is crucial as SSE route testing is not provided by Akka HTTP
     * route test kit.
     */
    private void executeRouteTest(final HttpRequest httpRequest, final StartStreaming expectedStartStreaming) {
        new TestKit(actorSystem) {{
            final TestRouteResult routeResult = underTest.run(httpRequest);

            final CompletableFuture<Void> routeTestAssertions = CompletableFuture.runAsync(() -> {
                routeResult.assertMediaType(MediaTypes.TEXT_EVENT_STREAM);
                routeResult.assertStatusCode(StatusCodes.OK);
            });

            final Connect receivedConnect = streamingActor.expectMsgClass(Connect.class);

            final ActorRef publisherActor = publisherActorReference.get();
            publisherActor.tell(receivedConnect, ActorRef.noSender());

            final StartStreaming receivedStartStreaming = streamingActor.expectMsgClass(StartStreaming.class);

            assertThat(receivedStartStreaming).isEqualTo(expectedStartStreaming);

            publisherActor.tell(
                    CloseStreamExceptionally.getInstance(GatewayServiceUnavailableException.newBuilder().build(),
                            connectionCorrelationId), ActorRef.noSender());

            try {
                routeTestAssertions.join();
            } catch (final CompletionException e) {
                // Ignore as the Future is expected to fail because of exceptionally closing the SSE stream.
            }
        }};
    }

}