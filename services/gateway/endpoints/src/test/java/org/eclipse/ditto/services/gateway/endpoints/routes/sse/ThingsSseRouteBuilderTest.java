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
package org.eclipse.ditto.services.gateway.endpoints.routes.sse;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import org.assertj.core.util.Lists;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.auth.AuthorizationModelFactory;
import org.eclipse.ditto.model.base.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.services.gateway.endpoints.EndpointTestBase;
import org.eclipse.ditto.services.gateway.streaming.Connect;
import org.eclipse.ditto.services.gateway.streaming.StartStreaming;
import org.eclipse.ditto.services.gateway.streaming.actors.SessionedJsonifiable;
import org.eclipse.ditto.services.utils.pubsub.StreamingType;
import org.eclipse.ditto.signals.commands.base.exceptions.GatewayServiceUnavailableException;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThing;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingResponse;
import org.eclipse.ditto.signals.commands.thingsearch.query.StreamThings;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import akka.actor.ActorSystem;
import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.MediaTypes;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.Route;
import akka.http.javadsl.testkit.TestRoute;
import akka.http.javadsl.testkit.TestRouteResult;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Source;
import akka.stream.javadsl.SourceQueueWithComplete;
import akka.stream.javadsl.StreamRefs;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;

/**
 * Unit test for the route built with {@link org.eclipse.ditto.services.gateway.endpoints.routes.sse.ThingsSseRouteBuilder}.
 */
public final class ThingsSseRouteBuilderTest extends EndpointTestBase {

    private static final String THINGS_ROUTE = "/things";
    private static final String SEARCH_ROUTE = "/search/things";

    private static ActorSystem actorSystem;
    private static HttpHeader acceptHeader;

    @Rule
    public final TestName testName = new TestName();

    private String connectionCorrelationId;
    private TestProbe streamingActor;
    private TestRoute underTest;
    private TestProbe proxyActor;

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
        streamingActor = TestProbe.apply("streaming", actorSystem);
        proxyActor = TestProbe.apply("proxy", actorSystem);

        connectionCorrelationId = testName.getMethodName();

        final Supplier<CompletionStage<DittoHeaders>> dittoHeadersSupplier = () -> {
            final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                    .correlationId(connectionCorrelationId)
                    .build();
            return CompletableFuture.completedFuture(dittoHeaders);
        };

        final ThingsSseRouteBuilder sseRouteBuilder =
                ThingsSseRouteBuilder.getInstance(streamingActor.ref(), streamingConfig, proxyActor.ref());
        sseRouteBuilder.withProxyActor(proxyActor.ref());
        final Route sseRoute = extractRequestContext(ctx -> sseRouteBuilder.build(ctx, dittoHeadersSupplier));
        underTest = testRoute(sseRoute);
    }

    @Test
    public void getWithoutAcceptHeaderFails() {
        underTest.run(HttpRequest.GET(THINGS_ROUTE)).assertStatusCode(StatusCodes.NOT_FOUND);
        underTest.run(HttpRequest.GET(SEARCH_ROUTE)).assertStatusCode(StatusCodes.NOT_FOUND);
    }

    @Test
    public void putWithAcceptHeaderFails() {
        underTest.run(HttpRequest.PUT(THINGS_ROUTE).addHeader(acceptHeader))
                .assertStatusCode(StatusCodes.METHOD_NOT_ALLOWED);
        underTest.run(HttpRequest.PUT(SEARCH_ROUTE).addHeader(acceptHeader))
                .assertStatusCode(StatusCodes.METHOD_NOT_ALLOWED);
    }

    @Test
    public void postWithAcceptHeaderFails() {
        underTest.run(HttpRequest.POST(THINGS_ROUTE).addHeader(acceptHeader))
                .assertStatusCode(StatusCodes.METHOD_NOT_ALLOWED);
        underTest.run(HttpRequest.POST(SEARCH_ROUTE).addHeader(acceptHeader))
                .assertStatusCode(StatusCodes.METHOD_NOT_ALLOWED);
    }

    @Test
    public void deleteWithAcceptHeaderFails() {
        underTest.run(HttpRequest.DELETE(THINGS_ROUTE).addHeader(acceptHeader))
                .assertStatusCode(StatusCodes.METHOD_NOT_ALLOWED);
        underTest.run(HttpRequest.DELETE(SEARCH_ROUTE).addHeader(acceptHeader))
                .assertStatusCode(StatusCodes.METHOD_NOT_ALLOWED);
    }

    @Test
    public void getWithAcceptHeaderAndNoQueryParametersOpensSseConnection() {
        executeThingsRouteTest(HttpRequest.GET(THINGS_ROUTE).addHeader(acceptHeader),
                StartStreaming.getBuilder(StreamingType.EVENTS, connectionCorrelationId,
                        AuthorizationModelFactory.newAuthContext(DittoAuthorizationContextType.UNSPECIFIED,
                                Collections.emptySet())).build());
    }

    @Test
    public void searchWithoutQueryParameters() {
        final TestRouteResult routeResult = underTest.run(HttpRequest.GET(SEARCH_ROUTE).addHeader(acceptHeader));
        final CompletableFuture<Void> assertions =
                CompletableFuture.runAsync(() -> {
                    routeResult.assertMediaType(MediaTypes.TEXT_EVENT_STREAM);
                    routeResult.assertStatusCode(StatusCodes.OK);
                });
        proxyActor.expectMsgClass(StreamThings.class);
        replySourceRef(proxyActor, Source.lazily(Source::empty));
        assertions.join();
    }

    @Test
    public void searchWithQueryParameters() {
        final String filter = "not(exists(thingId))";
        final String option = "sort(-policyId)";
        final String namespaces = "a,b,c";
        final String url = SEARCH_ROUTE + "?filter=" + filter + "&option=" + option + "&namespaces=" + namespaces;
        final TestRouteResult routeResult = underTest.run(HttpRequest.GET(url).addHeader(acceptHeader));
        final CompletableFuture<Void> assertions =
                CompletableFuture.runAsync(() -> {
                    routeResult.assertMediaType(MediaTypes.TEXT_EVENT_STREAM);
                    routeResult.assertStatusCode(StatusCodes.OK);
                });
        final StreamThings streamThings = proxyActor.expectMsgClass(StreamThings.class);
        replySourceRef(proxyActor, Source.lazily(Source::empty));
        assertions.join();
        assertThat(streamThings.getFilter()).contains(filter);
        assertThat(streamThings.getNamespaces()).contains(Set.of("a", "b", "c"));
        // sort options are parsed and appended with +/thingId
        assertThat(streamThings.getSort()).contains("sort(-/policyId,+/thingId)");
    }

    @Test
    public void searchWithResumption() {
        final String lastEventId = "my:lastEventId";
        final HttpHeader lastEventIdHeader = HttpHeader.parse("Last-Event-ID", lastEventId);
        final TestRouteResult routeResult = underTest.run(
                HttpRequest.GET(SEARCH_ROUTE)
                        .addHeader(acceptHeader)
                        .addHeader(lastEventIdHeader)
        );
        final CompletableFuture<Void> assertions =
                CompletableFuture.runAsync(() -> {
                    routeResult.assertMediaType(MediaTypes.TEXT_EVENT_STREAM);
                    routeResult.assertStatusCode(StatusCodes.OK);
                });
        final RetrieveThing retrieveThing = proxyActor.expectMsgClass(RetrieveThing.class);
        final Thing thing = Thing.newBuilder().setId(ThingId.of(lastEventId)).build();
        proxyActor.reply(RetrieveThingResponse.of(ThingId.of(lastEventId),
                thing,
                retrieveThing.getDittoHeaders()
        ));
        final StreamThings streamThings = proxyActor.expectMsgClass(StreamThings.class);
        replySourceRef(proxyActor, Source.lazily(Source::empty));
        assertions.join();
        assertThat(streamThings.getSortValues()).contains(JsonArray.of(JsonValue.of(lastEventId)));
    }

    @Test
    public void getWithAcceptHeaderAndFilterParameterOpensSseConnection() {
        final String filter = "eq(attributes/manufacturer,\"ACME\")";

        final String requestUrl = THINGS_ROUTE + "?filter=" + filter;

        executeThingsRouteTest(HttpRequest.GET(requestUrl).addHeader(acceptHeader),
                StartStreaming.getBuilder(StreamingType.EVENTS, connectionCorrelationId,
                        AuthorizationModelFactory.newAuthContext(DittoAuthorizationContextType.UNSPECIFIED,
                                Collections.emptySet()))
                        .withFilter(filter)
                        .build());
    }

    @Test
    public void getWithAcceptHeaderAndNamespacesParameterOpensSseConnection() {
        final Collection<String> namespaces = Lists.list("john", "frum", "tanna");

        final String requestUrl = THINGS_ROUTE + "?namespaces=" + String.join(",", namespaces);

        executeThingsRouteTest(HttpRequest.GET(requestUrl).addHeader(acceptHeader),
                StartStreaming.getBuilder(StreamingType.EVENTS, connectionCorrelationId,
                        AuthorizationModelFactory.newAuthContext(DittoAuthorizationContextType.UNSPECIFIED,
                                Collections.emptySet()))
                        .withNamespaces(namespaces)
                        .build());
    }

    @Test
    public void getWithAcceptHeaderAndExtraFieldsParameterOpensSseConnection() {
        final JsonFieldSelector extraFields = JsonFieldSelector.newInstance("attributes", "features/location");

        final String requestUrl = THINGS_ROUTE + "?extraFields=" + extraFields;

        executeThingsRouteTest(HttpRequest.GET(requestUrl).addHeader(acceptHeader),
                StartStreaming.getBuilder(StreamingType.EVENTS, connectionCorrelationId,
                        AuthorizationModelFactory.newAuthContext(DittoAuthorizationContextType.UNSPECIFIED,
                                Collections.emptySet()))
                        .withExtraFields(extraFields)
                        .build());
    }

    /*
     * Please change this method only if you know exactly what you do.
     * The order of statements and everything else is crucial as SSE route testing is not provided by Akka HTTP
     * route test kit.
     */
    private void executeThingsRouteTest(final HttpRequest httpRequest, final StartStreaming expectedStartStreaming) {
        new TestKit(actorSystem) {{
            final TestRouteResult routeResult = underTest.run(httpRequest);

            final CompletableFuture<Void> routeTestAssertions = CompletableFuture.runAsync(() -> {
                routeResult.assertMediaType(MediaTypes.TEXT_EVENT_STREAM);
                routeResult.assertStatusCode(StatusCodes.OK);
            });

            final Connect receivedConnect = streamingActor.expectMsgClass(Connect.class);
            streamingActor.reply(streamingActor.ref());

            final SourceQueueWithComplete<SessionedJsonifiable> publisherQueue =
                    receivedConnect.getEventAndResponsePublisher();

            final StartStreaming receivedStartStreaming = streamingActor.expectMsgClass(StartStreaming.class);

            // exclude connectionCorrelationId from being equal as the backend adds a random UUID to it:
            assertThat(receivedStartStreaming.getAuthorizationContext())
                    .isEqualTo(expectedStartStreaming.getAuthorizationContext());
            assertThat(receivedStartStreaming.getExtraFields())
                    .isEqualTo(expectedStartStreaming.getExtraFields());
            assertThat(receivedStartStreaming.getFilter())
                    .isEqualTo(expectedStartStreaming.getFilter());
            assertThat(receivedStartStreaming.getNamespaces())
                    .isEqualTo(expectedStartStreaming.getNamespaces());
            assertThat(receivedStartStreaming.getStreamingType())
                    .isEqualTo(expectedStartStreaming.getStreamingType());

            publisherQueue.fail(GatewayServiceUnavailableException.newBuilder().build());

            try {
                routeTestAssertions.join();
            } catch (final CompletionException e) {
                // Ignore as the Future is expected to fail because of exceptionally closing the SSE stream.
            }
        }};
    }

    private static void replySourceRef(final TestProbe testProbe, final Source<?, ?> source) {
        testProbe.reply(source.toMat(StreamRefs.sourceRef(), Keep.right()).run(actorSystem));
    }
}
