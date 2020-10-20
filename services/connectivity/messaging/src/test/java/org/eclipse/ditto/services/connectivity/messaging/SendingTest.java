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
package org.eclipse.ditto.services.connectivity.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.model.base.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.headers.WithDittoHeaders;
import org.eclipse.ditto.model.connectivity.GenericTarget;
import org.eclipse.ditto.model.connectivity.MessageSendingFailedException;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.model.placeholders.ExpressionResolver;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.ConnectionMonitor;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.models.connectivity.OutboundSignal;
import org.eclipse.ditto.services.utils.akka.logging.ThreadSafeDittoLoggingAdapter;
import org.eclipse.ditto.signals.acks.base.Acknowledgement;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.base.CommandResponse;
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
                .isThrownBy(() -> new Sending(null, CompletableFuture.completedStage(null), connectionIdResolver, logger))
                .withMessage("The sendingContext must not be null!")
                .withNoCause();
    }

    @Test
    public void createInstanceWithNullFuture() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Sending(sendingContext, null, connectionIdResolver, logger))
                .withMessage("The futureResponse must not be null!")
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
        final var source = Mockito.mock(Signal.class);
        final var thingId = ThingId.generateRandom();
        Mockito.when(source.getEntityId()).thenReturn(thingId);
        Mockito.when(source.getDittoHeaders()).thenReturn(dittoHeaders);
        Mockito.when(mappedOutboundSignal.getSource()).thenReturn(source);
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
                .failure(Mockito.eq(externalMessage), Mockito.any(DittoRuntimeException.class));
        assertThat(result).hasValueSatisfying(
                resultFuture -> assertThat(resultFuture).isCompletedWithValue(expectedResponse));
    }

    @Test
    public void monitorAcknowledgementSendSuccessKeepOriginalResponse() {
        final var acknowledgement = Mockito.mock(Acknowledgement.class);
        final var acknowledgementStatusCode = HttpStatusCode.ACCEPTED;
        Mockito.when(acknowledgement.getStatusCode()).thenReturn(acknowledgementStatusCode);
        final Sending underTest =
                new Sending(sendingContext, CompletableFuture.completedStage(acknowledgement), connectionIdResolver,
                        logger);

        final Optional<CompletionStage<CommandResponse>> result = underTest.monitorAndAcknowledge(exceptionConverter);

        Mockito.verify(acknowledgedMonitor).success(externalMessage);
        Mockito.verify(publishedMonitor).success(externalMessage);
        assertThat(result)
                .hasValueSatisfying(resultFuture -> assertThat(resultFuture).isCompletedWithValue(acknowledgement));
    }

    @Test
    public void monitorAcknowledgementSendFailureKeepOriginalResponse() {
        final var acknowledgement = Mockito.mock(Acknowledgement.class);
        final var acknowledgementPayload = JsonObject.newBuilder().set("foo", "bar").build();
        final var acknowledgementStatusCode = HttpStatusCode.INTERNAL_SERVER_ERROR;
        Mockito.when(acknowledgement.getStatusCode()).thenReturn(acknowledgementStatusCode);
        Mockito.when(acknowledgement.getEntity()).thenReturn(Optional.of(acknowledgementPayload));
        final var expectedException = MessageSendingFailedException.newBuilder()
                .statusCode(acknowledgementStatusCode)
                .message("Received negative acknowledgement for label <" + ACKNOWLEDGEMENT_LABEL + ">.")
                .description("Payload: " + acknowledgementPayload)
                .build();
        final Sending underTest =
                new Sending(sendingContext, CompletableFuture.completedStage(acknowledgement), connectionIdResolver,
                        logger);

        final Optional<CompletionStage<CommandResponse>> result = underTest.monitorAndAcknowledge(exceptionConverter);

        Mockito.verifyNoInteractions(publishedMonitor);
        Mockito.verify(acknowledgedMonitor).failure(externalMessage, expectedException);
        assertThat(result)
                .hasValueSatisfying(resultFuture -> assertThat(resultFuture).isCompletedWithValue(acknowledgement));
    }

    @Test
    public void monitorLiveResponseSendSuccessKeepOriginalResponse() {
        final var issuedAckLabel = DittoAcknowledgementLabel.LIVE_RESPONSE;
        Mockito.when(autoAckTarget.getIssuedAcknowledgementLabel()).thenReturn(Optional.of(issuedAckLabel));
        final CommandResponse<?> commandResponse = Mockito.mock(CommandResponse.class);
        final var commandResponseStatusCode = HttpStatusCode.ACCEPTED;
        Mockito.when(commandResponse.getStatusCode()).thenReturn(commandResponseStatusCode);
        final Sending underTest =
                new Sending(sendingContext, CompletableFuture.completedStage(commandResponse), connectionIdResolver,
                        logger);

        final Optional<CompletionStage<CommandResponse>> result = underTest.monitorAndAcknowledge(exceptionConverter);

        Mockito.verify(acknowledgedMonitor).success(externalMessage);
        Mockito.verify(publishedMonitor).success(externalMessage);
        assertThat(result)
                .hasValueSatisfying(resultFuture -> assertThat(resultFuture).isCompletedWithValue(commandResponse));
    }

    @Test
    public void monitorLiveResponseSendFailureKeepOriginalResponse() {
        final var issuedAckLabel = DittoAcknowledgementLabel.LIVE_RESPONSE;
        Mockito.when(autoAckTarget.getIssuedAcknowledgementLabel()).thenReturn(Optional.of(issuedAckLabel));
        final CommandResponse<?> commandResponse = Mockito.mock(CommandResponse.class);
        final var commandResponsePayload = JsonObject.newBuilder().set("foo", "bar").build();
        final var commandResponseStatusCode = HttpStatusCode.CONFLICT;
        Mockito.when(commandResponse.getStatusCode()).thenReturn(commandResponseStatusCode);
        Mockito.when(commandResponse.toJson()).thenReturn(commandResponsePayload);
        final var expectedException = MessageSendingFailedException.newBuilder()
                .statusCode(commandResponseStatusCode)
                .message("Received negative acknowledgement for label <" + issuedAckLabel + ">.")
                .description("Payload: " + commandResponsePayload)
                .build();
        final Sending underTest =
                new Sending(sendingContext, CompletableFuture.completedStage(commandResponse), connectionIdResolver,
                        logger);

        final Optional<CompletionStage<CommandResponse>> result = underTest.monitorAndAcknowledge(exceptionConverter);

        Mockito.verifyNoInteractions(publishedMonitor);
        Mockito.verify(acknowledgedMonitor).failure(externalMessage, expectedException);
        assertThat(result)
                .hasValueSatisfying(resultFuture -> assertThat(resultFuture).isCompletedWithValue(commandResponse));
    }

    @Test
    public void monitorNothingAsShouldNotAcknowledgeAndOriginalResponseIsNull() {
        sendingContext = SendingContext.newBuilder()
                .mappedOutboundSignal(mappedOutboundSignal)
                .externalMessage(externalMessage)
                .genericTarget(genericTarget)
                .publishedMonitor(publishedMonitor)
                .droppedMonitor(droppedMonitor)
                .build();
        final CommandResponse<?> commandResponse = null;
        final Sending underTest =
                new Sending(sendingContext, CompletableFuture.completedStage(commandResponse), connectionIdResolver,
                        logger);

        final Optional<CompletionStage<CommandResponse>> result = underTest.monitorAndAcknowledge(exceptionConverter);

        Mockito.verifyNoInteractions(publishedMonitor, acknowledgedMonitor);
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
        Mockito.verify(publishedMonitor).exception(Mockito.eq(externalMessage), Mockito.eq(rootCause));
        Mockito.verify(logger).withCorrelationId(testName.getMethodName());
        assertThat(result).hasValueSatisfying(resultFuture -> assertThat(resultFuture).isCompletedWithValue(null));
    }

}