/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

import static org.assertj.core.api.Assertions.assertThatCode;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgement;
import org.eclipse.ditto.connectivity.model.MessageSendingFailedException;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.logs.ConnectionLogger;
import org.eclipse.ditto.internal.utils.test.correlationid.TestNameCorrelationId;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.messages.model.Message;
import org.eclipse.ditto.messages.model.signals.commands.SendClaimMessageResponse;
import org.eclipse.ditto.messages.model.signals.commands.SendThingMessage;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.ThingErrorResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyAttributeResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeatureProperty;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThing;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThingResponse;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Unit test for {@link CommandAndCommandResponseMatchingValidator}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class CommandAndCommandResponseMatchingValidatorTest {

    private static final ThingId THING_ID = ThingId.generateRandom();

    @Rule
    public final TestNameCorrelationId testNameCorrelationId = TestNameCorrelationId.newInstance();

    @Mock
    private ConnectionLogger connectionLogger;

    @Test
    public void newInstanceWithNullConnectionLogger() {
        Assertions.assertThatNullPointerException()
                .isThrownBy(() -> CommandAndCommandResponseMatchingValidator.newInstance(null))
                .withMessage("The connectionLogger must not be null!")
                .withNoCause();
    }

    @Test
    public void acceptSignalsWithNoCorrelationIdEach() {
        final var emptyDittoHeaders = DittoHeaders.empty();
        final var command = RetrieveThing.of(THING_ID, emptyDittoHeaders);
        final var commandResponse = RetrieveThingResponse.of(THING_ID, JsonObject.empty(), emptyDittoHeaders);
        final var underTest = CommandAndCommandResponseMatchingValidator.newInstance(connectionLogger);

        assertThatCode(() -> underTest.accept(command, commandResponse)).doesNotThrowAnyException();

        Mockito.verifyNoInteractions(connectionLogger);
    }

    @Test
    public void acceptSignalsWithSameCorrelationId() {
        final var dittoHeaders = getDittoHeadersWithCorrelationId();
        final var command = RetrieveThing.of(THING_ID, dittoHeaders);
        final var commandResponse = RetrieveThingResponse.of(THING_ID, JsonObject.empty(), dittoHeaders);
        final var underTest = CommandAndCommandResponseMatchingValidator.newInstance(connectionLogger);

        assertThatCode(() -> underTest.accept(command, commandResponse)).doesNotThrowAnyException();

        Mockito.verifyNoInteractions(connectionLogger);
    }

    @Test
    public void acceptSignalsWithMismatchingCorrelationIds() {
        final var baseCorrelationId = testNameCorrelationId.getCorrelationId();
        final var correlationIdCommand = baseCorrelationId.withSuffix("-command");
        final var commandDittoHeaders = DittoHeaders.newBuilder()
                .correlationId(correlationIdCommand)
                .build();
        final var command = RetrieveThing.of(THING_ID, commandDittoHeaders);
        final var correlationIdCommandResponse = baseCorrelationId.withSuffix("-response");
        final var responseDittoHeaders = DittoHeaders.newBuilder()
                .correlationId(correlationIdCommandResponse)
                .build();
        final var commandResponse = RetrieveThingResponse.of(THING_ID, JsonObject.empty(), responseDittoHeaders);
        final var underTest = CommandAndCommandResponseMatchingValidator.newInstance(connectionLogger);

        Assertions.assertThatExceptionOfType(MessageSendingFailedException.class)
                .isThrownBy(() -> underTest.accept(command, commandResponse))
                .withMessage("Correlation ID of live response <%s> differs from correlation ID of command <%s>.",
                        correlationIdCommandResponse,
                        correlationIdCommand)
                .withNoCause();

        Mockito.verify(connectionLogger)
                .failure(Mockito.eq(commandResponse), Mockito.any(MessageSendingFailedException.class));
    }

    @Test
    public void acceptCommandWithCorrelationIdAndCommandResponseWithoutCorrelationId() {
        final var correlationIdCommand = testNameCorrelationId.getCorrelationId();
        final var dittoHeaders = DittoHeaders.newBuilder().correlationId(correlationIdCommand).build();
        final var command = RetrieveThing.of(THING_ID, dittoHeaders);
        final var commandResponse = RetrieveThingResponse.of(THING_ID, JsonObject.empty(), DittoHeaders.empty());
        final var underTest = CommandAndCommandResponseMatchingValidator.newInstance(connectionLogger);

        Assertions.assertThatExceptionOfType(MessageSendingFailedException.class)
                .isThrownBy(() -> underTest.accept(command, commandResponse))
                .withMessage("Live response has no correlation ID while command has correlation ID <%s>.",
                        correlationIdCommand)
                .withNoCause();

        Mockito.verify(connectionLogger)
                .failure(Mockito.eq(commandResponse), Mockito.any(MessageSendingFailedException.class));
    }

    @Test
    public void acceptCommandWithoutCorrelationIdAndCommandResponseWithCorrelationId() {
        final var command = RetrieveThing.of(THING_ID, DittoHeaders.empty());
        final var correlationIdCommandResponse = testNameCorrelationId.getCorrelationId();
        final var dittoHeaders = DittoHeaders.newBuilder().correlationId(correlationIdCommandResponse).build();
        final var commandResponse = RetrieveThingResponse.of(THING_ID, JsonObject.empty(), dittoHeaders);
        final var underTest = CommandAndCommandResponseMatchingValidator.newInstance(connectionLogger);

        Assertions.assertThatExceptionOfType(MessageSendingFailedException.class)
                .isThrownBy(() -> underTest.accept(command, commandResponse))
                .withMessage("Live response has correlation ID <%s> while command has none.",
                        correlationIdCommandResponse)
                .withNoCause();

        Mockito.verify(connectionLogger)
                .failure(Mockito.eq(commandResponse), Mockito.any(MessageSendingFailedException.class));
    }

    @Test
    public void acceptCommandAndAssociatedAcknowledgement() {
        final var dittoHeaders = getDittoHeadersWithCorrelationId();
        final var command = ModifyFeatureProperty.of(THING_ID,
                "thermostat",
                JsonPointer.of("currentTemperature"),
                JsonValue.of(21.5D),
                dittoHeaders);
        final var acknowledgement =
                Acknowledgement.of(AcknowledgementLabel.of("property-modified"), THING_ID, HttpStatus.OK, dittoHeaders);
        final var underTest = CommandAndCommandResponseMatchingValidator.newInstance(connectionLogger);

        assertThatCode(() -> underTest.accept(command, acknowledgement)).doesNotThrowAnyException();

        Mockito.verifyNoInteractions(connectionLogger);
    }

    @Test
    public void acceptCommandAndCommandResponseWithDifferentSignalDomain() {
        final var dittoHeaders = getDittoHeadersWithCorrelationId();
        final var command = RetrieveThing.of(THING_ID, dittoHeaders);
        final var commandResponse =
                SendClaimMessageResponse.of(THING_ID, Mockito.mock(Message.class), HttpStatus.OK, dittoHeaders);
        final var underTest = CommandAndCommandResponseMatchingValidator.newInstance(connectionLogger);

        Assertions.assertThatExceptionOfType(MessageSendingFailedException.class)
                .isThrownBy(() -> underTest.accept(command, commandResponse))
                .withMessage("Type of live response <%s> is not related to type of command <%s>.",
                        commandResponse.getType(),
                        command.getType())
                .withNoCause();

        Mockito.verify(connectionLogger)
                .failure(Mockito.eq(commandResponse), Mockito.any(MessageSendingFailedException.class));
    }

    @Test
    public void acceptCommandAndErrorResponseOfSameSignalDomain() {
        final var dittoHeaders = getDittoHeadersWithCorrelationId();
        final var command = RetrieveThing.of(THING_ID, dittoHeaders);
        final var dittoRuntimeException = Mockito.mock(DittoRuntimeException.class);
        Mockito.when(dittoRuntimeException.getDittoHeaders()).thenReturn(dittoHeaders);
        Mockito.when(dittoRuntimeException.getHttpStatus()).thenReturn(HttpStatus.BAD_REQUEST);
        final var thingErrorResponse = ThingErrorResponse.of(THING_ID, dittoRuntimeException);
        final var underTest = CommandAndCommandResponseMatchingValidator.newInstance(connectionLogger);

        assertThatCode(() -> underTest.accept(command, thingErrorResponse)).doesNotThrowAnyException();

        Mockito.verifyNoInteractions(connectionLogger);
    }

    @Test
    public void acceptCommandAndMismatchingCommandResponseByType() {
        final var dittoHeaders = getDittoHeadersWithCorrelationId();
        final var command = RetrieveThing.of(THING_ID, dittoHeaders);
        final var mismatchingCommandResponse =
                ModifyAttributeResponse.modified(THING_ID, JsonPointer.of("manufacturer"), dittoHeaders);
        final var underTest = CommandAndCommandResponseMatchingValidator.newInstance(connectionLogger);

        Assertions.assertThatExceptionOfType(MessageSendingFailedException.class)
                .isThrownBy(() -> underTest.accept(command, mismatchingCommandResponse))
                .withMessage("Type of live response <%s> is not related to type of command <%s>.",
                        mismatchingCommandResponse.getType(),
                        command.getType())
                .withNoCause();

        Mockito.verify(connectionLogger)
                .failure(Mockito.eq(mismatchingCommandResponse), Mockito.any(MessageSendingFailedException.class));
    }

    @Test
    public void acceptMessageSignalsWithDifferentType() {
        final var dittoHeaders = getDittoHeadersWithCorrelationId();
        final var messageMock = Mockito.mock(Message.class);
        Mockito.when(messageMock.getEntityId()).thenReturn(THING_ID);
        final var sendThingMessage = SendThingMessage.of(THING_ID, messageMock, dittoHeaders);
        final var sendClaimMessageResponse =
                SendClaimMessageResponse.of(THING_ID, messageMock, HttpStatus.OK, dittoHeaders);
        final var underTest = CommandAndCommandResponseMatchingValidator.newInstance(connectionLogger);

        Assertions.assertThatExceptionOfType(MessageSendingFailedException.class)
                .isThrownBy(() -> underTest.accept(sendThingMessage, sendClaimMessageResponse))
                .withMessage("Type of live message response <%s> is not related to type of message command <%s>.",
                        sendClaimMessageResponse.getType(),
                        sendThingMessage.getType())
                .withNoCause();

        Mockito.verify(connectionLogger)
                .failure(Mockito.eq(sendClaimMessageResponse), Mockito.any(MessageSendingFailedException.class));
    }

    @Test
    public void acceptSignalsWithSameEntityId() {
        final var dittoHeaders = getDittoHeadersWithCorrelationId();
        final var command = RetrieveThing.of(THING_ID, dittoHeaders);
        final var commandResponse = RetrieveThingResponse.of(THING_ID, JsonObject.empty(), dittoHeaders);
        final var underTest = CommandAndCommandResponseMatchingValidator.newInstance(connectionLogger);

        assertThatCode(() -> underTest.accept(command, commandResponse)).doesNotThrowAnyException();

        Mockito.verifyNoInteractions(connectionLogger);
    }

    @Test
    public void acceptSignalsWithDifferentEntityIds() {
        final var dittoHeaders = getDittoHeadersWithCorrelationId();
        final var command = RetrieveThing.of(THING_ID, dittoHeaders);
        final var commandResponse =
                RetrieveThingResponse.of(ThingId.generateRandom(), JsonObject.empty(), dittoHeaders);
        final var underTest = CommandAndCommandResponseMatchingValidator.newInstance(connectionLogger);

        Assertions.assertThatExceptionOfType(MessageSendingFailedException.class)
                .isThrownBy(() -> underTest.accept(command, commandResponse))
                .withMessage("Entity ID of live response <%s> differs from entity ID of command <%s>.",
                        commandResponse.getEntityId(),
                        command.getEntityId())
                .withNoCause();

        Mockito.verify(connectionLogger)
                .failure(Mockito.eq(commandResponse), Mockito.any(MessageSendingFailedException.class));
    }

    private DittoHeaders getDittoHeadersWithCorrelationId() {
        return DittoHeaders.newBuilder().correlationId(testNameCorrelationId.getCorrelationId()).build();
    }

}
