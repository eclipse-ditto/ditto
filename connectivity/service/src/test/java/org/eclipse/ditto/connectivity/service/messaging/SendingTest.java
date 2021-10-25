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
package org.eclipse.ditto.connectivity.service.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.withSettings;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.assertj.core.api.SoftAssertions;
import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.DittoHeadersSettable;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgement;
import org.eclipse.ditto.base.model.signals.commands.CommandResponse;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.api.OutboundSignal;
import org.eclipse.ditto.connectivity.model.GenericTarget;
import org.eclipse.ditto.connectivity.model.MessageSendingFailedException;
import org.eclipse.ditto.connectivity.model.Target;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.ConnectionMonitor;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLoggingAdapter;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.placeholders.ExpressionResolver;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.WithThingId;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Unit test for {@link Sending}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class SendingTest {

    private static final AcknowledgementLabel ACKNOWLEDGEMENT_LABEL = AcknowledgementLabel.of("twin-persisted");

    @Rule
    public final TestName testName = new TestName();

    @Mock private OutboundSignal.Mapped mappedOutboundSignal;
    @Mock private ExternalMessage externalMessage;
    @Mock private GenericTarget genericTarget;
    @Mock private ConnectionMonitor publishedMonitor;
    @Mock private ConnectionMonitor acknowledgedMonitor;
    @Mock private ConnectionMonitor droppedMonitor;
    @Mock private Target autoAckTarget;
    @Mock private ExpressionResolver connectionIdResolver;
    @Mock private ThreadSafeDittoLoggingAdapter logger;

    private DittoHeaders dittoHeaders;
    private SendingContext sendingContext;
    private ExceptionToAcknowledgementConverter exceptionConverter;

    @Before
    public void setUp() {
        dittoHeaders = DittoHeaders.newBuilder().correlationId(testName.getMethodName()).build();

        sendingContext = SendingContext.newBuilder()
                .mappedOutboundSignal(mappedOutboundSignal)
                .externalMessage(externalMessage)
                .genericTarget(genericTarget)
                .publishedMonitor(publishedMonitor)
                .acknowledgedMonitor(acknowledgedMonitor)
                .autoAckTarget(autoAckTarget)
                .droppedMonitor(droppedMonitor)
                .build();

        Mockito.when(externalMessage.getInternalHeaders()).thenReturn(dittoHeaders);
        Mockito.when(logger.withCorrelationId(Mockito.nullable(CharSequence.class))).thenReturn(logger);
        Mockito.when(logger.withCorrelationId(Mockito.any(WithDittoHeaders.class))).thenReturn(logger);

        exceptionConverter = DefaultExceptionToAcknowledgementConverter.getInstance();
    }

    @Test
    public void createInstanceWithNullSendingContext() {
        assertThatNullPointerException()
                .isThrownBy(
                        () -> new Sending(null, CompletableFuture.completedStage(null), connectionIdResolver, logger))
                .withMessage("The sendingContext must not be null!")
                .withNoCause();
    }

    @Test
    public void createInstanceWithNullFuture() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Sending(sendingContext, null, connectionIdResolver, logger))
                .withMessage("The futureResult must not be null!")
                .withNoCause();
    }

    @Test
    public void createInstanceWithNullLogger() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Sending(sendingContext, CompletableFuture.completedStage(null),
                        connectionIdResolver, null))
                .withMessage("The logger must not be null!")
                .withNoCause();
    }

    @Test
    public void acknowledgeAndMonitorNullCommandResponseWhenShouldAcknowledge() {
        Mockito.when(autoAckTarget.getIssuedAcknowledgementLabel()).thenReturn(Optional.of(ACKNOWLEDGEMENT_LABEL));
        final var source = Mockito.mock(WithThingId.class, withSettings()
                .extraInterfaces(DittoHeadersSettable.class, Signal.class));
        final var thingId = ThingId.generateRandom();
        Mockito.when(source.getEntityId()).thenReturn(thingId);
        Mockito.when(((DittoHeadersSettable)source).getDittoHeaders()).thenReturn(dittoHeaders);
        Mockito.when(mappedOutboundSignal.getSource()).thenReturn((Signal)source);
        final Acknowledgement expectedResponse =
                exceptionConverter.convertException(MessageSendingFailedException.newBuilder()
                                .message("Message sending terminated without the expected acknowledgement.")
                                .description("Please contact the service team.")
                                .build(), ACKNOWLEDGEMENT_LABEL, thingId,
                        dittoHeaders);
        final var expectedException = MessageSendingFailedException.newBuilder()
                .message("Message sending terminated without the expected acknowledgement.")
                .description("Please contact the service team.")
                .build();
        final Sending underTest = new Sending(sendingContext, CompletableFuture.completedStage(null),
                connectionIdResolver, logger);

        final Optional<CompletionStage<CommandResponse>> result = underTest.monitorAndAcknowledge(exceptionConverter);

        Mockito.verify(publishedMonitor).failure(externalMessage, expectedException);
        Mockito.verify(acknowledgedMonitor)
                .failure(eq(externalMessage), Mockito.any(DittoRuntimeException.class));
        assertThat(result).hasValueSatisfying(
                resultFuture -> assertThat(resultFuture).isCompletedWithValue(expectedResponse));
    }

    @Test
    public void monitorAcknowledgementSendSuccessKeepOriginalResponse() {
        final var acknowledgement = Mockito.mock(Acknowledgement.class);
        final SendResult sendResult = new SendResult(acknowledgement, DittoHeaders.empty());
        final var acknowledgementStatus = HttpStatus.ACCEPTED;
        Mockito.when(acknowledgement.getHttpStatus()).thenReturn(acknowledgementStatus);
        final Sending underTest =
                new Sending(sendingContext, CompletableFuture.completedStage(sendResult), connectionIdResolver,
                        logger);

        final Optional<CompletionStage<CommandResponse>> result = underTest.monitorAndAcknowledge(exceptionConverter);

        Mockito.verify(acknowledgedMonitor).success(eq(externalMessage));
        Mockito.verify(publishedMonitor).success(eq(externalMessage));
        assertThat(result)
                .hasValueSatisfying(resultFuture -> assertThat(resultFuture).isCompletedWithValue(acknowledgement));
    }

    @Test
    public void monitorAcknowledgementSendSuccessInCaseOfHandledException() {
        final var acknowledgement = Mockito.mock(Acknowledgement.class);
        final SendResult sendResult = new SendResult(acknowledgement, DittoHeaders.empty());
        final var acknowledgementPayload = JsonObject.newBuilder().set("foo", "bar").build();
        final var acknowledgementStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        Mockito.when(acknowledgement.getHttpStatus()).thenReturn(acknowledgementStatus);
        Mockito.when(acknowledgement.getEntity()).thenReturn(Optional.of(acknowledgementPayload));
        Mockito.when(acknowledgement.getLabel()).thenReturn(ACKNOWLEDGEMENT_LABEL);
        final var expectedException = MessageSendingFailedException.newBuilder()
                .httpStatus(acknowledgementStatus)
                .message("Received negative acknowledgement for label <" + ACKNOWLEDGEMENT_LABEL + ">.")
                .description("Payload: " + acknowledgementPayload)
                .build();
        final Sending underTest =
                new Sending(sendingContext, CompletableFuture.completedStage(sendResult), connectionIdResolver,
                        logger);

        final Optional<CompletionStage<CommandResponse>> result = underTest.monitorAndAcknowledge(exceptionConverter);

        Mockito.verify(publishedMonitor).success(externalMessage);
        Mockito.verify(acknowledgedMonitor).failure(externalMessage, expectedException);
        assertThat(result)
                .hasValueSatisfying(resultFuture -> assertThat(resultFuture).isCompletedWithValue(acknowledgement));
    }

    @Test
    public void monitorAcknowledgementSendFailureInCaseOfUnhandledException() {
        final var source = Mockito.mock(WithThingId.class, withSettings()
                        .extraInterfaces(Signal.class, DittoHeadersSettable.class));
        final var thingId = ThingId.generateRandom();
        Mockito.when(source.getEntityId()).thenReturn(thingId);
        Mockito.when(((DittoHeadersSettable<?>)source).getDittoHeaders()).thenReturn(dittoHeaders);
        Mockito.when(mappedOutboundSignal.getSource()).thenReturn((Signal)source);
        Mockito.when(autoAckTarget.getIssuedAcknowledgementLabel()).thenReturn(Optional.of(ACKNOWLEDGEMENT_LABEL));
        final var thrownException = new IllegalStateException("Test");
        final var acknowledgementPayload = JsonObject.newBuilder()
                .set("message", "Encountered <IllegalStateException>.")
                .set("description", "Test")
                .build();
        final var acknowledgementStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        final var expectedException = MessageSendingFailedException.newBuilder()
                .httpStatus(acknowledgementStatus)
                .message("Received negative acknowledgement for label <" + ACKNOWLEDGEMENT_LABEL + ">.")
                .description("Payload: " + acknowledgementPayload)
                .build();
        final CompletableFuture<SendResult> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(thrownException);
        final Sending underTest = new Sending(sendingContext, failedFuture, connectionIdResolver, logger);

        final Optional<CompletionStage<CommandResponse>> result = underTest.monitorAndAcknowledge(exceptionConverter);

        Mockito.verify(publishedMonitor).exception(externalMessage, thrownException);
        Mockito.verify(acknowledgedMonitor).failure(externalMessage, expectedException);
        assertThat(result).hasValueSatisfying(
                resultFuture -> assertThat(resultFuture).isCompletedWithValueMatching(response -> {
                    final SoftAssertions softly = new SoftAssertions();
                    softly.assertThat(response).isInstanceOf(Acknowledgement.class);
                    final Acknowledgement ack = (Acknowledgement) response;
                    softly.assertThat(ack.getLabel().toString()).isEqualTo(ACKNOWLEDGEMENT_LABEL.toString());
                    softly.assertThat(ack.getEntity()).contains(acknowledgementPayload);
                    softly.assertThat(ack.getEntityId().toString()).isEqualTo(thingId.toString());
                    softly.assertThat(ack.getHttpStatus()).isEqualTo(acknowledgementStatus);
                    softly.assertAll();
                    return true;
                }));
    }

    @Test
    public void monitorLiveResponseSendSuccessKeepOriginalResponse() {
        final var issuedAckLabel = DittoAcknowledgementLabel.LIVE_RESPONSE;
        Mockito.when(autoAckTarget.getIssuedAcknowledgementLabel()).thenReturn(Optional.of(issuedAckLabel));
        final CommandResponse<?> commandResponse = Mockito.mock(CommandResponse.class);
        final SendResult sendResult = new SendResult(commandResponse, DittoHeaders.empty());
        final var commandResponseStatus = HttpStatus.ACCEPTED;
        Mockito.when(commandResponse.getHttpStatus()).thenReturn(commandResponseStatus);
        final Sending underTest =
                new Sending(sendingContext, CompletableFuture.completedStage(sendResult), connectionIdResolver,
                        logger);

        final Optional<CompletionStage<CommandResponse>> result = underTest.monitorAndAcknowledge(exceptionConverter);

        Mockito.verify(acknowledgedMonitor).success(eq(externalMessage));
        Mockito.verify(publishedMonitor).success(eq(externalMessage));
        assertThat(result)
                .hasValueSatisfying(resultFuture -> assertThat(resultFuture).isCompletedWithValue(commandResponse));
    }

    @Test
    public void monitorLiveResponseSendFailureKeepOriginalResponse() {
        final var issuedAckLabel = DittoAcknowledgementLabel.LIVE_RESPONSE;
        Mockito.when(autoAckTarget.getIssuedAcknowledgementLabel()).thenReturn(Optional.of(issuedAckLabel));
        final CommandResponse<?> commandResponse = Mockito.mock(CommandResponse.class);
        final SendResult sendResult = new SendResult(commandResponse, DittoHeaders.empty());
        final var commandResponseStatus = HttpStatus.CONFLICT;
        Mockito.when(commandResponse.getHttpStatus()).thenReturn(commandResponseStatus);
        final Sending underTest =
                new Sending(sendingContext, CompletableFuture.completedStage(sendResult), connectionIdResolver,
                        logger);

        final Optional<CompletionStage<CommandResponse>> result = underTest.monitorAndAcknowledge(exceptionConverter);

        Mockito.verify(publishedMonitor).success(externalMessage);
        Mockito.verify(acknowledgedMonitor).success(externalMessage);
        assertThat(result)
                .hasValueSatisfying(resultFuture -> assertThat(resultFuture).isCompletedWithValue(commandResponse));
    }

    @Test
    public void monitorNoAckAsShouldNotAcknowledgeAndOriginalResponseIsNull() {
        sendingContext = SendingContext.newBuilder()
                .mappedOutboundSignal(mappedOutboundSignal)
                .externalMessage(externalMessage)
                .genericTarget(genericTarget)
                .publishedMonitor(publishedMonitor)
                .droppedMonitor(droppedMonitor)
                .build();
        final CommandResponse<?> commandResponse = null;
        final SendResult sendResult = new SendResult(commandResponse, DittoHeaders.empty());
        final Sending underTest =
                new Sending(sendingContext, CompletableFuture.completedStage(sendResult), connectionIdResolver,
                        logger);

        final Optional<CompletionStage<CommandResponse>> result = underTest.monitorAndAcknowledge(exceptionConverter);

        Mockito.verifyNoInteractions(acknowledgedMonitor);
        assertThat(result)
                .hasValueSatisfying(resultFuture -> assertThat(resultFuture).isCompletedWithValue(commandResponse));
    }

    @Test
    public void monitorAndAcknowledgeWhenFutureResponseTerminatedExceptionallyAndNoAckLabelIssued() {
        final var rootCause = new IllegalStateException("A foo must not bar! Never ever!");
        final Sending underTest =
                new Sending(sendingContext, CompletableFuture.failedStage(rootCause), connectionIdResolver, logger);

        final Optional<CompletionStage<CommandResponse>> result = underTest.monitorAndAcknowledge(exceptionConverter);

        Mockito.verifyNoInteractions(acknowledgedMonitor);
        Mockito.verify(publishedMonitor).exception(eq(externalMessage), eq(rootCause));
        Mockito.verify(logger).withCorrelationId(testName.getMethodName());
        assertThat(result).hasValueSatisfying(resultFuture -> assertThat(resultFuture).isCompletedWithValue(null));
    }

}
