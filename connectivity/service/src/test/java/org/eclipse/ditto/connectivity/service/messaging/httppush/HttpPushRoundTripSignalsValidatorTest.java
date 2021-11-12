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

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.byLessThan;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.assertj.core.api.Assertions;
import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.correlationid.TestNameCorrelationId;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.UnsupportedSignalException;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgement;
import org.eclipse.ditto.connectivity.model.LogCategory;
import org.eclipse.ditto.connectivity.model.LogEntry;
import org.eclipse.ditto.connectivity.model.LogLevel;
import org.eclipse.ditto.connectivity.model.LogType;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.logs.ConnectionLogger;
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
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Unit test for {@link HttpPushRoundTripSignalsValidator}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class HttpPushRoundTripSignalsValidatorTest {

    private static final ThingId THING_ID = ThingId.generateRandom();

    @Rule
    public final TestNameCorrelationId testNameCorrelationId = TestNameCorrelationId.newInstance();

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @Mock
    private ConnectionLogger connectionLogger;

    private ArgumentCaptor<LogEntry> logEntryArgumentCaptor;

    @Before
    public void before() {
        logEntryArgumentCaptor = ArgumentCaptor.forClass(LogEntry.class);
    }

    @Test
    public void newInstanceWithNullConnectionLogger() {
        Assertions.assertThatNullPointerException()
                .isThrownBy(() -> HttpPushRoundTripSignalsValidator.newInstance(null))
                .withMessage("The connectionLogger must not be null!")
                .withNoCause();
    }

    @Test
    public void acceptSignalsWithNoCorrelationIdEach() {
        final var emptyDittoHeaders = DittoHeaders.empty();
        final var command = RetrieveThing.of(THING_ID, emptyDittoHeaders);
        final var commandResponse = RetrieveThingResponse.of(THING_ID, JsonObject.empty(), emptyDittoHeaders);
        final var underTest = HttpPushRoundTripSignalsValidator.newInstance(connectionLogger);

        Assertions.assertThatCode(() -> underTest.accept(command, commandResponse)).doesNotThrowAnyException();

        Mockito.verifyNoInteractions(connectionLogger);
    }

    @Test
    public void acceptSignalsWithSameCorrelationId() {
        final var dittoHeaders = getDittoHeadersWithCorrelationId();
        final var command = RetrieveThing.of(THING_ID, dittoHeaders);
        final var commandResponse = RetrieveThingResponse.of(THING_ID, JsonObject.empty(), dittoHeaders);
        final var underTest = HttpPushRoundTripSignalsValidator.newInstance(connectionLogger);

        Assertions.assertThatCode(() -> underTest.accept(command, commandResponse)).doesNotThrowAnyException();

        Mockito.verifyNoInteractions(connectionLogger);
    }

    @Test
    public void acceptSignalsWithMismatchingCorrelationIds() {
        final var baseCorrelationId = testNameCorrelationId.getCorrelationId();
        final var correlationIdCommand = baseCorrelationId.withSuffix("-command");
        final var commandDittoHeaders = DittoHeaders.newBuilder().correlationId(correlationIdCommand).build();
        final var command = RetrieveThing.of(THING_ID, commandDittoHeaders);
        final var correlationIdCommandResponse = baseCorrelationId.withSuffix("-response");
        final var responseDittoHeaders = DittoHeaders.newBuilder().correlationId(correlationIdCommandResponse).build();
        final var commandResponse = RetrieveThingResponse.of(THING_ID, JsonObject.empty(), responseDittoHeaders);
        final var expectedDetailMessage =
                String.format("Correlation ID of live response <%s> differs from correlation ID of command <%s>.",
                        correlationIdCommandResponse,
                        correlationIdCommand);
        final var underTest = HttpPushRoundTripSignalsValidator.newInstance(connectionLogger);

        assertThatExceptionOfType(UnsupportedSignalException.class)
                .isThrownBy(() -> underTest.accept(command, commandResponse))
                .withMessage(expectedDetailMessage)
                .withNoCause();

        Mockito.verify(connectionLogger).logEntry(logEntryArgumentCaptor.capture());
        softly.assertThat(logEntryArgumentCaptor.getValue()).satisfies(logEntry -> {
            softly.assertThat(logEntry.getCorrelationId())
                    .as("correlation ID")
                    .isEqualTo(correlationIdCommand.toString());
            softly.assertThat(logEntry.getTimestamp())
                    .as("timestamp")
                    .isCloseTo(Instant.now(), byLessThan(2_500L, ChronoUnit.MILLIS));
            softly.assertThat(logEntry.getLogLevel()).as("log level").isEqualTo(LogLevel.FAILURE);
            softly.assertThat(logEntry.getLogCategory()).as("log category").isEqualTo(LogCategory.RESPONSE);
            softly.assertThat(logEntry.getLogType()).as("log type").isEqualTo(LogType.DROPPED);
            softly.assertThat(logEntry.getEntityId()).as("entity ID").hasValue(command.getEntityId());
            softly.assertThat(logEntry.getMessage()).as("message").isEqualTo(expectedDetailMessage);
        });
    }

    @Test
    public void acceptCommandWithCorrelationIdAndCommandResponseWithoutCorrelationId() {
        final var correlationIdCommand = testNameCorrelationId.getCorrelationId();
        final var dittoHeaders = DittoHeaders.newBuilder().correlationId(correlationIdCommand).build();
        final var command = RetrieveThing.of(THING_ID, dittoHeaders);
        final var commandResponse = RetrieveThingResponse.of(THING_ID, JsonObject.empty(), DittoHeaders.empty());
        final var expectedDetailMessage =
                String.format("Correlation ID of live response <%s> differs from correlation ID of command <%s>.",
                        null,
                        correlationIdCommand);
        final var underTest = HttpPushRoundTripSignalsValidator.newInstance(connectionLogger);

        assertThatExceptionOfType(UnsupportedSignalException.class)
                .isThrownBy(() -> underTest.accept(command, commandResponse))
                .withMessage(expectedDetailMessage)
                .withNoCause();

        Mockito.verify(connectionLogger).logEntry(logEntryArgumentCaptor.capture());
        softly.assertThat(logEntryArgumentCaptor.getValue()).satisfies(logEntry -> {
            softly.assertThat(logEntry.getCorrelationId())
                    .as("correlation ID")
                    .isEqualTo(correlationIdCommand.toString());
            softly.assertThat(logEntry.getTimestamp())
                    .as("timestamp")
                    .isCloseTo(Instant.now(), byLessThan(2_500L, ChronoUnit.MILLIS));
            softly.assertThat(logEntry.getLogLevel()).as("log level").isEqualTo(LogLevel.FAILURE);
            softly.assertThat(logEntry.getLogCategory()).as("log category").isEqualTo(LogCategory.RESPONSE);
            softly.assertThat(logEntry.getLogType()).as("log type").isEqualTo(LogType.DROPPED);
            softly.assertThat(logEntry.getEntityId()).as("entity ID").hasValue(command.getEntityId());
            softly.assertThat(logEntry.getMessage()).as("message").isEqualTo(expectedDetailMessage);
        });
    }

    @Test
    public void acceptCommandWithoutCorrelationIdAndCommandResponseWithCorrelationId() {
        final var command = RetrieveThing.of(THING_ID, DittoHeaders.empty());
        final var correlationIdCommandResponse = testNameCorrelationId.getCorrelationId();
        final var dittoHeaders = DittoHeaders.newBuilder().correlationId(correlationIdCommandResponse).build();
        final var commandResponse = RetrieveThingResponse.of(THING_ID, JsonObject.empty(), dittoHeaders);
        final var expectedDetailMessage =
                String.format("Correlation ID of live response <%s> differs from correlation ID of command <%s>.",
                        correlationIdCommandResponse,
                        null);
        final var underTest = HttpPushRoundTripSignalsValidator.newInstance(connectionLogger);

        assertThatExceptionOfType(UnsupportedSignalException.class)
                .isThrownBy(() -> underTest.accept(command, commandResponse))
                .withMessage(expectedDetailMessage)
                .withNoCause();

        Mockito.verify(connectionLogger).logEntry(logEntryArgumentCaptor.capture());
        softly.assertThat(logEntryArgumentCaptor.getValue()).satisfies(logEntry -> {
            softly.assertThat(logEntry.getCorrelationId())
                    .as("correlation ID")
                    .isEqualTo(correlationIdCommandResponse.toString());
            softly.assertThat(logEntry.getTimestamp())
                    .as("timestamp")
                    .isCloseTo(Instant.now(), byLessThan(2_500L, ChronoUnit.MILLIS));
            softly.assertThat(logEntry.getLogLevel()).as("log level").isEqualTo(LogLevel.FAILURE);
            softly.assertThat(logEntry.getLogCategory()).as("log category").isEqualTo(LogCategory.RESPONSE);
            softly.assertThat(logEntry.getLogType()).as("log type").isEqualTo(LogType.DROPPED);
            softly.assertThat(logEntry.getEntityId()).as("entity ID").hasValue(command.getEntityId());
            softly.assertThat(logEntry.getMessage()).as("message").isEqualTo(expectedDetailMessage);
        });
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
        final var underTest = HttpPushRoundTripSignalsValidator.newInstance(connectionLogger);

        Assertions.assertThatCode(() -> underTest.accept(command, acknowledgement)).doesNotThrowAnyException();

        Mockito.verifyNoInteractions(connectionLogger);
    }

    @Test
    public void acceptCommandAndCommandResponseWithDifferentSignalDomain() {
        final var dittoHeaders = getDittoHeadersWithCorrelationId();
        final var command = RetrieveThing.of(THING_ID, dittoHeaders);
        final var commandResponse =
                SendClaimMessageResponse.of(THING_ID, Mockito.mock(Message.class), HttpStatus.OK, dittoHeaders);
        final var expectedDetailMessage =
                String.format("Type of live response <%s> is not related to type of command <%s>.",
                        commandResponse.getType(),
                        command.getType());
        final var underTest = HttpPushRoundTripSignalsValidator.newInstance(connectionLogger);

        assertThatExceptionOfType(UnsupportedSignalException.class)
                .isThrownBy(() -> underTest.accept(command, commandResponse))
                .withMessage(expectedDetailMessage)
                .withNoCause();

        Mockito.verify(connectionLogger).logEntry(logEntryArgumentCaptor.capture());
        softly.assertThat(logEntryArgumentCaptor.getValue()).satisfies(logEntry -> {
            softly.assertThat(logEntry.getCorrelationId())
                    .as("correlation ID")
                    .isEqualTo(testNameCorrelationId.getCorrelationId().toString());
            softly.assertThat(logEntry.getTimestamp())
                    .as("timestamp")
                    .isCloseTo(Instant.now(), byLessThan(2_500L, ChronoUnit.MILLIS));
            softly.assertThat(logEntry.getLogLevel()).as("log level").isEqualTo(LogLevel.FAILURE);
            softly.assertThat(logEntry.getLogCategory()).as("log category").isEqualTo(LogCategory.RESPONSE);
            softly.assertThat(logEntry.getLogType()).as("log type").isEqualTo(LogType.DROPPED);
            softly.assertThat(logEntry.getEntityId()).as("entity ID").hasValue(command.getEntityId());
            softly.assertThat(logEntry.getMessage()).as("message").isEqualTo(expectedDetailMessage);
        });
    }

    @Test
    public void acceptCommandAndErrorResponseOfSameSignalDomain() {
        final var dittoHeaders = getDittoHeadersWithCorrelationId();
        final var command = RetrieveThing.of(THING_ID, dittoHeaders);
        final var dittoRuntimeException = Mockito.mock(DittoRuntimeException.class);
        Mockito.when(dittoRuntimeException.getDittoHeaders()).thenReturn(dittoHeaders);
        Mockito.when(dittoRuntimeException.getHttpStatus()).thenReturn(HttpStatus.BAD_REQUEST);
        final var thingErrorResponse = ThingErrorResponse.of(THING_ID, dittoRuntimeException);
        final var underTest = HttpPushRoundTripSignalsValidator.newInstance(connectionLogger);

        Assertions.assertThatCode(() -> underTest.accept(command, thingErrorResponse)).doesNotThrowAnyException();

        Mockito.verifyNoInteractions(connectionLogger);
    }

    @Test
    public void acceptCommandAndMismatchingCommandResponseByType() {
        final var dittoHeaders = getDittoHeadersWithCorrelationId();
        final var command = RetrieveThing.of(THING_ID, dittoHeaders);
        final var mismatchingCommandResponse =
                ModifyAttributeResponse.modified(THING_ID, JsonPointer.of("manufacturer"), dittoHeaders);
        final var expectedDetailMessage =
                String.format("Type of live response <%s> is not related to type of command <%s>.",
                        mismatchingCommandResponse.getType(),
                        command.getType());
        final var underTest = HttpPushRoundTripSignalsValidator.newInstance(connectionLogger);

        assertThatExceptionOfType(UnsupportedSignalException.class)
                .isThrownBy(() -> underTest.accept(command, mismatchingCommandResponse))
                .withMessage(expectedDetailMessage)
                .withNoCause();

        Mockito.verify(connectionLogger).logEntry(logEntryArgumentCaptor.capture());
        softly.assertThat(logEntryArgumentCaptor.getValue()).satisfies(logEntry -> {
            softly.assertThat(logEntry.getCorrelationId())
                    .as("correlation ID")
                    .isEqualTo(testNameCorrelationId.getCorrelationId().toString());
            softly.assertThat(logEntry.getTimestamp())
                    .as("timestamp")
                    .isCloseTo(Instant.now(), byLessThan(2_500L, ChronoUnit.MILLIS));
            softly.assertThat(logEntry.getLogLevel()).as("log level").isEqualTo(LogLevel.FAILURE);
            softly.assertThat(logEntry.getLogCategory()).as("log category").isEqualTo(LogCategory.RESPONSE);
            softly.assertThat(logEntry.getLogType()).as("log type").isEqualTo(LogType.DROPPED);
            softly.assertThat(logEntry.getEntityId()).as("entity ID").hasValue(command.getEntityId());
            softly.assertThat(logEntry.getMessage()).as("message").isEqualTo(expectedDetailMessage);
        });
    }

    @Test
    public void acceptMessageSignalsWithDifferentType() {
        final var dittoHeaders = getDittoHeadersWithCorrelationId();
        final var messageMock = Mockito.mock(Message.class);
        Mockito.when(messageMock.getEntityId()).thenReturn(THING_ID);
        final var sendThingMessage = SendThingMessage.of(THING_ID, messageMock, dittoHeaders);
        final var sendClaimMessageResponse =
                SendClaimMessageResponse.of(THING_ID, messageMock, HttpStatus.OK, dittoHeaders);
        final var expectedDetailMessage =
                String.format("Type of live response <%s> is not related to type of command <%s>.",
                        sendClaimMessageResponse.getType(),
                        sendThingMessage.getType());
        final var underTest = HttpPushRoundTripSignalsValidator.newInstance(connectionLogger);

        assertThatExceptionOfType(UnsupportedSignalException.class)
                .isThrownBy(() -> underTest.accept(sendThingMessage, sendClaimMessageResponse))
                .withMessage(expectedDetailMessage)
                .withNoCause();

        Mockito.verify(connectionLogger).logEntry(logEntryArgumentCaptor.capture());
        softly.assertThat(logEntryArgumentCaptor.getValue()).satisfies(logEntry -> {
            softly.assertThat(logEntry.getCorrelationId())
                    .as("correlation ID")
                    .isEqualTo(testNameCorrelationId.getCorrelationId().toString());
            softly.assertThat(logEntry.getTimestamp())
                    .as("timestamp")
                    .isCloseTo(Instant.now(), byLessThan(2_500L, ChronoUnit.MILLIS));
            softly.assertThat(logEntry.getLogLevel()).as("log level").isEqualTo(LogLevel.FAILURE);
            softly.assertThat(logEntry.getLogCategory()).as("log category").isEqualTo(LogCategory.RESPONSE);
            softly.assertThat(logEntry.getLogType()).as("log type").isEqualTo(LogType.DROPPED);
            softly.assertThat(logEntry.getEntityId()).as("entity ID").hasValue(sendThingMessage.getEntityId());
            softly.assertThat(logEntry.getMessage()).as("message").isEqualTo(expectedDetailMessage);
        });
    }

    @Test
    public void acceptSignalsWithSameEntityId() {
        final var dittoHeaders = getDittoHeadersWithCorrelationId();
        final var command = RetrieveThing.of(THING_ID, dittoHeaders);
        final var commandResponse = RetrieveThingResponse.of(THING_ID, JsonObject.empty(), dittoHeaders);
        final var underTest = HttpPushRoundTripSignalsValidator.newInstance(connectionLogger);

        Assertions.assertThatCode(() -> underTest.accept(command, commandResponse)).doesNotThrowAnyException();

        Mockito.verifyNoInteractions(connectionLogger);
    }

    @Test
    public void acceptSignalsWithDifferentEntityIds() {
        final var dittoHeaders = getDittoHeadersWithCorrelationId();
        final var command = RetrieveThing.of(THING_ID, dittoHeaders);
        final var commandResponseThingId = ThingId.generateRandom();
        final var commandResponse = RetrieveThingResponse.of(commandResponseThingId, JsonObject.empty(), dittoHeaders);
        final var expectedDetailMessage =
                String.format("Entity ID of live response <%s> differs from entity ID of command <%s>.",
                        commandResponse.getEntityId(),
                        command.getEntityId());
        final var underTest = HttpPushRoundTripSignalsValidator.newInstance(connectionLogger);

        assertThatExceptionOfType(UnsupportedSignalException.class)
                .isThrownBy(() -> underTest.accept(command, commandResponse))
                .withMessage(expectedDetailMessage)
                .withNoCause();

        Mockito.verify(connectionLogger).logEntry(logEntryArgumentCaptor.capture());
        softly.assertThat(logEntryArgumentCaptor.getValue()).satisfies(logEntry -> {
            softly.assertThat(logEntry.getCorrelationId())
                    .as("correlation ID")
                    .isEqualTo(testNameCorrelationId.getCorrelationId().toString());
            softly.assertThat(logEntry.getTimestamp())
                    .as("timestamp")
                    .isCloseTo(Instant.now(), byLessThan(2_500L, ChronoUnit.MILLIS));
            softly.assertThat(logEntry.getLogLevel()).as("log level").isEqualTo(LogLevel.FAILURE);
            softly.assertThat(logEntry.getLogCategory()).as("log category").isEqualTo(LogCategory.RESPONSE);
            softly.assertThat(logEntry.getLogType()).as("log type").isEqualTo(LogType.DROPPED);
            softly.assertThat(logEntry.getEntityId()).as("entity ID").hasValue(THING_ID);
            softly.assertThat(logEntry.getMessage()).as("message").isEqualTo(expectedDetailMessage);
        });
    }

    private DittoHeaders getDittoHeadersWithCorrelationId() {
        return DittoHeaders.newBuilder().correlationId(testNameCorrelationId.getCorrelationId()).build();
    }

}
