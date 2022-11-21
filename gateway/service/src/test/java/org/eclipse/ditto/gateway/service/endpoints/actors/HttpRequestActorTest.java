/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.gateway.service.endpoints.actors;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.assertj.core.api.AutoCloseableSoftAssertions;
import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.acks.AcknowledgementRequest;
import org.eclipse.ditto.base.model.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgement;
import org.eclipse.ditto.connectivity.api.commands.sudo.SudoAddConnectionLogEntry;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.LogLevel;
import org.eclipse.ditto.connectivity.model.LogType;
import org.eclipse.ditto.edge.service.acknowledgements.AcknowledgementAggregatorActorStarter;
import org.eclipse.ditto.gateway.service.endpoints.routes.whoami.Whoami;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.messages.model.Message;
import org.eclipse.ditto.messages.model.MessageDirection;
import org.eclipse.ditto.messages.model.MessageHeaders;
import org.eclipse.ditto.messages.model.signals.commands.SendThingMessageResponse;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyAttribute;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyAttributeResponse;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThingResponse;
import org.junit.Rule;
import org.junit.Test;

import akka.Done;
import akka.actor.ActorRef;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpEntities;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.ResponseEntity;
import akka.http.javadsl.model.StatusCodes;
import akka.testkit.javadsl.TestKit;
import akka.util.ByteString;

/**
 * Unit test for {@link HttpRequestActor}.
 */
public final class HttpRequestActorTest extends AbstractHttpRequestActorTest {

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @Test
    public void handlesWhoamiCommand() throws ExecutionException, InterruptedException {
        final var dittoHeaders = createAuthorizedHeaders();
        final var whoami = Whoami.of(dittoHeaders);
        final var expectedResponse = createExpectedWhoamiResponse(whoami);
        final var request = HttpRequest.GET("/whoami");
        final var responseFuture = new CompletableFuture<HttpResponse>();
        final var proxyActorTestProbe = ACTOR_SYSTEM_RESOURCE.newTestProbe();
        final var underTest = createHttpRequestActor(proxyActorTestProbe.ref(), request, responseFuture);

        underTest.tell(Whoami.of(dittoHeaders), ActorRef.noSender());

        assertThat(responseFuture.get()).isEqualTo(expectedResponse);
    }

    @Test
    public void generateLocationHeaderInTwinPersistedAcknowledgementWithCreatedStatusCode() throws Exception {
        final var thingId = ThingId.generateRandom();
        final var attributeName = "foo";
        final var attributeValue = JsonValue.of("bar");
        final var attributePointer = JsonPointer.of(attributeName);
        final var customAckLabel = AcknowledgementLabel.of("custom-ack");

        final var dittoHeaders = DittoHeaders.newBuilder(createAuthorizedHeaders())
                .responseRequired(true)
                .acknowledgementRequest(AcknowledgementRequest.of(DittoAcknowledgementLabel.TWIN_PERSISTED),
                        AcknowledgementRequest.of(customAckLabel))
                .build();

        final var command = ModifyAttribute.of(thingId, attributePointer, attributeValue, dittoHeaders);
        final var createAttributeResponse =
                ModifyAttributeResponse.created(thingId, attributePointer, attributeValue, dittoHeaders);
        final var customAcknowledgement =
                Acknowledgement.of(customAckLabel, thingId, HttpStatus.FORBIDDEN, DittoHeaders.empty());

        final var proxyActorProbe = ACTOR_SYSTEM_RESOURCE.newTestProbe();

        final var request = HttpRequest.PUT("/api/2/things/" + thingId + "/attributes/" + attributeName);
        final var responseFuture = new CompletableFuture<HttpResponse>();

        final var underTest = createHttpRequestActor(proxyActorProbe.ref(), request, responseFuture);
        underTest.tell(command, ActorRef.noSender());

        ModifyAttribute receivedModifyAttribute = proxyActorProbe.expectMsgClass(ModifyAttribute.class);
        final DittoHeaders receivedHeaders = receivedModifyAttribute.getDittoHeaders();
        if (AcknowledgementAggregatorActorStarter.shouldStartForIncoming(command)) {
            assertThat(receivedHeaders.get(DittoHeaderDefinition.DITTO_ACKREGATOR_ADDRESS.getKey()))
                    .startsWith("akka:");
            receivedModifyAttribute = receivedModifyAttribute.setDittoHeaders(
                    receivedHeaders.toBuilder()
                            .removeHeader(DittoHeaderDefinition.DITTO_ACKREGATOR_ADDRESS.getKey())
                            .build()
            );
        }
        assertThat(receivedModifyAttribute).isEqualTo(command);

        proxyActorProbe.reply(createAttributeResponse);
        proxyActorProbe.reply(customAcknowledgement);

        final var response = responseFuture.get();
        assertThat(response.status()).isEqualTo(StatusCodes.FAILED_DEPENDENCY);

        final var responseBody = JsonObject.of(
                response.entity()
                        .toStrict(1000L, ACTOR_SYSTEM_RESOURCE.getMaterializer())
                        .toCompletableFuture()
                        .join()
                        .getData()
                        .utf8String()
        );
        assertThat(responseBody.getValue("twin-persisted/status"))
                .contains(JsonValue.of(HttpStatus.CREATED.getCode()));
        assertThat(responseBody.getValue("twin-persisted/headers/location"))
                .contains(JsonValue.of("/api/2/things/" + thingId + "/attributes/" + attributeName));
    }

    @Test
    public void handlesThingModifyCommand() throws ExecutionException, InterruptedException {
        final var thingId = ThingId.generateRandom();
        final var attributeName = "foo";
        final var attributePointer = JsonPointer.of(attributeName);

        final var dittoHeaders = createAuthorizedHeaders();
        final var expectedHeaders = DittoHeaders.newBuilder(dittoHeaders)
                .responseRequired(true)
                .acknowledgementRequest(AcknowledgementRequest.of(DittoAcknowledgementLabel.TWIN_PERSISTED))
                .build();

        testThingModifyCommand(thingId,
                attributePointer,
                dittoHeaders,
                expectedHeaders,
                ModifyAttributeResponse.modified(thingId, attributePointer, expectedHeaders),
                StatusCodes.NO_CONTENT,
                setResponseRequiredToFalse(expectedHeaders));
    }

    private static DittoHeaders setResponseRequiredToFalse(final DittoHeaders dittoHeaders) {
        return DittoHeaders.newBuilder(dittoHeaders).responseRequired(false).build();
    }

    @Test
    public void handlesThingModifyCommandLive() throws ExecutionException, InterruptedException {
        final var thingId = ThingId.generateRandom();
        final var attributeName = "foo";
        final var attributePointer = JsonPointer.of(attributeName);

        final var dittoHeaders = DittoHeaders.newBuilder(createAuthorizedHeaders())
                .channel("live")
                .build();
        final var expectedHeaders = DittoHeaders.newBuilder(dittoHeaders)
                .responseRequired(true)
                .channel("live")
                .acknowledgementRequest(AcknowledgementRequest.of(DittoAcknowledgementLabel.LIVE_RESPONSE))
                .build();

        testThingModifyCommand(thingId,
                attributePointer,
                dittoHeaders,
                expectedHeaders,
                ModifyAttributeResponse.modified(thingId, attributePointer, expectedHeaders),
                StatusCodes.NO_CONTENT,
                setResponseRequiredToFalse(expectedHeaders));
    }

    @Test
    public void liveResponseHeadersDoNotCauseSpurious202Accepted() throws Exception {
        final var thingId = ThingId.generateRandom();
        final var attributeName = "foo";
        final var attributePointer = JsonPointer.of(attributeName);

        final var dittoHeaders = DittoHeaders.newBuilder(createAuthorizedHeaders())
                .channel("live")
                .build();
        final var expectedHeaders = DittoHeaders.newBuilder(dittoHeaders)
                .responseRequired(true)
                .channel("live")
                .acknowledgementRequest(AcknowledgementRequest.of(DittoAcknowledgementLabel.LIVE_RESPONSE))
                .build();

        final var probeResponse = ModifyAttributeResponse.modified(thingId,
                attributePointer,
                setResponseRequiredToFalse(expectedHeaders));

        testThingModifyCommand(thingId,
                attributePointer,
                dittoHeaders,
                expectedHeaders,
                probeResponse,
                StatusCodes.NO_CONTENT,
                null);
    }

    @Test
    public void handlesThingModifyCommandLiveNoResponseRequired() throws ExecutionException, InterruptedException {
        final var attributeName = "foo";

        final var dittoHeaders = DittoHeaders.newBuilder(createAuthorizedHeaders())
                .responseRequired(false)
                .channel("live")
                .build();

        testThingModifyCommand(ThingId.generateRandom(),
                JsonPointer.of(attributeName),
                dittoHeaders,
                DittoHeaders.newBuilder(dittoHeaders).acknowledgementRequests(Collections.emptyList()).build(),
                null,
                StatusCodes.ACCEPTED,
                null);
    }

    @Test
    public void handlesThingModifyCommandLiveNoResponseRequiredAndLiveResponseAckRequest()
            throws ExecutionException, InterruptedException {

        final var thingId = ThingId.generateRandom();
        final var attributeName = "foo";
        final var attributePointer = JsonPointer.of(attributeName);

        final var dittoHeaders = DittoHeaders.newBuilder(createAuthorizedHeaders())
                .responseRequired(false)
                .channel("live")
                .acknowledgementRequest(AcknowledgementRequest.of(DittoAcknowledgementLabel.LIVE_RESPONSE))
                .build();

        // headers shall be same:
        final var expectedHeaders = DittoHeaders.newBuilder(dittoHeaders)
                .acknowledgementRequests(Collections.emptyList())
                .build();

        testThingModifyCommand(thingId,
                attributePointer,
                dittoHeaders,
                expectedHeaders,
                ModifyAttributeResponse.modified(thingId, attributePointer, expectedHeaders),
                StatusCodes.ACCEPTED,
                null);
    }

    @Test
    public void handlesMessageCommand() throws ExecutionException, InterruptedException {
        final var thingId = ThingId.generateRandom();
        final var messageSubject = "sayPing";

        final var dittoHeaders = DittoHeaders.newBuilder(createAuthorizedHeaders())
                .responseRequired(true)
                .channel("live")
                .build();

        final var expectedHeaders = DittoHeaders.newBuilder(dittoHeaders)
                .responseRequired(true)
                .channel("live")
                .acknowledgementRequest(AcknowledgementRequest.of(DittoAcknowledgementLabel.LIVE_RESPONSE))
                .build();

        final var contentType = "application/json";
        final var responsePayload = JsonValue.of("poooong");
        final var probeResponse =
                buildSendThingMessageResponse(thingId, messageSubject, expectedHeaders, contentType, responsePayload);

        final var expectedHttpStatus = StatusCodes.IMUSED;
        final ResponseEntity expectedHttpResponseEntity = HttpEntities.create(ContentTypes.parse(contentType),
                ByteString.ByteStrings.fromString(responsePayload.toString(), Charset.defaultCharset()));

        testMessageCommand(thingId,
                messageSubject,
                dittoHeaders,
                expectedHeaders,
                probeResponse,
                expectedHttpStatus,
                setResponseRequiredToFalse(expectedHeaders),
                expectedHttpResponseEntity);
    }

    @Test
    public void forwardMessageCommandResponseWithInconsistentContentTypes() throws Exception {
        final var thingId = ThingId.generateRandom();
        final var messageSubject = "sayPing";

        // Scenario:
        // message command and response sent with content-type "text/plain",
        // but the message in the message command response has content-type "null".
        final var dittoHeaders = DittoHeaders.newBuilder(createAuthorizedHeaders())
                .responseRequired(true)
                .channel("live")
                .contentType("text/plain")
                .build();

        final var expectedHeaders = DittoHeaders.newBuilder(dittoHeaders)
                .responseRequired(true)
                .channel("live")
                .contentType("text/plain")
                .acknowledgementRequest(AcknowledgementRequest.of(DittoAcknowledgementLabel.LIVE_RESPONSE))
                .build();

        final var messageHeaders = MessageHeaders.newBuilder(MessageDirection.TO, thingId, messageSubject)
                .httpStatus(HttpStatus.IM_USED)
                .contentType("null")
                .build();
        final var responsePayload = "poooong";
        final var responseBytePayload = ByteBuffer.wrap(responsePayload.getBytes());
        final var responseMessage = Message.<ByteBuffer>newBuilder(messageHeaders)
                .payload(responseBytePayload)
                .rawPayload(responseBytePayload)
                .build();
        final var probeResponse =
                SendThingMessageResponse.of(thingId, responseMessage, HttpStatus.IM_USED, expectedHeaders);

        final var httpResponse =
                testMessageCommand(thingId, messageSubject, dittoHeaders, expectedHeaders, probeResponse);

        assertThat(httpResponse.status().intValue()).isEqualTo(HttpStatus.IM_USED.getCode());

        final var responseBody = httpResponse.entity()
                .toStrict(10000, ACTOR_SYSTEM_RESOURCE.getMaterializer())
                .toCompletableFuture()
                .join()
                .getData();

        assertThat(responseBody.utf8String()).isEqualTo(responsePayload);
        assertThat(httpResponse.entity().getContentType()).isEqualTo(ContentTypes.APPLICATION_OCTET_STREAM);
    }

    @Test
    public void handlesMessageCommandNoResponseRequired() throws ExecutionException, InterruptedException {
        final var thingId = ThingId.generateRandom();
        final var messageSubject = "sayPing";

        final var dittoHeaders = DittoHeaders.newBuilder(createAuthorizedHeaders())
                .channel("live")
                .responseRequired(false)
                .acknowledgementRequest(AcknowledgementRequest.of(DittoAcknowledgementLabel.LIVE_RESPONSE))
                .build();

        // headers shall be same:
        final var expectedHeaders = DittoHeaders.newBuilder(dittoHeaders)
                .acknowledgementRequests(Collections.emptyList())
                .build();

        final var probeResponse = buildSendThingMessageResponse(thingId,
                messageSubject,
                expectedHeaders,
                "application/json",
                JsonValue.of("poooong"));

        testMessageCommand(thingId,
                messageSubject,
                dittoHeaders,
                expectedHeaders,
                probeResponse,
                StatusCodes.ACCEPTED,
                null,
                null);
    }

    @Test
    public void handlesMessageCommandNoResponseRequiredAndLiveResponseAckRequest()
            throws ExecutionException, InterruptedException {

        final var dittoHeaders = DittoHeaders.newBuilder(createAuthorizedHeaders())
                .channel("live")
                .responseRequired(false)
                .build();

        testMessageCommand(ThingId.generateRandom(),
                "sayPing",
                dittoHeaders,
                DittoHeaders.newBuilder(dittoHeaders).acknowledgementRequests(Collections.emptyList()).build(),
                null,
                StatusCodes.ACCEPTED,
                null,
                null);
    }

    @Test
    public void handlesLiveCommandWithoutAckRequestWithValidResponse() throws ExecutionException, InterruptedException {
        final var modifyAttribute = ModifyAttribute.of(ThingId.generateRandom(),
                JsonPointer.of("manufacturer"),
                JsonValue.of("ACME"),
                DittoHeaders.newBuilder(createAuthorizedHeaders())
                        .channel("live")
                        .build());
        final var proxyActorTestProbe = ACTOR_SYSTEM_RESOURCE.newTestProbe();
        final var httpResponseFuture = new CompletableFuture<HttpResponse>();
        final var underTest = ACTOR_SYSTEM_RESOURCE.newActor(HttpRequestActor.props(proxyActorTestProbe.ref(),
                HEADER_TRANSLATOR,
                getHttpRequest(modifyAttribute),
                httpResponseFuture,
                gatewayConfig.getHttpConfig(),
                gatewayConfig.getCommandConfig()));
        final var testKit = ACTOR_SYSTEM_RESOURCE.newTestKit();

        underTest.tell(modifyAttribute, testKit.getRef());

        final var proxiedCommand = proxyActorTestProbe.expectMsgClass(modifyAttribute.getClass());

        final var modifyAttributeResponse = ModifyAttributeResponse.modified(modifyAttribute.getEntityId(),
                modifyAttribute.getAttributePointer(),
                proxiedCommand.getDittoHeaders());

        underTest.tell(modifyAttributeResponse, testKit.getRef());

        try (final var softly = new AutoCloseableSoftAssertions()) {
            softly.assertThat(httpResponseFuture).as("future is done").isDone();
            softly.assertThat(httpResponseFuture.get())
                    .as("HTTP response is expected")
                    .satisfies(httpResponse -> softly.assertThat(httpResponse.status())
                            .as("HTTP response status")
                            .isEqualTo(StatusCodes.get(modifyAttributeResponse.getHttpStatus().getCode())));
        }
    }

    @Test
    public void liveCommandWithoutAckRequestWithInvalidResponseTimesOut()
            throws ExecutionException, InterruptedException, TimeoutException {

        final var connectionId = ConnectionId.generateRandom();
        final var thingId = ThingId.generateRandom();
        final var correlationId = testNameCorrelationId.getCorrelationId();
        final var modifyAttribute = ModifyAttribute.of(thingId,
                JsonPointer.of("manufacturer"),
                JsonValue.of("ACME"),
                DittoHeaders.newBuilder(createAuthorizedHeaders())
                        .correlationId(correlationId)
                        .channel("live")
                        .responseRequired(true)
                        .timeout(Duration.ofSeconds(4L))
                        .build());
        final var proxyActorTestProbe = ACTOR_SYSTEM_RESOURCE.newTestProbe();
        final var httpResponseFuture = new CompletableFuture<HttpResponse>();
        final var httpConfig = gatewayConfig.getHttpConfig();
        final var underTest = ACTOR_SYSTEM_RESOURCE.newActor(HttpRequestActor.props(proxyActorTestProbe.ref(),
                HEADER_TRANSLATOR,
                getHttpRequest(modifyAttribute),
                httpResponseFuture,
                httpConfig,
                gatewayConfig.getCommandConfig()));
        final var commandHandler = ACTOR_SYSTEM_RESOURCE.newTestProbe();

        underTest.tell(modifyAttribute, commandHandler.ref());

        final var proxiedCommand = proxyActorTestProbe.expectMsgClass(modifyAttribute.getClass());
        final var acknowledgementAggregator = proxyActorTestProbe.sender();

        // Send invalid response.
        final var responseDittoHeaders = DittoHeaders.newBuilder(proxiedCommand.getDittoHeaders())
                .putHeader(DittoHeaderDefinition.CONNECTION_ID.getKey(), connectionId.toString())
                .build();
        acknowledgementAggregator.tell(
                RetrieveThingResponse.of(modifyAttribute.getEntityId(), JsonObject.empty(), responseDittoHeaders),
                commandHandler.ref()
        );

        commandHandler.expectNoMessage();

        // Assert expected log entry for invalid response.
        final var addConnectionLogEntry = proxyActorTestProbe.expectMsgClass(SudoAddConnectionLogEntry.class);
        softly.assertThat((Object) addConnectionLogEntry.getEntityId()).as("connection ID").isEqualTo(connectionId);
        softly.assertThat(addConnectionLogEntry.getLogEntry())
                .as("log entry")
                .satisfies(logEntry -> {
                    softly.assertThat(logEntry.getCorrelationId())
                            .as("correlation ID")
                            .isEqualTo(correlationId.toString());
                    softly.assertThat(logEntry.getLogLevel()).as("log level").isEqualTo(LogLevel.FAILURE);
                    softly.assertThat(logEntry.getLogType()).as("log type").isEqualTo(LogType.DROPPED);
                    softly.assertThat(logEntry.getEntityId()).as("entity ID").hasValue(thingId);
                    softly.assertThat(logEntry.getMessage()).as("message").isNotBlank();
                });

        final var futureCompletionTimeout = httpConfig.getRequestTimeout().plusSeconds(1L);
        final var httpResponse = httpResponseFuture.get(futureCompletionTimeout.toMillis(), TimeUnit.MILLISECONDS);
        softly.assertThat(httpResponse.status())
                .as("request eventually times out")
                .isEqualTo(StatusCodes.REQUEST_TIMEOUT);
    }

    @Test
    public void liveCommandWithInvalidResponseWithoutConnectionIdIsLoggedOnly()
            throws ExecutionException, InterruptedException, TimeoutException {

        final var thingId = ThingId.generateRandom();
        final var correlationId = testNameCorrelationId.getCorrelationId();
        final var modifyAttribute = ModifyAttribute.of(thingId,
                JsonPointer.of("manufacturer"),
                JsonValue.of("ACME"),
                DittoHeaders.newBuilder(createAuthorizedHeaders())
                        .correlationId(correlationId)
                        .channel("live")
                        .responseRequired(true)
                        .timeout(Duration.ofSeconds(4L))
                        .build());
        final var proxyActorTestProbe = ACTOR_SYSTEM_RESOURCE.newTestProbe();
        final var httpResponseFuture = new CompletableFuture<HttpResponse>();
        final var httpConfig = gatewayConfig.getHttpConfig();
        final var underTest = ACTOR_SYSTEM_RESOURCE.newActor(HttpRequestActor.props(proxyActorTestProbe.ref(),
                HEADER_TRANSLATOR,
                getHttpRequest(modifyAttribute),
                httpResponseFuture,
                httpConfig,
                gatewayConfig.getCommandConfig()));
        final var commandHandler = ACTOR_SYSTEM_RESOURCE.newTestProbe();

        underTest.tell(modifyAttribute, commandHandler.ref());

        final var proxiedCommand = proxyActorTestProbe.expectMsgClass(modifyAttribute.getClass());
        final var acknowledgementAggregator = proxyActorTestProbe.sender();

        // Send response with different ThingId (hence invalid) without connection ID.
        acknowledgementAggregator.tell(
                ModifyAttributeResponse.modified(ThingId.generateRandom(),
                        modifyAttribute.getAttributePointer(),
                        proxiedCommand.getDittoHeaders()),
                commandHandler.ref()
        );

        commandHandler.expectNoMessage();
        proxyActorTestProbe.expectNoMessage();

        final var futureCompletionTimeout = httpConfig.getRequestTimeout().plusSeconds(1L);
        final var httpResponse = httpResponseFuture.get(futureCompletionTimeout.toMillis(), TimeUnit.MILLISECONDS);
        softly.assertThat(httpResponse.status())
                .as("request eventually times out")
                .isEqualTo(StatusCodes.REQUEST_TIMEOUT);
    }

    @Test
    public void liveCommandWithoutAckRequestWithEventualValidResponseSucceeds()
            throws ExecutionException, InterruptedException, TimeoutException {

        final var connectionId = ConnectionId.generateRandom();
        final var thingId = ThingId.generateRandom();
        final var correlationId = testNameCorrelationId.getCorrelationId();
        final var modifyAttribute = ModifyAttribute.of(thingId,
                JsonPointer.of("manufacturer"),
                JsonValue.of("ACME"),
                DittoHeaders.newBuilder(createAuthorizedHeaders())
                        .channel("live")
                        .correlationId(correlationId)
                        .responseRequired(true)
                        .timeout(Duration.ofSeconds(5L))
                        .build());
        final var proxyActorTestProbe = ACTOR_SYSTEM_RESOURCE.newTestProbe();
        final var httpResponseFuture = new CompletableFuture<HttpResponse>();
        final var httpConfig = gatewayConfig.getHttpConfig();
        final var underTest = ACTOR_SYSTEM_RESOURCE.newActor(HttpRequestActor.props(proxyActorTestProbe.ref(),
                HEADER_TRANSLATOR,
                getHttpRequest(modifyAttribute),
                httpResponseFuture,
                httpConfig,
                gatewayConfig.getCommandConfig()));
        final var commandHandler = ACTOR_SYSTEM_RESOURCE.newTestProbe();

        underTest.tell(modifyAttribute, commandHandler.ref());

        final var proxiedCommand = proxyActorTestProbe.expectMsgClass(modifyAttribute.getClass());
        final var acknowledgementAggregator = proxyActorTestProbe.sender();

        // Send invalid response.
        final var responseDittoHeaders = DittoHeaders.newBuilder(proxiedCommand.getDittoHeaders())
                .putHeader(DittoHeaderDefinition.CONNECTION_ID.getKey(), connectionId.toString())
                .build();
        acknowledgementAggregator.tell(
                RetrieveThingResponse.of(modifyAttribute.getEntityId(), JsonObject.empty(), responseDittoHeaders),
                commandHandler.ref()
        );

        commandHandler.expectNoMessage();

        // Assert expected log entry for invalid response.
        final var addConnectionLogEntry = proxyActorTestProbe.expectMsgClass(SudoAddConnectionLogEntry.class);
        softly.assertThat((Object) addConnectionLogEntry.getEntityId()).as("connection ID").isEqualTo(connectionId);
        softly.assertThat(addConnectionLogEntry.getLogEntry())
                .as("log entry")
                .satisfies(logEntry -> {
                    softly.assertThat(logEntry.getCorrelationId())
                            .as("correlation ID")
                            .isEqualTo(correlationId.toString());
                    softly.assertThat(logEntry.getLogLevel()).as("log level").isEqualTo(LogLevel.FAILURE);
                    softly.assertThat(logEntry.getLogType()).as("log type").isEqualTo(LogType.DROPPED);
                    softly.assertThat(logEntry.getEntityId()).as("entity ID").hasValue(thingId);
                    softly.assertThat(logEntry.getMessage()).as("message").isNotBlank();
                });

        // Eventually send valid response.
        acknowledgementAggregator.tell(
                ModifyAttributeResponse.modified(proxiedCommand.getEntityId(),
                        proxiedCommand.getAttributePointer(),
                        proxiedCommand.getDittoHeaders()),
                commandHandler.ref()
        );

        final var futureCompletionTimeout = httpConfig.getRequestTimeout().plusSeconds(1L);
        final var httpResponse = httpResponseFuture.get(futureCompletionTimeout.toMillis(), TimeUnit.MILLISECONDS);
        softly.assertThat(httpResponse.status())
                .as("request eventually succeeds")
                .isEqualTo(StatusCodes.NO_CONTENT);
    }

    @Test
    public void actorShutsDownAfterServiceRequestDoneMessageWasReceived() {
        new TestKit(ACTOR_SYSTEM_RESOURCE.getActorSystem()) {{
                final var thingId = ThingId.generateRandom();
                final var attributeName = "foo";
                final var attributePointer = JsonPointer.of(attributeName);
                final long timeoutInSec = 2L;
                final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                        .timeout(Duration.ofSeconds(timeoutInSec))
                        .build();

                final var proxyActorProbe = ACTOR_SYSTEM_RESOURCE.newTestProbe();
                final var modifyAttribute =
                        ModifyAttribute.of(thingId, attributePointer, JsonValue.of("bar"), dittoHeaders);
                final var responseFuture = new CompletableFuture<HttpResponse>();
                final var underTest =
                        createHttpRequestActor(proxyActorProbe.ref(), getHttpRequest(modifyAttribute), responseFuture);
                watch(underTest);

                underTest.tell(modifyAttribute, ActorRef.noSender());
                proxyActorProbe.expectMsgClass(ModifyAttribute.class);

                underTest.tell(AbstractHttpRequestActor.Control.SERVICE_REQUESTS_DONE, getRef());
                expectMsgClass(Done.class);
                expectTerminated(underTest);
            }};
    }

}
