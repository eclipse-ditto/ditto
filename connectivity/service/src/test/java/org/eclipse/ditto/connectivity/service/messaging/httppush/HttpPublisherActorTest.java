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
package org.eclipse.ditto.connectivity.service.messaging.httppush;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.connectivity.service.messaging.httppush.HttpPushSpecificConfig.OMIT_REQUEST_BODY;
import static org.eclipse.ditto.connectivity.service.messaging.httppush.HttpTestDittoProtocolHelper.signalToJsonString;
import static org.eclipse.ditto.connectivity.service.messaging.httppush.HttpTestDittoProtocolHelper.signalToMultiMapped;
import static org.mockito.Mockito.mock;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.acks.AcknowledgementRequest;
import org.eclipse.ditto.base.model.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.base.model.common.DittoConstants;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgements;
import org.eclipse.ditto.connectivity.api.ExternalMessageFactory;
import org.eclipse.ditto.connectivity.api.OutboundSignal;
import org.eclipse.ditto.connectivity.api.OutboundSignalFactory;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.HmacCredentials;
import org.eclipse.ditto.connectivity.model.Target;
import org.eclipse.ditto.connectivity.model.Topic;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.connectivity.service.messaging.AbstractPublisherActorTest;
import org.eclipse.ditto.connectivity.service.messaging.ConnectivityStatusResolver;
import org.eclipse.ditto.connectivity.service.messaging.TestConstants;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.ConnectionMonitor;
import org.eclipse.ditto.internal.utils.metrics.instruments.timer.PreparedTimer;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.messages.model.Message;
import org.eclipse.ditto.messages.model.MessageDirection;
import org.eclipse.ditto.messages.model.MessageHeaders;
import org.eclipse.ditto.messages.model.signals.commands.SendFeatureMessageResponse;
import org.eclipse.ditto.messages.model.signals.commands.SendThingMessage;
import org.eclipse.ditto.messages.model.signals.commands.SendThingMessageResponse;
import org.eclipse.ditto.protocol.ProtocolFactory;
import org.eclipse.ditto.protocol.adapter.DittoProtocolAdapter;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThing;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThingResponse;
import org.junit.Test;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.event.LoggingAdapter;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpEntity;
import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.HttpMethod;
import akka.http.javadsl.model.HttpMethods;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.Uri;
import akka.japi.Pair;
import akka.stream.Attributes;
import akka.stream.SystemMaterializer;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;
import akka.util.ByteString;
import scala.util.Try;

/**
 * Tests {@link HttpPublisherActor}.
 */
public final class HttpPublisherActorTest extends AbstractPublisherActorTest {

    private static final String CONTENT_TYPE = "APPLICATION/VND.ECLIPSE.DITTO+JSON ; PARAM_NAME=PARAM_VALUE";
    private static final String CUSTOM_HEADER_NAME = "my-custom-header";
    private static final String CUSTOM_HEADER_VALUE = "bumlux";
    private static final String BODY = "[\"The quick brown fox jumps over the lazy dog.\"]";

    private HttpPushFactory httpPushFactory;
    private final BlockingQueue<HttpRequest> received = new LinkedBlockingQueue<>();

    @Override
    protected String getOutboundAddress() {
        return "PATCH:/the/quick/brown/fox/jumps/over/the/lazy/dog?someQuery=foo&entity={{ entity:id }}";
    }

    @Override
    protected void setupMocks(final TestProbe probe) {
        httpPushFactory = mockHttpPushFactory(CONTENT_TYPE, HttpStatus.OK, BODY);

        // activate debug log to show responses
        actorSystem.eventStream().setLogLevel(Attributes.logLevelDebug());
    }

    @Override
    protected Props getPublisherActorProps() {
        return HttpPublisherActor.props(TestConstants.createConnection(),
                httpPushFactory,
                mock(ConnectivityStatusResolver.class),
                ConnectivityConfig.of(actorSystem.settings().config()));
    }

    @Override
    protected Target decorateTarget(final Target target) {
        return target;
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    @Override
    protected void verifyPublishedMessage() throws Exception {
        final var request = received.take();
        assertThat(received).isEmpty();

        // method
        assertThat(request.method()).isEqualTo(HttpMethods.PATCH);

        // uri
        assertThat(request.getUri().host().address()).isEqualTo("8.8.4.4");
        assertThat(request.getUri().query().get("entity")).contains(TestConstants.Things.THING_ID.toString());
        assertThat(request.getUri().port()).isEqualTo(12345);

        // headers
        assertThat(request.getHeader("thing_id").get().value()).isEqualTo(TestConstants.Things.THING_ID.toString());
        assertThat(request.getHeader("suffixed_thing_id").get().value())
                .contains(TestConstants.Things.THING_ID + ".some.suffix");
        assertThat(request.getHeader("prefixed_thing_id").get().value())
                .isEqualTo("some.prefix." + TestConstants.Things.THING_ID);
        assertThat(request.getHeader("eclipse").get().value()).isEqualTo("ditto");
        assertThat(request.getHeader("device_id").get().value()).isEqualTo(TestConstants.Things.THING_ID.toString());

        final var entity = request.entity()
                .toStrict(60_000L, SystemMaterializer.get(actorSystem).materializer())
                .toCompletableFuture()
                .join();
        assertThat(entity.getData().utf8String()).isEqualTo("payload");
        if (!entity.getContentType().toString().equals(DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE)) {
            // Ditto protocol content type is parsed as binary for some reason
            assertThat(entity.getContentType().binary()).isFalse();
        }
    }

    @Override
    protected void verifyAcknowledgements(final Supplier<Acknowledgements> ackSupplier) {
        final var acks = ackSupplier.get();
        assertThat(acks.getSize()).describedAs("Expect 1 acknowledgement in: " + acks).isEqualTo(1);
        final var ack = acks.stream().findAny().orElseThrow();
        assertThat(ack.getLabel().toString()).describedAs("Ack label").hasToString("please-verify");
        assertThat(ack.getHttpStatus()).describedAs("Ack status").isEqualTo(HttpStatus.OK);
        assertThat(ack.getEntity()).contains(JsonFactory.readFrom(BODY));
        assertThat(ack.getDittoHeaders()).containsAllEntriesOf(
                Map.of("content-type", "application/vnd.eclipse.ditto+json; PARAM_NAME=PARAM_VALUE", CUSTOM_HEADER_NAME,
                        CUSTOM_HEADER_VALUE));
    }

    @Test
    public void testPlainTextAck() {
        new TestKit(actorSystem) {{
            httpPushFactory = mockHttpPushFactory("text/plain", HttpStatus.OK, "hello!");

            final var label = AcknowledgementLabel.of("please-verify");
            final var target = decorateTarget(createTestTarget(label));

            final var props = getPublisherActorProps();
            final var publisherActor = childActorOf(props);
            publisherCreated(this, publisherActor);

            publisherActor.tell(newMultiMappedWithContentType(target, getRef()), getRef());
            final var acks = expectMsgClass(Acknowledgements.class);
            assertThat(acks.getAcknowledgement(label)).isNotEmpty();
            final var ack = acks.getAcknowledgement(label).orElseThrow();
            assertThat(ack.getDittoHeaders()).containsAllEntriesOf(
                    Map.of("content-type", "text/plain", CUSTOM_HEADER_NAME, CUSTOM_HEADER_VALUE));
            assertThat(ack.getEntity()).contains(JsonValue.of("hello!"));
        }};
    }

    @Test
    public void testBinaryAck() {
        new TestKit(actorSystem) {{
            httpPushFactory = mockHttpPushFactory("application/octet-stream", HttpStatus.OK, "hello!");

            final var label = AcknowledgementLabel.of("please-verify");
            final var target = decorateTarget(createTestTarget(label));

            final var props = getPublisherActorProps();
            final var publisherActor = childActorOf(props);
            publisherCreated(this, publisherActor);

            publisherActor.tell(newMultiMappedWithContentType(target, getRef()), getRef());
            final var acks = expectMsgClass(Acknowledgements.class);
            assertThat(acks.getAcknowledgement(label)).isNotEmpty();
            final var ack = acks.getAcknowledgement(label).orElseThrow();
            assertThat(ack.getDittoHeaders()).containsAllEntriesOf(
                    Map.of("content-type", "application/octet-stream", CUSTOM_HEADER_NAME, CUSTOM_HEADER_VALUE));
            assertThat(ack.getEntity()).contains(JsonValue.of("aGVsbG8h"));
        }};
    }

    @Test
    public void testMessageCommandHttpPushCreatesCommandResponse() {
        new TestKit(actorSystem) {{
            final var customContentType = "application/vnd.org.eclipse.ditto.foobar+json";
            final var httpStatus = HttpStatus.IM_A_TEAPOT;
            final var jsonResponse = JsonFactory.readFrom("{ \"foo\": true }");
            httpPushFactory = mockHttpPushFactory(customContentType, httpStatus, jsonResponse.toString());

            final var target = ConnectivityModelFactory.newTargetBuilder()
                    .address(getOutboundAddress())
                    .originalAddress(getOutboundAddress())
                    .authorizationContext(TestConstants.Authorization.AUTHORIZATION_CONTEXT)
                    .headerMapping(TestConstants.HEADER_MAPPING)
                    .issuedAcknowledgementLabel(DittoAcknowledgementLabel.LIVE_RESPONSE)
                    .topics(Topic.LIVE_MESSAGES)
                    .build();

            final var props = getPublisherActorProps();
            final var publisherActor = childActorOf(props);
            publisherCreated(this, publisherActor);

            final var messageDirection = MessageDirection.FROM;
            final var messageSubject = "please-respond";
            final Message<?> message = Message.newBuilder(
                    MessageHeaders.newBuilder(messageDirection, TestConstants.Things.THING_ID, messageSubject)
                            .build()
            ).build();
            final var dittoHeaders = DittoHeaders.newBuilder()
                    .correlationId(TestConstants.CORRELATION_ID)
                    .putHeader("device_id", "ditto:thing")
                    .acknowledgementRequest(AcknowledgementRequest.of(DittoAcknowledgementLabel.LIVE_RESPONSE))
                    .build();
            final Signal<?> source = SendThingMessage.of(TestConstants.Things.THING_ID, message, dittoHeaders);
            final var outboundSignal =
                    OutboundSignalFactory.newOutboundSignal(source, Collections.singletonList(target));
            final var externalMessage =
                    ExternalMessageFactory.newExternalMessageBuilder(Collections.emptyMap())
                            .withText("payload")
                            .build();
            final var adaptable = DittoProtocolAdapter.newInstance().toAdaptable(source);
            final var mapped =
                    OutboundSignalFactory.newMappedOutboundSignal(outboundSignal, adaptable, externalMessage);

            publisherActor.tell(
                    OutboundSignalFactory.newMultiMappedOutboundSignal(Collections.singletonList(mapped), getRef()),
                    getRef());

            final SendThingMessageResponse<JsonValue> sendThingMessageResponse =
                    expectMsgClass(SendThingMessageResponse.class);
            assertThat((CharSequence) sendThingMessageResponse.getEntityId()).isEqualTo(TestConstants.Things.THING_ID);
            assertThat(sendThingMessageResponse.getHttpStatus()).isEqualTo(httpStatus);
            assertThat(sendThingMessageResponse.getDittoHeaders().getCorrelationId())
                    .contains(TestConstants.CORRELATION_ID);

            final var responseMessage = sendThingMessageResponse.getMessage();
            assertThat(responseMessage.getContentType()).contains(customContentType);
            assertThat(responseMessage.getSubject()).isEqualTo(messageSubject);
            assertThat(responseMessage.getDirection()).isEqualTo(messageDirection);
            assertThat(responseMessage.getHttpStatus()).hasValue(httpStatus);
            assertThat(responseMessage.getPayload()).contains(jsonResponse);

            final var responseMessageHeaders = responseMessage.getHeaders();
            assertThat(responseMessageHeaders).containsKey(CUSTOM_HEADER_NAME);
        }};
    }

    @Test
    public void testMessageCommandHttpPushWithNonLiveResponseIssuedAcknowledgement() {
        new TestKit(actorSystem) {{
            final var contentType = "application/json";
            final var httpStatus = HttpStatus.IM_A_TEAPOT;
            final var jsonResponse = JsonFactory.readFrom("{ \"foo\": true }");
            httpPushFactory = mockHttpPushFactory(contentType, httpStatus, jsonResponse.toString());
            final var autoAckLabel = AcknowledgementLabel.of("foo:bar");
            final var target = ConnectivityModelFactory.newTargetBuilder()
                    .address(getOutboundAddress())
                    .originalAddress(getOutboundAddress())
                    .authorizationContext(TestConstants.Authorization.AUTHORIZATION_CONTEXT)
                    .headerMapping(TestConstants.HEADER_MAPPING)
                    .issuedAcknowledgementLabel(autoAckLabel)
                    .topics(Topic.LIVE_MESSAGES)
                    .build();

            final var props = getPublisherActorProps();
            final var publisherActor = childActorOf(props);
            publisherCreated(this, publisherActor);

            final var messageDirection = MessageDirection.FROM;
            final var messageSubject = "please-respond";
            final Message<?> message = Message.newBuilder(
                    MessageHeaders.newBuilder(messageDirection, TestConstants.Things.THING_ID, messageSubject)
                            .build()
            ).build();
            final var dittoHeaders = DittoHeaders.newBuilder()
                    .correlationId(TestConstants.CORRELATION_ID)
                    .putHeader("device_id", "ditto:thing")
                    .acknowledgementRequest(AcknowledgementRequest.of(DittoAcknowledgementLabel.LIVE_RESPONSE),
                            AcknowledgementRequest.of(autoAckLabel))
                    .putHeader(DittoHeaderDefinition.DITTO_ACKREGATOR_ADDRESS.getKey(), getRef().path().toSerializationFormat())
                    .build();
            final Signal<?> source = SendThingMessage.of(TestConstants.Things.THING_ID, message, dittoHeaders);
            final var outboundSignal =
                    OutboundSignalFactory.newOutboundSignal(source, Collections.singletonList(target));
            final var externalMessage =
                    ExternalMessageFactory.newExternalMessageBuilder(Collections.emptyMap())
                            .withText("payload")
                            .build();
            final var adaptable = DittoProtocolAdapter.newInstance().toAdaptable(source);
            final var mapped =
                    OutboundSignalFactory.newMappedOutboundSignal(outboundSignal, adaptable, externalMessage);

            publisherActor.tell(
                    OutboundSignalFactory.newMultiMappedOutboundSignal(Collections.singletonList(mapped), getRef()),
                    getRef());

            final var acknowledgements = expectMsgClass(Acknowledgements.class);
            assertThat(acknowledgements).hasSize(1);
            final var acknowledgement = acknowledgements.getAcknowledgement(autoAckLabel).get();
            assertThat(acknowledgement).isNotNull();
            assertThat(acknowledgement.getHttpStatus()).isEqualTo(httpStatus);
            assertThat(acknowledgement.getEntityId().toString()).hasToString(TestConstants.Things.THING_ID.toString());
        }};
    }

    @Test
    public void testMessageCommandHttpPushCreatesCommandResponseFromProtocolMessage() {
        new TestKit(actorSystem) {{
            final var customContentType = "application/vnd.org.eclipse.ditto.foobar+json";
            final var messageSubject = "please-respond";
            final var contentType = DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE;
            final var httpStatus = HttpStatus.IM_A_TEAPOT;
            final var messageHeaders =
                    MessageHeaders.newBuilder(MessageDirection.FROM, TestConstants.Things.THING_ID, messageSubject)
                            .contentType(customContentType)
                            .correlationId(TestConstants.CORRELATION_ID)
                            .putHeader(CUSTOM_HEADER_NAME, CUSTOM_HEADER_VALUE)
                            .build();
            final var jsonResponse = JsonFactory.readFrom("{ \"foo\": true }");
            final var response =
                    Message.<JsonValue>newBuilder(messageHeaders).payload(jsonResponse).build();
            final var sendMessageResponse =
                    SendThingMessageResponse.of(TestConstants.Things.THING_ID, response, httpStatus,
                            messageHeaders);
            final var messageResponseAdaptable =
                    DittoProtocolAdapter.newInstance().toAdaptable(sendMessageResponse);
            final var messageResponseJson =
                    ProtocolFactory.wrapAsJsonifiableAdaptable(messageResponseAdaptable).toJson();
            httpPushFactory = mockHttpPushFactory(contentType, httpStatus, messageResponseJson.toString());

            final var target = ConnectivityModelFactory.newTargetBuilder()
                    .address(getOutboundAddress())
                    .originalAddress(getOutboundAddress())
                    .authorizationContext(TestConstants.Authorization.AUTHORIZATION_CONTEXT)
                    .headerMapping(TestConstants.HEADER_MAPPING)
                    .issuedAcknowledgementLabel(DittoAcknowledgementLabel.LIVE_RESPONSE)
                    .topics(Topic.LIVE_MESSAGES)
                    .build();

            final var props = getPublisherActorProps();
            final var publisherActor = childActorOf(props);
            publisherCreated(this, publisherActor);

            final var messageDirection = MessageDirection.FROM;
            final Message<?> message = Message.newBuilder(
                    MessageHeaders.newBuilder(messageDirection, TestConstants.Things.THING_ID, messageSubject)
                            .build()
            ).build();
            final var dittoHeaders = DittoHeaders.newBuilder()
                    .correlationId(TestConstants.CORRELATION_ID)
                    .putHeader("device_id", "ditto:thing")
                    .acknowledgementRequest(AcknowledgementRequest.of(DittoAcknowledgementLabel.LIVE_RESPONSE))
                    .build();
            final Signal<?> source = SendThingMessage.of(TestConstants.Things.THING_ID, message, dittoHeaders);
            final var outboundSignal =
                    OutboundSignalFactory.newOutboundSignal(source, Collections.singletonList(target));
            final var externalMessage =
                    ExternalMessageFactory.newExternalMessageBuilder(Collections.emptyMap())
                            .withText("payload")
                            .build();
            final var adaptable = DittoProtocolAdapter.newInstance().toAdaptable(source);
            final var mapped =
                    OutboundSignalFactory.newMappedOutboundSignal(outboundSignal, adaptable, externalMessage);

            publisherActor.tell(
                    OutboundSignalFactory.newMultiMappedOutboundSignal(Collections.singletonList(mapped), getRef()),
                    getRef());

            final SendThingMessageResponse<JsonValue> sendThingMessageResponse =
                    expectMsgClass(SendThingMessageResponse.class);
            assertThat((CharSequence) sendThingMessageResponse.getEntityId()).isEqualTo(TestConstants.Things.THING_ID);
            assertThat(sendThingMessageResponse.getHttpStatus()).isEqualTo(httpStatus);
            assertThat(sendThingMessageResponse.getDittoHeaders().getCorrelationId())
                    .contains(TestConstants.CORRELATION_ID);

            final var responseMessage = sendThingMessageResponse.getMessage();
            assertThat(responseMessage.getContentType()).contains(customContentType);
            assertThat(responseMessage.getSubject()).isEqualTo(messageSubject);
            assertThat(responseMessage.getDirection()).isEqualTo(messageDirection);
            assertThat(responseMessage.getHttpStatus()).hasValue(httpStatus);
            assertThat(responseMessage.getPayload()).contains(jsonResponse);

            final var responseMessageHeaders = responseMessage.getHeaders();
            assertThat(responseMessageHeaders).containsKey(CUSTOM_HEADER_NAME);
        }};
    }

    @Test
    public void testLiveCommandHttpPushCreatesLiveCommandResponseFromProtocolMessage() {
        // Arrange
        final var testCorrelationId = TestConstants.CORRELATION_ID.concat(".liveCommandHttpPush");
        final var contentType = DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE;
        final var thingId = TestConstants.Things.THING_ID;

        final var commandResponseDittoHeaders = DittoHeaders.newBuilder()
                .contentType(contentType)
                .channel("live")
                .correlationId(testCorrelationId)
                .build();

        final var retrieveThingMockResponse = RetrieveThingResponse.of(thingId,
                TestConstants.Things.THING,
                null,
                null,
                commandResponseDittoHeaders);

        httpPushFactory = mockHttpPushFactory(contentType, retrieveThingMockResponse.getHttpStatus(),
                signalToJsonString(retrieveThingMockResponse));

        final var target = ConnectivityModelFactory.newTargetBuilder()
                .address(getOutboundAddress())
                .originalAddress(getOutboundAddress())
                .authorizationContext(TestConstants.Authorization.AUTHORIZATION_CONTEXT)
                .headerMapping(TestConstants.HEADER_MAPPING)
                .topics(Topic.LIVE_COMMANDS)
                .build();

        final var testKit = new TestKit(actorSystem);
        final var publisherActor = testKit.childActorOf(getPublisherActorProps());
        publisherCreated(testKit, publisherActor);

        final var commandDittoHeaders = commandResponseDittoHeaders.toBuilder()
                .responseRequired(true)
                .build();

        final Signal<?> command = RetrieveThing.of(thingId, commandDittoHeaders);

        // Act
        publisherActor.tell(signalToMultiMapped(command, target, testKit.getRef()), testKit.getRef());

        // Assert
        final var responseSignal = testKit.expectMsgClass(Signal.class);
        assertThat(responseSignal).isInstanceOfSatisfying(RetrieveThingResponse.class, retrieveThingResponse -> {
            assertThat((CharSequence) retrieveThingResponse.getEntityId()).isEqualTo(thingId);
            assertThat(retrieveThingResponse.getHttpStatus()).isEqualTo(retrieveThingMockResponse.getHttpStatus());
            assertThat(retrieveThingResponse.getDittoHeaders().getCorrelationId()).contains(testCorrelationId);
            assertThat(retrieveThingResponse.getThing()).isEqualTo(TestConstants.Things.THING);
        });
    }

    @Test
    public void sendingLiveResponseWithWrongCorrelationIdDoesNotWork() {
        new TestKit(actorSystem) {{
            final var customContentType = "application/vnd.org.eclipse.ditto.foobar+json";
            final var messageSubject = "please-respond";
            final var contentType = DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE;
            final var httpStatus = HttpStatus.IM_A_TEAPOT;
            final var messageHeaders =
                    MessageHeaders.newBuilder(MessageDirection.FROM, TestConstants.Things.THING_ID, messageSubject)
                            .contentType(customContentType)
                            .correlationId("otherID")
                            .putHeader(CUSTOM_HEADER_NAME, CUSTOM_HEADER_VALUE)
                            .build();
            final var jsonResponse = JsonFactory.readFrom("{ \"foo\": true }");
            final var response =
                    Message.<JsonValue>newBuilder(messageHeaders).payload(jsonResponse).build();
            final var sendMessageResponse =
                    SendThingMessageResponse.of(TestConstants.Things.THING_ID, response, httpStatus, messageHeaders);
            final var messageResponseAdaptable =
                    DittoProtocolAdapter.newInstance().toAdaptable(sendMessageResponse);
            final var messageResponseJson =
                    ProtocolFactory.wrapAsJsonifiableAdaptable(messageResponseAdaptable).toJson();
            httpPushFactory = mockHttpPushFactory(contentType, httpStatus, messageResponseJson.toString());

            final var target = ConnectivityModelFactory.newTargetBuilder()
                    .address(getOutboundAddress())
                    .originalAddress(getOutboundAddress())
                    .authorizationContext(TestConstants.Authorization.AUTHORIZATION_CONTEXT)
                    .headerMapping(TestConstants.HEADER_MAPPING)
                    .issuedAcknowledgementLabel(DittoAcknowledgementLabel.LIVE_RESPONSE)
                    .topics(Topic.LIVE_MESSAGES)
                    .build();

            final var props = getPublisherActorProps();
            final var publisherActor = childActorOf(props);
            publisherCreated(this, publisherActor);

            final var messageDirection = MessageDirection.FROM;
            final Message<?> message = Message.newBuilder(
                    MessageHeaders.newBuilder(messageDirection, TestConstants.Things.THING_ID, messageSubject)
                            .build()
            ).build();
            final var dittoHeaders = DittoHeaders.newBuilder()
                    .correlationId(TestConstants.CORRELATION_ID)
                    .putHeader("device_id", "ditto:thing")
                    .acknowledgementRequest(AcknowledgementRequest.of(DittoAcknowledgementLabel.LIVE_RESPONSE))
                    .putHeader(DittoHeaderDefinition.DITTO_ACKREGATOR_ADDRESS.getKey(), getRef().path().toSerializationFormat())
                    .build();
            final Signal<?> source = SendThingMessage.of(TestConstants.Things.THING_ID, message, dittoHeaders);
            final var outboundSignal =
                    OutboundSignalFactory.newOutboundSignal(source, Collections.singletonList(target));
            final var externalMessage =
                    ExternalMessageFactory.newExternalMessageBuilder(Collections.emptyMap())
                            .withText("payload")
                            .build();
            final var adaptable = DittoProtocolAdapter.newInstance().toAdaptable(source);
            final var mapped =
                    OutboundSignalFactory.newMappedOutboundSignal(outboundSignal, adaptable, externalMessage);

            publisherActor.tell(
                    OutboundSignalFactory.newMultiMappedOutboundSignal(Collections.singletonList(mapped), getRef()),
                    getRef());

            final var acknowledgements = expectMsgClass(Duration.ofSeconds(5), Acknowledgements.class);
            assertThat((CharSequence) acknowledgements.getEntityId()).isEqualTo(TestConstants.Things.THING_ID);
            assertThat(acknowledgements.getHttpStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(acknowledgements.getDittoHeaders().getCorrelationId())
                    .contains(TestConstants.CORRELATION_ID);
            assertThat(acknowledgements.getSize()).isOne();

            assertThat(acknowledgements.getAcknowledgement(DittoAcknowledgementLabel.LIVE_RESPONSE))
                    .hasValueSatisfying(ack -> assertThat(ack.toJsonString())
                            .contains("Correlation ID of live response <otherID> differs from correlation ID of" +
                                    " command <cid>."));
        }};
    }

    @Test
    public void sendingLiveResponseToDifferentThingIdDoesNotWork() {
        new TestKit(actorSystem) {{
            final var customContentType = "application/vnd.org.eclipse.ditto.foobar+json";
            final var messageSubject = "please-respond";
            final var contentType = DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE;
            final var httpStatus = HttpStatus.IM_A_TEAPOT;
            final var wrongThingId = ThingId.of("namespace:wrongthing");
            final var messageHeaders =
                    MessageHeaders.newBuilder(MessageDirection.FROM, wrongThingId, messageSubject)
                            .contentType(customContentType)
                            .correlationId(TestConstants.CORRELATION_ID)
                            .putHeader(CUSTOM_HEADER_NAME, CUSTOM_HEADER_VALUE)
                            .build();
            final var jsonResponse = JsonFactory.readFrom("{ \"foo\": true }");
            final var response =
                    Message.<JsonValue>newBuilder(messageHeaders).payload(jsonResponse).build();
            final var sendMessageResponse =
                    SendThingMessageResponse.of(wrongThingId, response, httpStatus, messageHeaders);
            final var messageResponseAdaptable =
                    DittoProtocolAdapter.newInstance().toAdaptable(sendMessageResponse);
            final var messageResponseJson =
                    ProtocolFactory.wrapAsJsonifiableAdaptable(messageResponseAdaptable).toJson();
            httpPushFactory = mockHttpPushFactory(contentType, httpStatus, messageResponseJson.toString());

            final var target = ConnectivityModelFactory.newTargetBuilder()
                    .address(getOutboundAddress())
                    .originalAddress(getOutboundAddress())
                    .authorizationContext(TestConstants.Authorization.AUTHORIZATION_CONTEXT)
                    .headerMapping(TestConstants.HEADER_MAPPING)
                    .issuedAcknowledgementLabel(DittoAcknowledgementLabel.LIVE_RESPONSE)
                    .topics(Topic.LIVE_MESSAGES)
                    .build();

            final var props = getPublisherActorProps();
            final var publisherActor = childActorOf(props);
            publisherCreated(this, publisherActor);

            final var messageDirection = MessageDirection.FROM;
            final Message<?> message = Message.newBuilder(
                    MessageHeaders.newBuilder(messageDirection, TestConstants.Things.THING_ID, messageSubject)
                            .build()
            ).build();
            final var dittoHeaders = DittoHeaders.newBuilder()
                    .correlationId(TestConstants.CORRELATION_ID)
                    .putHeader("device_id", "ditto:thing")
                    .acknowledgementRequest(AcknowledgementRequest.of(DittoAcknowledgementLabel.LIVE_RESPONSE))
                    .putHeader(DittoHeaderDefinition.DITTO_ACKREGATOR_ADDRESS.getKey(), getRef().path().toSerializationFormat())
                    .build();
            final Signal<?> source = SendThingMessage.of(TestConstants.Things.THING_ID, message, dittoHeaders);
            final var outboundSignal =
                    OutboundSignalFactory.newOutboundSignal(source, Collections.singletonList(target));
            final var externalMessage =
                    ExternalMessageFactory.newExternalMessageBuilder(Collections.emptyMap())
                            .withText("payload")
                            .build();
            final var adaptable = DittoProtocolAdapter.newInstance().toAdaptable(source);
            final var mapped =
                    OutboundSignalFactory.newMappedOutboundSignal(outboundSignal, adaptable, externalMessage);

            publisherActor.tell(
                    OutboundSignalFactory.newMultiMappedOutboundSignal(Collections.singletonList(mapped), getRef()),
                    getRef());

            final var acknowledgements = expectMsgClass(Acknowledgements.class);
            assertThat((CharSequence) acknowledgements.getEntityId()).isEqualTo(TestConstants.Things.THING_ID);
            assertThat(acknowledgements.getHttpStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(acknowledgements.getDittoHeaders().getCorrelationId())
                    .contains(TestConstants.CORRELATION_ID);
            assertThat(acknowledgements.getSize()).isOne();
            assertThat(acknowledgements.getAcknowledgement(DittoAcknowledgementLabel.LIVE_RESPONSE))
                    .hasValueSatisfying(ack -> assertThat(ack.toJsonString())
                            .contains("Entity ID of live response <namespace:wrongthing> differs from entity ID of" +
                                    " command <ditto:thing>."));
        }};
    }

    @Test
    public void sendingWrongResponseTypeDoesNotWork() {
        new TestKit(actorSystem) {{
            final var customContentType = "application/vnd.org.eclipse.ditto.foobar+json";
            final var messageSubject = "please-respond";
            final var contentType = DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE;
            final var httpStatus = HttpStatus.IM_A_TEAPOT;
            final var messageHeaders =
                    MessageHeaders.newBuilder(MessageDirection.FROM, TestConstants.Things.THING_ID, messageSubject)
                            .contentType(customContentType)
                            .correlationId(TestConstants.CORRELATION_ID)
                            .putHeader(CUSTOM_HEADER_NAME, CUSTOM_HEADER_VALUE)
                            .featureId("wrongId")
                            .build();
            final var jsonResponse = JsonFactory.readFrom("{ \"foo\": true }");
            final var response =
                    Message.<JsonValue>newBuilder(messageHeaders).payload(jsonResponse).build();
            final var sendMessageResponse = SendFeatureMessageResponse.of(
                    TestConstants.Things.THING_ID,
                    "wrongId",
                    response,
                    httpStatus,
                    messageHeaders);
            final var messageResponseAdaptable =
                    DittoProtocolAdapter.newInstance().toAdaptable(sendMessageResponse);
            final var messageResponseJson =
                    ProtocolFactory.wrapAsJsonifiableAdaptable(messageResponseAdaptable).toJson();
            httpPushFactory = mockHttpPushFactory(contentType, httpStatus, messageResponseJson.toString());

            final var target = ConnectivityModelFactory.newTargetBuilder()
                    .address(getOutboundAddress())
                    .originalAddress(getOutboundAddress())
                    .authorizationContext(TestConstants.Authorization.AUTHORIZATION_CONTEXT)
                    .headerMapping(TestConstants.HEADER_MAPPING)
                    .issuedAcknowledgementLabel(DittoAcknowledgementLabel.LIVE_RESPONSE)
                    .topics(Topic.LIVE_MESSAGES)
                    .build();

            final var props = getPublisherActorProps();
            final var publisherActor = childActorOf(props);
            publisherCreated(this, publisherActor);

            final var messageDirection = MessageDirection.FROM;
            final Message<?> message = Message.newBuilder(
                    MessageHeaders.newBuilder(messageDirection, TestConstants.Things.THING_ID, messageSubject)
                            .build()
            ).build();
            final var dittoHeaders = DittoHeaders.newBuilder()
                    .correlationId(TestConstants.CORRELATION_ID)
                    .putHeader("device_id", "ditto:thing")
                    .acknowledgementRequest(AcknowledgementRequest.of(DittoAcknowledgementLabel.LIVE_RESPONSE))
                    .putHeader(DittoHeaderDefinition.DITTO_ACKREGATOR_ADDRESS.getKey(), getRef().path().toSerializationFormat())
                    .build();
            final Signal<?> source = SendThingMessage.of(TestConstants.Things.THING_ID, message, dittoHeaders);
            final var outboundSignal =
                    OutboundSignalFactory.newOutboundSignal(source, Collections.singletonList(target));
            final var externalMessage =
                    ExternalMessageFactory.newExternalMessageBuilder(Collections.emptyMap())
                            .withText("payload")
                            .build();
            final var adaptable = DittoProtocolAdapter.newInstance().toAdaptable(source);
            final var mapped =
                    OutboundSignalFactory.newMappedOutboundSignal(outboundSignal, adaptable, externalMessage);

            publisherActor.tell(
                    OutboundSignalFactory.newMultiMappedOutboundSignal(Collections.singletonList(mapped), getRef()),
                    getRef());

            final var acknowledgements = expectMsgClass(Acknowledgements.class);
            assertThat((CharSequence) acknowledgements.getEntityId()).isEqualTo(TestConstants.Things.THING_ID);
            assertThat(acknowledgements.getHttpStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(acknowledgements.getDittoHeaders().getCorrelationId())
                    .contains(TestConstants.CORRELATION_ID);
            assertThat(acknowledgements.getSize()).isOne();
            assertThat(acknowledgements.getAcknowledgement(DittoAcknowledgementLabel.LIVE_RESPONSE)).hasValueSatisfying(
                    ack -> assertThat(ack.toJsonString())
                            .contains("Type of live response <messages.responses:featureResponseMessage> is" +
                                    " not related to type of command <messages.commands:thingMessage>"));
        }};
    }

    @Override
    protected void verifyPublishedMessageToReplyTarget() throws Exception {
        // the Test: testPublishResponseToReplyTarget doesn't make any sense for HttpPush, because it's not possible
        // to define httpPush sources, hence it's not possible to define reply-targets.
    }

    @Test
    public void testAzMonitorRequestSigning() throws Exception {
        new TestKit(actorSystem) {{
            // GIVEN: HTTP publisher actor configured to authenticate by HMAC request signing
            httpPushFactory = mockHttpPushFactory("none", HttpStatus.OK, "");
            final var target = ConnectivityModelFactory.newTargetBuilder()
                    .address("POST:/api/logs?api-version=2016-04-01")
                    .authorizationContext(TestConstants.Authorization.AUTHORIZATION_CONTEXT)
                    .topics(Topic.LIVE_MESSAGES)
                    .build();

            final var hmacCredentials = HmacCredentials.of("az-monitor-2016-04-01", JsonObject.newBuilder()
                    .set("workspaceId", "xxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx")
                    .set("sharedKey", "SGFsbG8gV2VsdCEgSXN0IGRhcyBhbG")
                    .build());

            final var connection = TestConstants.createConnection()
                    .toBuilder()
                    .credentials(hmacCredentials)
                    .build();
            final var props = HttpPublisherActor.props(connection,
                    httpPushFactory,
                    mock(ConnectivityStatusResolver.class),
                    ConnectivityConfig.of(actorSystem.settings().config()));
            final var publisherActor = childActorOf(props);
            publisherCreated(this, publisherActor);

            // WHEN: HTTP publisher sends an HTTP request
            final Message<?> message = Message.newBuilder(
                    MessageHeaders.newBuilder(MessageDirection.FROM, TestConstants.Things.THING_ID, "please-respond")
                            .build()
            ).build();
            final Signal<?> source = SendThingMessage.of(TestConstants.Things.THING_ID, message, DittoHeaders.empty());
            final var outboundSignal =
                    OutboundSignalFactory.newOutboundSignal(source, Collections.singletonList(target));
            final var externalMessage =
                    ExternalMessageFactory.newExternalMessageBuilder(Collections.emptyMap())
                            .withText("payload")
                            .build();
            final var adaptable = DittoProtocolAdapter.newInstance().toAdaptable(source);
            final var mapped =
                    OutboundSignalFactory.newMappedOutboundSignal(outboundSignal, adaptable, externalMessage);

            publisherActor.tell(
                    OutboundSignalFactory.newMultiMappedOutboundSignal(Collections.singletonList(mapped), getRef()),
                    getRef());

            // THEN: The request is signed by the configured request signing process.
            final var signedRequest = received.take();
            final var unsignedRequest = signedRequest.withHeaders(List.of());

            final var xMsDate = ZonedDateTime.parse(signedRequest.getHeader("x-ms-date").orElseThrow().value(),
                    AzMonitorRequestSigning.X_MS_DATE_FORMAT).toInstant();

            final var expectedSignedRequest =
                    new AzMonitorRequestSigningFactory().create(actorSystem, hmacCredentials)
                            .sign(unsignedRequest, xMsDate)
                            .runWith(Sink.head(), actorSystem)
                            .toCompletableFuture()
                            .join();

            assertThat(signedRequest).isEqualTo(expectedSignedRequest);
        }};
    }

    @Test
    @SuppressWarnings("secrets:S6290")
    public void testAwsRequestSigning() throws Exception {
        new TestKit(actorSystem) {{
            // GIVEN: HTTP publisher actor configured to authenticate by HMAC request signing
            httpPushFactory = mockHttpPushFactory("none", HttpStatus.OK, "");
            final var target = ConnectivityModelFactory.newTargetBuilder()
                    .address("POST:/api/logs?api-version=2016-04-01")
                    .authorizationContext(TestConstants.Authorization.AUTHORIZATION_CONTEXT)
                    .topics(Topic.LIVE_MESSAGES)
                    .build();

            final var hmacCredentials = HmacCredentials.of("aws4-hmac-sha256", JsonObject.newBuilder()
                    .set("region", "us-east-1")
                    .set("service", "iam")
                    .set("accessKey", "MyAwesomeAccessKey")
                    .set("secretKey", "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY")
                    .set("doubleEncode", false)
                    .set("canonicalHeaders", JsonArray.newBuilder().add("x-amz-date", "host").build())
                    .build());

            final var connection = TestConstants.createConnection()
                    .toBuilder()
                    .credentials(hmacCredentials)
                    .build();
            final var props = HttpPublisherActor.props(connection,
                    httpPushFactory,
                    mock(ConnectivityStatusResolver.class),
                    ConnectivityConfig.of(actorSystem.settings().config()));
            final var publisherActor = childActorOf(props);
            publisherCreated(this, publisherActor);

            // WHEN: HTTP publisher sends an HTTP request
            final Message<?> message = Message.newBuilder(
                    MessageHeaders.newBuilder(MessageDirection.FROM, TestConstants.Things.THING_ID, "please-respond")
                            .build()
            ).build();
            final Signal<?> source = SendThingMessage.of(TestConstants.Things.THING_ID, message, DittoHeaders.empty());
            final var outboundSignal =
                    OutboundSignalFactory.newOutboundSignal(source, Collections.singletonList(target));
            final var externalMessage =
                    ExternalMessageFactory.newExternalMessageBuilder(Collections.emptyMap())
                            .withText("payload")
                            .build();
            final var adaptable = DittoProtocolAdapter.newInstance().toAdaptable(source);
            final var mapped =
                    OutboundSignalFactory.newMappedOutboundSignal(outboundSignal, adaptable, externalMessage);

            publisherActor.tell(
                    OutboundSignalFactory.newMultiMappedOutboundSignal(Collections.singletonList(mapped), getRef()),
                    getRef());

            // THEN: The request is signed by the configured request signing process.
            final var signedRequest = received.take();
            final var unsignedRequest = signedRequest.withHeaders(List.of());

            final var xAmzDate = ZonedDateTime.parse(signedRequest.getHeader("x-amz-date").orElseThrow().value(),
                    AwsRequestSigning.X_AMZ_DATE_FORMATTER).toInstant();

            final var expectedSignedRequest =
                    new AwsRequestSigningFactory().create(actorSystem, hmacCredentials)
                            .sign(unsignedRequest, xAmzDate)
                            .runWith(Sink.head(), actorSystem)
                            .toCompletableFuture()
                            .join();

            assertThat(signedRequest).isEqualTo(expectedSignedRequest);
        }};
    }

    @Test
    public void testReservedHeaders() throws Exception {
        // GIVEN: reserved headers are set
        final var reservedHeaders = Map.of(
                "http.query", "a=b&c=d&e=f",
                "http.path", "my/awesome/path"
        );

        // WHEN: publisher actor is asked to publish a message with reserved headers
        final var request = publishMessageWithHeaders(reservedHeaders);

        // THEN: reserved headers do not appear as HTTP headers
        assertThat(request.getHeader("http.query")).isEmpty();
        assertThat(request.getHeader("http.path")).isEmpty();

        // THEN: reserved headers are evaluated
        assertThat(request.getUri().queryString(StandardCharsets.UTF_8)).contains("a=b&c=d&e=f");
        assertThat(request.getUri().getPathString()).isEqualTo("/my/awesome/path");
    }

    @Test
    public void testHttpQueryReservedHeaderWithLeadingSlash() throws Exception {
        // GIVEN: reserved headers are set
        final var reservedHeaders = Map.of(
                "http.query", "a=b&c=d&e=f"
        );

        // WHEN: publisher actor is asked to publish a message with reserved headers
        final var request = publishMessageWithHeaders(reservedHeaders);

        // THEN: reserved headers do not appear as HTTP headers
        assertThat(request.getHeader("http.query")).isEmpty();

        // THEN: reserved headers are evaluated
        assertThat(request.getUri().queryString(StandardCharsets.UTF_8)).contains("a=b&c=d&e=f");
    }

    @Test
    public void testHttpPathReservedHeaderWithLeadingSlash() throws Exception {
        // GIVEN: reserved headers are set
        final var reservedHeaders = Map.of(
                "http.path", "/my/awesome/path"
        );

        // WHEN: publisher actor is asked to publish a message with reserved headers
        final var request = publishMessageWithHeaders(reservedHeaders);

        // THEN: reserved headers do not appear as HTTP headers
        assertThat(request.getHeader("http.path")).isEmpty();

        // THEN: reserved headers are evaluated
        assertThat(request.getUri().getPathString()).isEqualTo("/my/awesome/path");
    }

    @Test
    public void testOmitRequestBody() throws Exception {
        testOmitRequestBody(HttpMethods.GET, Map.of(), true);
        testOmitRequestBody(HttpMethods.GET, Map.of(OMIT_REQUEST_BODY, "GET"), true);
        testOmitRequestBody(HttpMethods.GET, Map.of(OMIT_REQUEST_BODY, "POST"), false);
        testOmitRequestBody(HttpMethods.GET, Map.of(OMIT_REQUEST_BODY, ""), false);
        testOmitRequestBody(HttpMethods.DELETE, Map.of(), true);
        testOmitRequestBody(HttpMethods.DELETE, Map.of(OMIT_REQUEST_BODY, "GET,DELETE"), true);
        testOmitRequestBody(HttpMethods.DELETE, Map.of(OMIT_REQUEST_BODY, "GET"), false);
        testOmitRequestBody(HttpMethods.DELETE, Map.of(OMIT_REQUEST_BODY, ""), false);
    }

    private void testOmitRequestBody(final HttpMethod method, final Map<String, String> specificConfig,
            final boolean expectEmptyBody) throws Exception {
        new TestKit(actorSystem) {{

            final TestProbe probe = new TestProbe(actorSystem);
            setupMocks(probe);
            final Target testTarget =
                    ConnectivityModelFactory.newTargetBuilder(createTestTarget())
                            .address(method.name() + ":/path")
                            .build();
            final OutboundSignal.MultiMapped multiMapped =
                    OutboundSignalFactory.newMultiMappedOutboundSignal(List.of(getMockOutboundSignal(testTarget)),
                            getRef());

            final Connection connection =
                    TestConstants.createConnection().toBuilder().specificConfig(specificConfig).build();
            final Props props = HttpPublisherActor.props(connection,
                    httpPushFactory,
                    mock(ConnectivityStatusResolver.class),
                    ConnectivityConfig.of(actorSystem.settings().config()));
            final ActorRef publisherActor = childActorOf(props);

            publisherCreated(this, publisherActor);

            publisherActor.tell(multiMapped, getRef());

            final HttpRequest request = received.take();
            assertThat(received).isEmpty();
            assertThat(request.method()).isEqualTo(method);
            if (expectEmptyBody) {
                assertThat(request.entity().isKnownEmpty()).isTrue();
            } else {
                final HttpEntity.Strict entity = request.entity()
                        .toStrict(60_000L, SystemMaterializer.get(actorSystem).materializer())
                        .toCompletableFuture()
                        .join();
                assertThat(entity.getData().utf8String()).isEqualTo("payload");
                if (!entity.getContentType().toString().equals(DittoConstants.DITTO_PROTOCOL_CONTENT_TYPE)) {
                    // Ditto protocol content type is parsed as binary for some reason
                    assertThat(entity.getContentType().binary()).isFalse();
                }
            }
        }};

    }


    private HttpRequest publishMessageWithHeaders(final Map<String, String> headers) throws InterruptedException {
        final var published = new Container<HttpRequest>();
        new TestKit(actorSystem) {{

            // WHEN: publisher actor is asked to publish a message with reserved headers
            final var probe = new TestProbe(actorSystem);
            setupMocks(probe);
            final var target = decorateTarget(createTestTarget());
            final Message<?> message = Message.newBuilder(
                    MessageHeaders.newBuilder(MessageDirection.FROM, TestConstants.Things.THING_ID,
                                    "please-respond")
                            .build()
            ).build();
            final Signal<?> source =
                    SendThingMessage.of(TestConstants.Things.THING_ID, message, DittoHeaders.empty());
            final var outboundSignal =
                    OutboundSignalFactory.newOutboundSignal(source, Collections.singletonList(target));
            final var externalMessage =
                    ExternalMessageFactory.newExternalMessageBuilder(headers)
                            .withText("payload")
                            .build();
            final var adaptable = DittoProtocolAdapter.newInstance().toAdaptable(source);
            final var mapped =
                    OutboundSignalFactory.newMappedOutboundSignal(outboundSignal, adaptable, externalMessage);
            final var multiMapped =
                    OutboundSignalFactory.newMultiMappedOutboundSignal(List.of(mapped), getRef());
            final var props = getPublisherActorProps();
            final var publisherActor = childActorOf(props);
            publisherCreated(this, publisherActor);
            publisherActor.tell(multiMapped, getRef());

            // THEN: reserved headers do not appear as HTTP headers
            published.setValue(received.take());
        }};

        return published.getValue();
    }

    private static class Container<T> {

        private T value;

        private void setValue(final T value) {
            this.value = value;
        }

        private T getValue() {
            return value;
        }

    }

    private OutboundSignal.MultiMapped newMultiMappedWithContentType(final Target target, final ActorRef sender) {
        return OutboundSignalFactory.newMultiMappedOutboundSignal(
                List.of(getMockOutboundSignal(target,
                        "requested-acks", JsonArray.of(JsonValue.of("please-verify")).toString(),
                        DittoHeaderDefinition.DITTO_ACKREGATOR_ADDRESS.getKey(), sender.path().toSerializationFormat()))
                , sender);
    }

    private HttpPushFactory mockHttpPushFactory(final String contentType, final HttpStatus httpStatus,
            final String body) {

        return new DummyHttpPushFactory("8.8.4.4", request -> {
            received.offer(request);
            return HttpResponse.create()
                    .withStatus(httpStatus.getCode())
                    .addHeader(HttpHeader.parse(CUSTOM_HEADER_NAME, CUSTOM_HEADER_VALUE))
                    .withEntity(new akka.http.scaladsl.model.HttpEntity.Strict(
                            (akka.http.scaladsl.model.ContentType) ContentTypes.parse(contentType),
                            ByteString.fromString(body)
                    ));
        });
    }

    private static final class DummyHttpPushFactory implements HttpPushFactory {

        private final String hostname;
        private final Function<HttpRequest, HttpResponse> mapper;

        private DummyHttpPushFactory(final String hostname, final Function<HttpRequest, HttpResponse> mapper) {
            this.hostname = hostname;
            this.mapper = mapper;
        }

        @Override
        public HttpRequest newRequest(final HttpPublishTarget httpPublishTarget) {
            final var separator = httpPublishTarget.getPathWithQuery().startsWith("/") ? "" : "/";
            final var uri =
                    Uri.create("http://" + hostname + ":12345" + separator + httpPublishTarget.getPathWithQuery());

            return HttpRequest.create().withMethod(httpPublishTarget.getMethod()).withUri(uri);
        }

        @Override
        public Flow<Pair<HttpRequest, HttpPushContext>, Pair<Try<HttpResponse>, HttpPushContext>, ?> createFlow(
                final ActorSystem system,
                final LoggingAdapter log, final Duration requestTimeout, @Nullable final PreparedTimer timer,
                @Nullable final BiConsumer<Duration, ConnectionMonitor.InfoProvider> consumer) {

            return Flow.<Pair<HttpRequest, HttpPushContext>>create()
                    .map(pair -> Pair.create(Try.apply(() -> mapper.apply(pair.first())), pair.second()));
        }

    }

}
