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
package org.eclipse.ditto.edge.service.acknowledgements;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.assertj.core.api.Assertions;
import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.acks.AcknowledgementRequest;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.common.ResponseType;
import org.eclipse.ditto.base.model.correlationid.TestNameCorrelationId;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.translator.HeaderTranslator;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgement;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgements;
import org.eclipse.ditto.base.model.signals.commands.exceptions.CommandTimeoutException;
import org.eclipse.ditto.edge.service.acknowledgements.config.DefaultAcknowledgementConfig;
import org.eclipse.ditto.edge.service.acknowledgements.things.ThingCommandResponseAcknowledgementProvider;
import org.eclipse.ditto.internal.models.signal.correlation.MatchingValidationResult;
import org.eclipse.ditto.internal.utils.akka.ActorSystemResource;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.ThingCommand;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteThing;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteThingResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.ThingModifyCommand;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.testkit.javadsl.TestKit;

/**
 * Tests {@link AcknowledgementAggregatorActor}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class AcknowledgementAggregatorActorTest {

    private static final ThingId THING_ID = ThingId.of("mynamespace:myname");

    private static HeaderTranslator headerTranslator;

    @Rule
    public final ActorSystemResource actorSystemResource = ActorSystemResource.newInstance();

    @Rule
    public final TestNameCorrelationId testNameCorrelationId = TestNameCorrelationId.newInstance();

    @Mock private Consumer<MatchingValidationResult.Failure> responseValidationFailureConsumer;

    @BeforeClass
    public static void beforeClass() {
        headerTranslator = HeaderTranslator.empty();
    }

    @Test
    public void returnSingleResponseForTwin() {

        // GIVEN
        final var testKit = actorSystemResource.newTestKit();
        final var command = DeleteThing.of(THING_ID, DittoHeaders.newBuilder()
                .correlationId(testNameCorrelationId.getCorrelationId())
                .acknowledgementRequest(AcknowledgementRequest.of(AcknowledgementLabel.of("twin-persisted")))
                .build());
        final var underTest = testKit.childActorOf(getAcknowledgementAggregatorProps(command, testKit));

        // WHEN
        final var response = DeleteThingResponse.of(command.getEntityId(), command.getDittoHeaders());
        underTest.tell(response, ActorRef.noSender());

        // THEN
        testKit.expectMsg(response);
    }

    @Test
    public void returnSingleResponseForLive() {

        // GIVEN
        final var testKit = actorSystemResource.newTestKit();
        final var command = DeleteThing.of(THING_ID, DittoHeaders.newBuilder()
                .correlationId(testNameCorrelationId.getCorrelationId())
                .acknowledgementRequest(AcknowledgementRequest.of(AcknowledgementLabel.of("live-response")))
                .channel("live")
                .build());
        final var underTest = testKit.childActorOf(getAcknowledgementAggregatorProps(command, testKit));

        // WHEN
        final var response = DeleteThingResponse.of(command.getEntityId(), command.getDittoHeaders());
        underTest.tell(response, ActorRef.noSender());

        // THEN
        testKit.expectMsg(response);
    }

    @Test
    public void returnErrorResponseForTwin() {

        // GIVEN
        final var testKit = actorSystemResource.newTestKit();
        final var command = DeleteThing.of(THING_ID, DittoHeaders.newBuilder()
                .correlationId(testNameCorrelationId.getCorrelationId())
                .acknowledgementRequest(AcknowledgementRequest.of(AcknowledgementLabel.of("twin-persisted")))
                .timeout(Duration.ofMillis(1L))
                .build());

        // WHEN
        testKit.childActorOf(getAcknowledgementAggregatorProps(command, testKit));

        // THEN
        final var commandTimeoutException = testKit.expectMsgClass(CommandTimeoutException.class);
        Assertions.assertThat(commandTimeoutException.getDittoHeaders().get(DittoHeaderDefinition.ENTITY_ID.getKey()))
                .isEqualTo(THING_ID.getEntityType() + ":" + THING_ID);
    }

    @Test
    public void returnErrorResponseForLive() {

        // GIVEN
        final var testKit = actorSystemResource.newTestKit();
        final var command = DeleteThing.of(THING_ID, DittoHeaders.newBuilder()
                .correlationId(testNameCorrelationId.getCorrelationId())
                .acknowledgementRequest(AcknowledgementRequest.of(AcknowledgementLabel.of("live-response")))
                .channel("live")
                .timeout(Duration.ofMillis(1L))
                .build());

        // WHEN
        testKit.childActorOf(getAcknowledgementAggregatorProps(command, testKit));

        // THEN
        final var commandTimeoutException = testKit.expectMsgClass(CommandTimeoutException.class);
        Assertions.assertThat(commandTimeoutException.getDittoHeaders().get(DittoHeaderDefinition.ENTITY_ID.getKey()))
                .isEqualTo(THING_ID.getEntityType() + ":" + THING_ID);
    }

    @Test
    public void keepCommandHeaders() {

        // GIVEN
        final var testKit = actorSystemResource.newTestKit();
        final var tag = "tag";
        final var correlationId = testNameCorrelationId.getCorrelationId();
        final var label1 = AcknowledgementLabel.of("ack1");
        final var label2 = AcknowledgementLabel.of("ack2");
        final ThingModifyCommand<?> command = DeleteThing.of(THING_ID, DittoHeaders.newBuilder()
                .correlationId(correlationId)
                .acknowledgementRequest(AcknowledgementRequest.of(label1), AcknowledgementRequest.of(label2))
                .putHeader(tag, DeleteThing.class.getSimpleName())
                .build());
        final var underTest = testKit.childActorOf(getAcknowledgementAggregatorProps(command, testKit));

        // WHEN
        final var ack1 = Acknowledgement.of(label1,
                THING_ID,
                HttpStatus.UNAUTHORIZED,
                DittoHeaders.newBuilder().correlationId(correlationId).putHeader(tag, label1.toString()).build());
        final var ack2 = Acknowledgement.of(label2,
                THING_ID,
                HttpStatus.PAYMENT_REQUIRED,
                DittoHeaders.newBuilder().correlationId(correlationId).putHeader(tag, label2.toString()).build());
        underTest.tell(ack1, ActorRef.noSender());
        underTest.tell(ack2, ActorRef.noSender());

        // THEN
        final var acks = testKit.expectMsgClass(Acknowledgements.class);
        Assertions.assertThat(acks.getDittoHeaders())
                .isEqualTo(command.getDittoHeaders().toBuilder()
                        .responseRequired(false)
                        .removeHeader(DittoHeaderDefinition.REQUESTED_ACKS.getKey())
                        .build());
        Assertions.assertThat(acks.getSize()).isEqualTo(2);
        Assertions.assertThat(acks.getAcknowledgement(label1).map(Acknowledgement::getHttpStatus))
                .contains(HttpStatus.UNAUTHORIZED);
        Assertions.assertThat(acks.getAcknowledgement(label2).map(Acknowledgement::getHttpStatus))
                .contains(HttpStatus.PAYMENT_REQUIRED);
    }

    @Test
    public void awaitsOnlyUntilTimeout() throws InterruptedException {

        // GIVEN
        final var testKit = actorSystemResource.newTestKit();
        final var timeout = Duration.ofSeconds(5);
        final var tag = "tag";
        final var correlationId = "awaitsOnlyUntilTimeout";
        final var thingId = ThingId.of("thing:id");
        final var label1 = AcknowledgementLabel.of("ack1");
        final var label2 = AcknowledgementLabel.of("ack2");
        final var command = DeleteThing.of(
                thingId,
                DittoHeaders.newBuilder()
                        .correlationId(correlationId)
                        .timeout(timeout)
                        .acknowledgementRequest(AcknowledgementRequest.of(label1), AcknowledgementRequest.of(label2))
                        .build()
        );
        final var underTest = testKit.childActorOf(getAcknowledgementAggregatorProps(command, testKit));

        // WHEN
        final var ack1 = Acknowledgement.of(label1,
                thingId,
                HttpStatus.UNAUTHORIZED,
                DittoHeaders.newBuilder().correlationId(correlationId).putHeader(tag, label1.toString()).build());
        final var ack2 = Acknowledgement.of(label2,
                thingId,
                HttpStatus.PAYMENT_REQUIRED,
                DittoHeaders.newBuilder().correlationId(correlationId).putHeader(tag, label2.toString()).build());

        // Wait more than half the time before sending first ack
        TimeUnit.SECONDS.sleep(timeout.toSeconds() / 2 + 1);
        underTest.tell(ack1, ActorRef.noSender());

        // Wait more than half the time before sending second ack. This should not be taken into account.
        TimeUnit.SECONDS.sleep(timeout.toSeconds() / 2 + 1);
        underTest.tell(ack2, ActorRef.noSender());

        // THEN
        final var acks = testKit.expectMsgClass(Acknowledgements.class);
        Assertions.assertThat(acks.getSize()).isEqualTo(2);
        Assertions.assertThat(acks.getAcknowledgement(label1).map(Acknowledgement::getHttpStatus))
                .contains(HttpStatus.UNAUTHORIZED);
        Assertions.assertThat(acks.getAcknowledgement(label2).map(Acknowledgement::getHttpStatus))
                .contains(HttpStatus.REQUEST_TIMEOUT);
    }

    @Test
    public void discardDuplicateAndUnsolicitedAcknowledgements() {

        // GIVEN
        final var testKit = actorSystemResource.newTestKit();
        final var tag = "tag";
        final var correlationId = testNameCorrelationId.getCorrelationId();
        final var label1 = AcknowledgementLabel.of("ack1");
        final var label2 = AcknowledgementLabel.of("ack2");
        final var label3 = AcknowledgementLabel.of("ack3");
        final ThingModifyCommand<?> command = DeleteThing.of(THING_ID, DittoHeaders.newBuilder()
                .correlationId(correlationId)
                .acknowledgementRequest(AcknowledgementRequest.of(label1), AcknowledgementRequest.of(label2))
                .putHeader(tag, DeleteThing.class.getSimpleName())
                .build());
        final var underTest = testKit.childActorOf(getAcknowledgementAggregatorProps(command, testKit));

        // WHEN
        final var ack1 = Acknowledgement.of(label1,
                THING_ID,
                HttpStatus.UNAUTHORIZED,
                DittoHeaders.newBuilder().correlationId(correlationId).putHeader(tag, label1.toString()).build());
        final var ack2 = Acknowledgement.of(label2,
                THING_ID,
                HttpStatus.PAYMENT_REQUIRED,
                DittoHeaders.newBuilder().correlationId(correlationId).putHeader(tag, label2.toString()).build());
        final var ack3 = Acknowledgement.of(label3,
                THING_ID,
                HttpStatus.OK,
                DittoHeaders.newBuilder().correlationId(correlationId).putHeader(tag, "unsolicited").build());
        final var ack4 = Acknowledgement.of(label1,
                THING_ID,
                HttpStatus.UNAUTHORIZED,
                DittoHeaders.newBuilder().correlationId(correlationId).putHeader(tag, "duplicate").build());
        underTest.tell(ack1, ActorRef.noSender());
        underTest.tell(ack3, ActorRef.noSender());
        underTest.tell(ack4, ActorRef.noSender());
        underTest.tell(ack2, ActorRef.noSender());

        // THEN
        final var acks = testKit.expectMsgClass(Acknowledgements.class);
        Assertions.assertThat(acks.getSize()).isEqualTo(2);
        Assertions.assertThat(acks.getAcknowledgement(label1)).contains(ack1);
        Assertions.assertThat(acks.getAcknowledgement(label2)).contains(ack2);
    }

    @Test
    public void resetConnectivityHeadersForLive() {

        // GIVEN
        final var testKit = actorSystemResource.newTestKit();
        final var label1 = AcknowledgementLabel.of("live-response");
        final var command = DeleteThing.of(THING_ID, DittoHeaders.newBuilder()
                .correlationId(testNameCorrelationId.getCorrelationId())
                .acknowledgementRequest(AcknowledgementRequest.of(label1))
                .channel("live")
                .replyTarget(0)
                .build());
        final var underTest = testKit.childActorOf(getAcknowledgementAggregatorProps(command, testKit));

        // WHEN
        final var response = DeleteThingResponse.of(THING_ID, command.getDittoHeaders()
                .toBuilder()
                .replyTarget(1)
                .expectedResponseTypes(ResponseType.RESPONSE)
                .build());
        underTest.tell(response, ActorRef.noSender());

        // THEN
        final var processedResponse = testKit.expectMsgClass(DeleteThingResponse.class);
        Assertions.assertThat(processedResponse.getDittoHeaders().getReplyTarget()).contains(0);
        Assertions.assertThat(processedResponse.isOfExpectedResponseType()).isFalse();
    }

    @Test
    public void returnInvalidThingCommandResponseAndThenValidCommandResponseWithoutConnectionIdForLive() {

        // GIVEN
        final var testKit = actorSystemResource.newTestKit();
        final var command = DeleteThing.of(THING_ID, DittoHeaders.newBuilder()
                .correlationId(testNameCorrelationId.getCorrelationId())
                .acknowledgementRequest(AcknowledgementRequest.of(AcknowledgementLabel.of("live-response")))
                .channel("live")
                .timeout(Duration.ofSeconds(3L))
                .build());
        final var underTest = testKit.childActorOf(getAcknowledgementAggregatorProps(command, testKit));

        // WHEN
        final var randomThingId = ThingId.generateRandom();
        final var invalidResponse = DeleteThingResponse.of(randomThingId, command.getDittoHeaders());
        underTest.tell(invalidResponse, ActorRef.noSender());

        final var validResponse = DeleteThingResponse.of(THING_ID, command.getDittoHeaders());
        underTest.tell(validResponse, ActorRef.noSender());

        // THEN
        testKit.expectMsg(validResponse);
    }

    @Test
    public void returnSingleInvalidThingCommandResponseWithoutConnectionIdForLive() {

        // GIVEN
        final var testKit = actorSystemResource.newTestKit();
        final var command = DeleteThing.of(THING_ID, DittoHeaders.newBuilder()
                .correlationId(testNameCorrelationId.getCorrelationId())
                .acknowledgementRequest(AcknowledgementRequest.of(AcknowledgementLabel.of("live-response")))
                .channel("live")
                .timeout(Duration.ofSeconds(3L))
                .build());
        final var underTest = testKit.childActorOf(getAcknowledgementAggregatorProps(command, testKit));

        // WHEN
        final var randomThingId = ThingId.generateRandom();
        final var response = DeleteThingResponse.of(randomThingId, command.getDittoHeaders());
        underTest.tell(response, ActorRef.noSender());

        // THEN
        final var commandTimeoutException =
                testKit.expectMsgClass(Duration.ofSeconds(5L), CommandTimeoutException.class);
        Assertions.assertThat(commandTimeoutException.getDittoHeaders().get(DittoHeaderDefinition.ENTITY_ID.getKey()))
                .isEqualTo(THING_ID.getEntityType() + ":" + THING_ID);
        Assertions.assertThat(commandTimeoutException.getDescription())
                .hasValueSatisfying(description -> Assertions.assertThat(description)
                        .contains(String.format(
                                "Entity ID of live response <%s> differs from entity ID of command <%s>.",
                                randomThingId,
                                THING_ID
                        )));
    }

    @Test
    public void returnSingleInvalidThingCommandResponseWithConnectionIdForLive() {

        // GIVEN
        final var testKit = actorSystemResource.newTestKit();
        final var connectionId = UUID.randomUUID().toString();
        final var command = DeleteThing.of(THING_ID, DittoHeaders.newBuilder()
                .correlationId(testNameCorrelationId.getCorrelationId())
                .acknowledgementRequest(AcknowledgementRequest.of(AcknowledgementLabel.of("live-response")))
                .channel("live")
                .timeout(Duration.ofSeconds(3L))
                .build());
        final var underTest = testKit.childActorOf(getAcknowledgementAggregatorProps(command, testKit));

        // WHEN
        final var randomThingId = ThingId.generateRandom();
        final var response = DeleteThingResponse.of(randomThingId, DittoHeaders.newBuilder(command.getDittoHeaders())
                .putHeader(DittoHeaderDefinition.CONNECTION_ID.getKey(), connectionId)
                .build());
        underTest.tell(response, ActorRef.noSender());

        // THEN
        final var commandTimeoutException =
                testKit.expectMsgClass(Duration.ofSeconds(5L), CommandTimeoutException.class);
        Assertions.assertThat(commandTimeoutException.getDittoHeaders().get(DittoHeaderDefinition.ENTITY_ID.getKey()))
                .isEqualTo(THING_ID.getEntityType() + ":" + THING_ID);
        Assertions.assertThat(commandTimeoutException.getDescription())
                .hasValueSatisfying(description -> Assertions.assertThat(description)
                        .contains(String.format(
                                "Entity ID of live response <%s> differs from entity ID of command <%s>.",
                                randomThingId,
                                THING_ID
                        )));
        final var responseValidationFailureArgumentCaptor =
                ArgumentCaptor.forClass(MatchingValidationResult.Failure.class);
        Mockito.verify(responseValidationFailureConsumer).accept(responseValidationFailureArgumentCaptor.capture());
        final var responseValidationFailure = responseValidationFailureArgumentCaptor.getValue();

        final var softly = new JUnitSoftAssertions();
        softly.assertThat(responseValidationFailure.getConnectionId())
                .as("connection ID")
                .contains(connectionId);
        softly.assertThat(responseValidationFailure.getCommand()).as("command").isEqualTo(command);
        softly.assertThat(responseValidationFailure.getCommandResponse())
                .as("command response")
                .isEqualTo(response);
        softly.assertThat(responseValidationFailure.getDetailMessage())
                .as("failure detail message")
                .isEqualTo("Entity ID of live response <%s> differs from entity ID of command <%s>.",
                        randomThingId,
                        THING_ID);
        softly.assertAll();
    }

    private <C extends ThingCommand<C>> Props getAcknowledgementAggregatorProps(final ThingModifyCommand<?> command,
            final TestKit testKit) {
        return AcknowledgementAggregatorActor.props(command.getEntityId(),
                command,
                DefaultAcknowledgementConfig.of(ConfigFactory.empty()),
                headerTranslator,
                tellThis(testKit.getRef()),
                responseValidationFailureConsumer,
                ThingCommandResponseAcknowledgementProvider.getInstance()
        );
    }

    private static Consumer<Object> tellThis(final ActorRef testKit) {
        return result -> testKit.tell(result, ActorRef.noSender());
    }

}
