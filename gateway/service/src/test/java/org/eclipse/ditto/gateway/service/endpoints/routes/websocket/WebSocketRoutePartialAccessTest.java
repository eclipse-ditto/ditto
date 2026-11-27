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
package org.eclipse.ditto.gateway.service.endpoints.routes.websocket;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.apache.pekko.http.javadsl.model.StatusCodes;
import org.apache.pekko.http.javadsl.model.Uri;
import org.apache.pekko.http.javadsl.server.Route;
import org.apache.pekko.http.javadsl.testkit.TestRoute;
import org.apache.pekko.http.javadsl.testkit.TestRouteResult;
import org.apache.pekko.http.javadsl.testkit.WSProbe;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.SystemMaterializer;
import org.apache.pekko.stream.javadsl.SourceQueueWithComplete;
import org.apache.pekko.testkit.TestProbe;
import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.acks.AcknowledgementRequest;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.DittoHeadersBuilder;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgement;
import org.eclipse.ditto.gateway.service.endpoints.EndpointTestBase;
import org.eclipse.ditto.gateway.service.streaming.actors.SessionedJsonifiable;
import org.eclipse.ditto.gateway.service.streaming.actors.SessionedSignals;
import org.eclipse.ditto.gateway.service.streaming.actors.StreamingActor;
import org.eclipse.ditto.gateway.service.streaming.signals.Connect;
import org.eclipse.ditto.gateway.service.streaming.signals.IncomingSignal;
import org.eclipse.ditto.internal.models.signalenrichment.SignalEnrichmentFacade;
import org.eclipse.ditto.internal.utils.tracing.DittoTracingInitResource;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.protocol.adapter.DittoProtocolAdapter;
import org.eclipse.ditto.things.model.Attributes;
import org.eclipse.ditto.things.model.ThingFieldSelector;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.events.AttributeModified;
import org.eclipse.ditto.things.model.signals.events.AttributesModified;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import scala.concurrent.duration.FiniteDuration;

/**
 * Tests that the WebSocket route delivers to a subscriber with restricted READ access only what it may read and
 * evaluates its RQL filter only on that.
 */
public final class WebSocketRoutePartialAccessTest extends EndpointTestBase {

    @ClassRule
    public static final DittoTracingInitResource DITTO_TRACING_INIT_RESOURCE =
            DittoTracingInitResource.disableDittoTracing();

    private static final Executor ASYNC_TEST_EXECUTOR = command -> {
        final Thread thread = new Thread(command, "WebSocketRoutePartialAccessTest-async");
        thread.setDaemon(true);
        thread.start();
    };

    private static final ThingId THING_ID = ThingId.of("org.eclipse.ditto:my-thing-1");
    private static final AuthorizationSubject ALICE = AuthorizationSubject.newInstance("nginx:alice");
    private static final AuthorizationSubject GROUP_X = AuthorizationSubject.newInstance("nginx:group-x");
    private static final AuthorizationSubject CAROL = AuthorizationSubject.newInstance("nginx:carol");
    private static final AcknowledgementLabel ACK_LABEL = AcknowledgementLabel.of("custom-ack");
    private static final ThingFieldSelector EXTRA_FIELDS =
            ThingFieldSelector.fromJsonFieldSelector(JsonFieldSelector.newInstance("attributes"));
    private static final JsonObject READABLE_ATTRIBUTES = JsonObject.of("{\"pub\":\"p\"}");

    private TestProbe streamingActor;
    private TestProbe streamingSessionActor;
    private WSProbe wsProbe;
    private SourceQueueWithComplete<SessionedJsonifiable> publisherQueue;

    @Before
    public void setUp() {
        streamingActor = TestProbe.apply("streaming", system());
        streamingSessionActor = TestProbe.apply("streamingSession", system());
        final Materializer materializer = SystemMaterializer.get(system()).materializer();
        // returns also what the subscriber may not read, so that the route itself must remove it
        final SignalEnrichmentFacade facade = (thingId, fields, headers, signal) ->
                CompletableFuture.completedFuture(JsonObject.of("{\"attributes\":{\"pub\":\"p\",\"secret\":\"s\"}}"));
        final WebSocketRouteBuilder routeBuilder =
                WebSocketRoute.getInstance(system(), streamingActor.ref(), streamingConfig, materializer)
                        .withSignalEnrichmentProvider(request -> CompletableFuture.completedFuture(facade))
                        .withHeaderTranslator(httpHeaderTranslator);
        final Route route = extractRequestContext(ctx -> routeBuilder.build(JsonSchemaVersion.V_2,
                testNameCorrelationId.getCorrelationId(), dittoHeaders, DittoProtocolAdapter.newInstance(), ctx));
        final TestRoute testRoute = testRoute(route);
        wsProbe = WSProbe.create(system(), materializer);

        final CompletableFuture<TestRouteResult> upgrade = CompletableFuture.supplyAsync(
                () -> testRoute.run(WS(Uri.create("/ws/2"), wsProbe.flow(), materializer)), ASYNC_TEST_EXECUTOR);
        streamingActor.expectMsg(StreamingActor.Control.RETRIEVE_WEBSOCKET_CONFIG);
        streamingActor.reply(streamingConfig.getWebsocketConfig());
        final Connect connect = streamingActor.expectMsgClass(Connect.class);
        streamingActor.reply(streamingSessionActor.ref());
        upgrade.join().assertStatusCode(StatusCodes.SWITCHING_PROTOCOLS);
        publisherQueue = connect.getEventAndResponsePublisher();
    }

    @After
    public void tearDown() {
        if (null != publisherQueue) {
            publisherQueue.complete();
        }
    }

    @Test
    public void partialReaderReceivesOnlyReadableFieldsAndIsNotFilteredByUnreadableOnes() {
        final DittoHeaders partialForCarol = withAckRequest(DittoHeaders.newBuilder()
                .putHeader(DittoHeaderDefinition.PARTIAL_ACCESS_PATHS.getKey(),
                        "{\"subjects\":[\"nginx:carol\"],\"paths\":{\"attributes/pub\":[0]}}")
                .readGrantedSubjects(List.of(CAROL)));

        assertOnlyReadableFieldsAreDeliveredAndFiltered(partialForCarol, sessionHeaders(CAROL));
    }

    @Test
    public void subscriberWithRevokeOnOneOfItsSubjectsReceivesOnlyReadableFieldsAndIsNotFilteredByUnreadableOnes() {
        final DittoHeaders revokedForGroupX = withAckRequest(DittoHeaders.newBuilder()
                .putHeader(DittoHeaderDefinition.REVOKED_ACCESS_PATHS.getKey(),
                        "{\"subjects\":[\"nginx:group-x\"],\"paths\":{\"attributes/secret\":[0]}}")
                .readGrantedSubjects(List.of(ALICE)));

        assertOnlyReadableFieldsAreDeliveredAndFiltered(revokedForGroupX, sessionHeaders(ALICE, GROUP_X));
    }

    @Test
    public void changeFilterIsEvaluatedOnlyOnReadableFields() {
        final DittoHeaders revokedForGroupX = withAckRequest(DittoHeaders.newBuilder()
                .putHeader(DittoHeaderDefinition.REVOKED_ACCESS_PATHS.getKey(),
                        "{\"subjects\":[\"nginx:group-x\"],\"paths\":{\"attributes/secret\":[0]}}")
                .readGrantedSubjects(List.of(ALICE)));
        final DittoHeaders sessionHeaders = sessionHeaders(ALICE, GROUP_X);
        final Attributes attributes = Attributes.newBuilder().set("pub", "p").set("secret", "s").build();

        offerWithChangeFilter(AttributesModified.of(THING_ID, attributes, 2L, null,
                withCorrelationId(revokedForGroupX, "unreadable-change"), null), sessionHeaders,
                "eq(attributes/secret,\"s\")");
        offerWithChangeFilter(AttributesModified.of(THING_ID, attributes, 3L, null, revokedForGroupX, null),
                sessionHeaders, "eq(attributes/pub,\"p\")");
        final JsonObject delivered = JsonObject.of(wsProbe.expectMessage().asTextMessage().getStrictText());

        assertThat(delivered.getValue("revision")).contains(JsonValue.of(3L));
        assertThat(delivered.toString()).doesNotContain("secret");
        expectWeakAck("unreadable-change");
        streamingSessionActor.expectNoMessage(FiniteDuration.create(200, TimeUnit.MILLISECONDS));
    }

    private void assertOnlyReadableFieldsAreDeliveredAndFiltered(final DittoHeaders eventHeaders,
            final DittoHeaders sessionHeaders) {

        final Attributes attributes = Attributes.newBuilder().set("pub", "p").set("secret", "s").build();
        offer(AttributesModified.of(THING_ID, attributes, 2L, null, eventHeaders, null), sessionHeaders, null);
        final JsonObject delivered = JsonObject.of(wsProbe.expectMessage().asTextMessage().getStrictText());

        offer(AttributesModified.of(THING_ID, attributes, 3L, null, withCorrelationId(eventHeaders, "filtered"),
                null), sessionHeaders, "eq(attributes/secret,\"s\")");
        offer(AttributeModified.of(THING_ID, JsonPointer.of("secret"), JsonValue.of("s"), 4L, null,
                withCorrelationId(eventHeaders, "unreadable"), null), sessionHeaders, null);
        offer(AttributesModified.of(THING_ID, attributes, 5L, null, eventHeaders, null), sessionHeaders,
                "eq(attributes/pub,\"p\")");
        final JsonObject deliveredAfterDropped = JsonObject.of(wsProbe.expectMessage().asTextMessage().getStrictText());

        assertThat(delivered.toString()).doesNotContain("secret");
        assertThat(delivered.getValue("value")).contains(READABLE_ATTRIBUTES);
        assertThat(delivered.getValue("extra")).contains(JsonObject.newBuilder()
                .set("attributes", READABLE_ATTRIBUTES)
                .build());
        assertThat(deliveredAfterDropped.toString()).doesNotContain("secret");
        assertThat(deliveredAfterDropped.getValue("revision")).contains(JsonValue.of(5L));
        expectWeakAck("filtered");
        expectWeakAck("unreadable");
        streamingSessionActor.expectNoMessage(FiniteDuration.create(200, TimeUnit.MILLISECONDS));
    }

    private void expectWeakAck(final String correlationId) {
        final Signal<?> signal = streamingSessionActor.expectMsgClass(IncomingSignal.class).getSignal();
        assertThat(signal).isInstanceOf(Acknowledgement.class);
        final Acknowledgement ack = (Acknowledgement) signal;
        assertThat(ack.getLabel().toString()).isEqualTo(ACK_LABEL.toString());
        assertThat(ack.isWeak()).isTrue();
        assertThat(ack.getEntityId().toString()).isEqualTo(THING_ID.toString());
        assertThat(ack.getDittoHeaders().getCorrelationId()).contains(correlationId);
    }

    private void offer(final Signal<?> event, final DittoHeaders sessionHeaders, @Nullable final String filter) {
        publisherQueue.offer(SessionedSignals.of(event, sessionHeaders, filter, EXTRA_FIELDS,
                        streamingSessionActor.ref()))
                .toCompletableFuture()
                .join();
    }

    private void offerWithChangeFilter(final Signal<?> event, final DittoHeaders sessionHeaders,
            final String changeFilter) {
        publisherQueue.offer(SessionedSignals.of(event, sessionHeaders, null, changeFilter, EXTRA_FIELDS,
                        streamingSessionActor.ref()))
                .toCompletableFuture()
                .join();
    }

    private DittoHeaders sessionHeaders(final AuthorizationSubject... subjects) {
        return dittoHeaders.toBuilder()
                .authorizationContext(AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED,
                        List.of(subjects)))
                .build();
    }

    private static DittoHeaders withCorrelationId(final DittoHeaders headers, final String correlationId) {
        return headers.toBuilder().correlationId(correlationId).build();
    }

    private static DittoHeaders withAckRequest(final DittoHeadersBuilder<?, ?> builder) {
        return builder.acknowledgementRequest(AcknowledgementRequest.of(ACK_LABEL)).build();
    }

}
