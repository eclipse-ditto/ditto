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
package org.eclipse.ditto.internal.models.signal.correlation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.common.ResponseType;
import org.eclipse.ditto.base.model.correlationid.TestNameCorrelationId;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.exceptions.TooManyRequestsException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgement;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.messages.model.Message;
import org.eclipse.ditto.messages.model.signals.commands.SendClaimMessageResponse;
import org.eclipse.ditto.messages.model.signals.commands.SendThingMessage;
import org.eclipse.ditto.messages.model.signals.commands.SendThingMessageResponse;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.ThingErrorResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyAttribute;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyAttributeResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeatureProperty;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThing;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThingResponse;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
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

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @Test
    public void getInstanceReturnsNotNull() {
        final var instance = CommandAndCommandResponseMatchingValidator.getInstance();

        assertThat(instance).isNotNull();
    }

    @Test
    public void applySignalsWithNoCorrelationIdEach() {
        final var emptyDittoHeaders = DittoHeaders.empty();
        final var command = RetrieveThing.of(THING_ID, emptyDittoHeaders);
        final var commandResponse = RetrieveThingResponse.of(THING_ID, JsonObject.empty(), emptyDittoHeaders);
        final var underTest = CommandAndCommandResponseMatchingValidator.getInstance();

        final var validationResult = underTest.apply(command, commandResponse);

        assertThat(validationResult.isSuccess()).isTrue();
    }

    @Test
    public void applySignalsWithSameCorrelationId() {
        final var dittoHeaders = getDittoHeadersWithCorrelationId();
        final var command = RetrieveThing.of(THING_ID, dittoHeaders);
        final var commandResponse = RetrieveThingResponse.of(THING_ID, JsonObject.empty(), dittoHeaders);
        final var underTest = CommandAndCommandResponseMatchingValidator.getInstance();

        final var validationResult = underTest.apply(command, commandResponse);

        assertThat(validationResult.isSuccess()).isTrue();
    }

    @Test
    public void applySignalsWithMismatchingCorrelationIds() {
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
        final var underTest = CommandAndCommandResponseMatchingValidator.getInstance();

        final var validationResult = underTest.apply(command, commandResponse);

        softly.assertThat(validationResult.asFailureOrThrow())
                .satisfies(failure -> softly.assertThat(failure.getDetailMessage())
                        .as("detail message")
                        .isEqualTo("Correlation ID of live response <%s> differs from correlation ID of command <%s>.",
                                correlationIdCommandResponse,
                                correlationIdCommand));
    }

    @Test
    public void applyCommandWithCorrelationIdAndCommandResponseWithoutCorrelationId() {
        final var correlationIdCommand = testNameCorrelationId.getCorrelationId();
        final var dittoHeaders = DittoHeaders.newBuilder().correlationId(correlationIdCommand).build();
        final var command = RetrieveThing.of(THING_ID, dittoHeaders);
        final var commandResponse = RetrieveThingResponse.of(THING_ID, JsonObject.empty(), DittoHeaders.empty());
        final var underTest = CommandAndCommandResponseMatchingValidator.getInstance();

        final var validationResult = underTest.apply(command, commandResponse);

        softly.assertThat(validationResult.asFailureOrThrow())
                .satisfies(failure -> softly.assertThat(failure.getDetailMessage())
                        .as("detail message")
                        .isEqualTo("Correlation ID of live response <%s> differs from correlation ID of command <%s>.",
                                null,
                                correlationIdCommand));
    }

    @Test
    public void applyCommandWithCorrelationIdAndCommandResponseWithSuffixedSameCorrelationId() {
        final var correlationIdCommand = testNameCorrelationId.getCorrelationId();
        final var dittoHeaders = DittoHeaders.newBuilder().correlationId(correlationIdCommand).build();
        final var command = RetrieveThing.of(THING_ID, dittoHeaders);
        final var correlationIdCommandResponse = correlationIdCommand.withSuffix("_" +
                UUID.randomUUID());
        final var responseDittoHeaders = DittoHeaders.newBuilder()
                .correlationId(correlationIdCommandResponse)
                .build();
        final var commandResponse = RetrieveThingResponse.of(THING_ID, JsonObject.empty(), responseDittoHeaders);
        final var underTest = CommandAndCommandResponseMatchingValidator.getInstance();

        final var validationResult = underTest.apply(command, commandResponse);

        assertThat(validationResult.isSuccess()).isTrue();
    }

    @Test
    public void applyCommandWithoutCorrelationIdAndCommandResponseWithCorrelationId() {
        final var command = RetrieveThing.of(THING_ID, DittoHeaders.empty());
        final var correlationIdCommandResponse = testNameCorrelationId.getCorrelationId();
        final var dittoHeaders = DittoHeaders.newBuilder().correlationId(correlationIdCommandResponse).build();
        final var commandResponse = RetrieveThingResponse.of(THING_ID, JsonObject.empty(), dittoHeaders);
        final var underTest = CommandAndCommandResponseMatchingValidator.getInstance();

        final var validationResult = underTest.apply(command, commandResponse);

        softly.assertThat(validationResult.asFailureOrThrow())
                .satisfies(failure -> softly.assertThat(failure.getDetailMessage())
                        .as("detail message")
                        .isEqualTo("Correlation ID of live response <%s> differs from correlation ID of command <%s>.",
                                correlationIdCommandResponse,
                                null));
    }

    @Test
    public void applyCommandAndAssociatedAcknowledgement() {
        final var dittoHeaders = getDittoHeadersWithCorrelationId();
        final var command = ModifyFeatureProperty.of(THING_ID,
                "thermostat",
                JsonPointer.of("currentTemperature"),
                JsonValue.of(21.5D),
                dittoHeaders);
        final var acknowledgement =
                Acknowledgement.of(AcknowledgementLabel.of("property-modified"), THING_ID, HttpStatus.OK, dittoHeaders);
        final var underTest = CommandAndCommandResponseMatchingValidator.getInstance();

        final var validationResult = underTest.apply(command, acknowledgement);

        assertThat(validationResult.isSuccess()).isTrue();
    }

    @Test
    public void applyCommandAndCommandResponseWithDifferentSignalDomain() {
        final var dittoHeaders = getDittoHeadersWithCorrelationId();
        final var command = RetrieveThing.of(THING_ID, dittoHeaders);
        final var commandResponse =
                SendClaimMessageResponse.of(THING_ID, Mockito.mock(Message.class), HttpStatus.OK, dittoHeaders);
        final var underTest = CommandAndCommandResponseMatchingValidator.getInstance();

        final var validationResult = underTest.apply(command, commandResponse);

        softly.assertThat(validationResult.asFailureOrThrow())
                .satisfies(failure -> softly.assertThat(failure.getDetailMessage())
                        .as("detail message")
                        .isEqualTo("Type of live response <%s> is not related to type of command <%s>.",
                                commandResponse.getType(),
                                command.getType()));
    }

    @Test
    public void applyCommandAndErrorResponseOfSameSignalDomain() {
        final var dittoHeaders = getDittoHeadersWithCorrelationId();
        final var command = RetrieveThing.of(THING_ID, dittoHeaders);
        final var dittoRuntimeException = Mockito.mock(DittoRuntimeException.class);
        Mockito.when(dittoRuntimeException.getDittoHeaders()).thenReturn(dittoHeaders);
        Mockito.when(dittoRuntimeException.getHttpStatus()).thenReturn(HttpStatus.BAD_REQUEST);
        final var thingErrorResponse = ThingErrorResponse.of(THING_ID, dittoRuntimeException);
        final var underTest = CommandAndCommandResponseMatchingValidator.getInstance();

        final var validationResult = underTest.apply(command, thingErrorResponse);

        assertThat(validationResult.isSuccess()).isTrue();
    }

    @Test
    public void applyCommandAndMismatchingCommandResponseByType() {
        final var dittoHeaders = getDittoHeadersWithCorrelationId();
        final var command = RetrieveThing.of(THING_ID, dittoHeaders);
        final var mismatchingCommandResponse =
                ModifyAttributeResponse.modified(THING_ID, JsonPointer.of("manufacturer"), dittoHeaders);
        final var underTest = CommandAndCommandResponseMatchingValidator.getInstance();

        final var validationResult = underTest.apply(command, mismatchingCommandResponse);

        softly.assertThat(validationResult.asFailureOrThrow())
                .satisfies(failure -> softly.assertThat(failure.getDetailMessage())
                        .as("detail message")
                        .isEqualTo("Type of live response <%s> is not related to type of command <%s>.",
                                mismatchingCommandResponse.getType(),
                                command.getType()));
    }

    @Test
    public void applyMatchingMessageSignals() {
        final var dittoHeaders = getDittoHeadersWithCorrelationId();
        final var messageMock = Mockito.mock(Message.class);
        Mockito.when(messageMock.getEntityId()).thenReturn(THING_ID);
        final var command = SendThingMessage.of(THING_ID, messageMock, dittoHeaders);
        final var commandResponse = SendThingMessageResponse.of(THING_ID, messageMock, HttpStatus.OK, dittoHeaders);
        final var underTest = CommandAndCommandResponseMatchingValidator.getInstance();

        final var validationResult = underTest.apply(command, commandResponse);

        assertThat(validationResult.isSuccess()).isTrue();
    }

    @Test
    public void applyMessageSignalsWithDifferentType() {
        final var dittoHeaders = getDittoHeadersWithCorrelationId();
        final var messageMock = Mockito.mock(Message.class);
        Mockito.when(messageMock.getEntityId()).thenReturn(THING_ID);
        final var sendThingMessage = SendThingMessage.of(THING_ID, messageMock, dittoHeaders);
        final var sendClaimMessageResponse =
                SendClaimMessageResponse.of(THING_ID, messageMock, HttpStatus.OK, dittoHeaders);
        final var underTest = CommandAndCommandResponseMatchingValidator.getInstance();

        final var validationResult = underTest.apply(sendThingMessage, sendClaimMessageResponse);

        softly.assertThat(validationResult.asFailureOrThrow())
                .satisfies(failure -> softly.assertThat(failure.getDetailMessage())
                        .as("detail message")
                        .isEqualTo("Type of live response <%s> is not related to type of command <%s>.",
                                sendClaimMessageResponse.getType(),
                                sendThingMessage.getType()));
    }

    @Test
    public void applySignalsWithSameEntityId() {
        final var dittoHeaders = getDittoHeadersWithCorrelationId();
        final var command = RetrieveThing.of(THING_ID, dittoHeaders);
        final var commandResponse = RetrieveThingResponse.of(THING_ID, JsonObject.empty(), dittoHeaders);
        final var underTest = CommandAndCommandResponseMatchingValidator.getInstance();

        final var validationResult = underTest.apply(command, commandResponse);

        assertThat(validationResult.isSuccess()).isTrue();
    }

    @Test
    public void applySignalsWithDifferentEntityIds() {
        final var dittoHeaders = getDittoHeadersWithCorrelationId();
        final var command = RetrieveThing.of(THING_ID, dittoHeaders);
        final var commandResponse =
                RetrieveThingResponse.of(ThingId.generateRandom(), JsonObject.empty(), dittoHeaders);
        final var underTest = CommandAndCommandResponseMatchingValidator.getInstance();

        final var validationResult = underTest.apply(command, commandResponse);

        softly.assertThat(validationResult.asFailureOrThrow())
                .satisfies(failure -> softly.assertThat(failure.getDetailMessage())
                        .as("detail message")
                        .isEqualTo("Entity ID of live response <%s> differs from entity ID of command <%s>.",
                                commandResponse.getEntityId(),
                                command.getEntityId()));
    }

    @Test
    public void applyCommandResponseWithoutEntityId() {
        final var dittoHeaders = getDittoHeadersWithCorrelationId();
        final var command = RetrieveThing.of(THING_ID, dittoHeaders);
        final var retrieveThingResponse =
                RetrieveThingResponse.of(THING_ID, JsonObject.empty(), dittoHeaders);
        final var commandResponseWithoutEntityId = Mockito.mock(CommandResponse.class);
        Mockito.when(commandResponseWithoutEntityId.getDittoHeaders()).thenReturn(dittoHeaders);
        Mockito.when(commandResponseWithoutEntityId.getResponseType()).thenReturn(ResponseType.RESPONSE);
        Mockito.when(commandResponseWithoutEntityId.getType()).thenReturn(retrieveThingResponse.getType());
        final var underTest = CommandAndCommandResponseMatchingValidator.getInstance();

        final var validationResult = underTest.apply(command, commandResponseWithoutEntityId);

        softly.assertThat(validationResult.asFailureOrThrow())
                .satisfies(failure -> softly.assertThat(failure.getDetailMessage())
                        .as("detail message")
                        .isEqualTo("Entity ID of live response <%s> differs from entity ID of command <%s>.",
                                null,
                                command.getEntityId()));
    }

    @Test
    public void applyThingCommandResponseWithDifferentResourcePath() {
        final var command = ModifyAttribute.of(THING_ID,
                JsonPointer.of("manufacturer"),
                JsonValue.of("ACME"),
                getDittoHeadersWithCorrelationId());
        final var commandResponse =
                ModifyAttributeResponse.modified(THING_ID, JsonPointer.of("serialNo"), command.getDittoHeaders());
        final var underTest = CommandAndCommandResponseMatchingValidator.getInstance();

        final var validationResult = underTest.apply(command, commandResponse);

        softly.assertThat(validationResult.asFailureOrThrow())
                .satisfies(failure -> softly.assertThat(failure.getDetailMessage())
                        .as("detail message")
                        .isEqualTo("Resource path of live response <%s> differs from resource path of command <%s>.",
                                commandResponse.getResourcePath(),
                                command.getResourcePath()));
    }

    @Test
    public void applyTooManyRequestsExceptionAsThingErrorResponse() {

        // GIVEN
        final var command = ModifyAttribute.of(THING_ID,
                JsonPointer.of("manufacturer"),
                JsonValue.of("ACME"),
                getDittoHeadersWithCorrelationId());

        final var tooManyRequestsException = TooManyRequestsException.fromMessage("I'm just a random error mate!",
                command.getDittoHeaders());
        final var thingErrorResponse = ThingErrorResponse.of(THING_ID, tooManyRequestsException);

        final var underTest = CommandAndCommandResponseMatchingValidator.getInstance();

        // WHEN
        final var validationResult = underTest.apply(command, thingErrorResponse);

        // THEN
        softly.assertThat(validationResult.isSuccess())
                .withFailMessage(() -> {
                    final var failure = validationResult.asFailureOrThrow();
                    return failure.getDetailMessage();
                })
                .isTrue();
    }

    private DittoHeaders getDittoHeadersWithCorrelationId() {
        return DittoHeaders.newBuilder().correlationId(testNameCorrelationId.getCorrelationId()).build();
    }

}
