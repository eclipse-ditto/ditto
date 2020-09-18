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

import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.GenericTarget;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.services.connectivity.messaging.monitoring.ConnectionMonitor;
import org.eclipse.ditto.services.models.connectivity.ExternalMessage;
import org.eclipse.ditto.services.models.connectivity.OutboundSignal;
import org.eclipse.ditto.services.utils.akka.logging.ThreadSafeDittoLoggingAdapter;
import org.eclipse.ditto.signals.acks.base.Acknowledgement;
import org.eclipse.ditto.signals.base.Signal;
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
    @Mock private ConnectionMonitor connectionMonitor;
    @Mock private Target autoAckTarget;
    @Mock private ThreadSafeDittoLoggingAdapter logger;

    private DittoHeaders dittoHeaders;
    private SendingContext sendingContext;

    @Before
    public void setUp() {
        dittoHeaders = DittoHeaders.newBuilder().correlationId(testName.getMethodName()).build();

        sendingContext = SendingContext.newBuilder()
                .mappedOutboundSignal(mappedOutboundSignal)
                .externalMessage(externalMessage)
                .genericTarget(genericTarget)
                .publishedMonitor(publishedMonitor)
                .acknowledgedMonitor(connectionMonitor)
                .droppedMonitor(connectionMonitor)
                .autoAckTarget(autoAckTarget)
                .build();
    }

    @Test
    public void createInstanceWithNullSendingContext() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Sending(null, CompletableFuture.completedStage(null), logger))
                .withMessage("The sendingContext must not be null!")
                .withNoCause();
    }

    @Test
    public void createInstanceWithNullFuture() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Sending(sendingContext, null, logger))
                .withMessage("The future must not be null!")
                .withNoCause();
    }

    @Test
    public void createInstanceWithNullLogger() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Sending(sendingContext, CompletableFuture.completedStage(null), null))
                .withMessage("The logger must not be null!")
                .withNoCause();
    }

    @Test
    public void monitorAndAcknowledgeIfNoSendSuccessAndShouldNotAcknowledge() {
        sendingContext = SendingContext.newBuilder()
                .mappedOutboundSignal(mappedOutboundSignal)
                .externalMessage(externalMessage)
                .genericTarget(genericTarget)
                .publishedMonitor(publishedMonitor)
                .droppedMonitor(connectionMonitor)
                .build();
        final DefaultExceptionToAcknowledgementConverter exceptionConverter =
                DefaultExceptionToAcknowledgementConverter.getInstance();
        final Acknowledgement acknowledgement = Mockito.mock(Acknowledgement.class);
        final Sending underTest =
                new Sending(sendingContext, CompletableFuture.completedStage(acknowledgement), logger);

        final Optional<CompletionStage<Acknowledgement>> result =
                underTest.monitorAndAcknowledge(exceptionConverter);

        Mockito.verify(publishedMonitor).success(externalMessage);
        assertThat(result).hasValueSatisfying(resultFuture -> assertThat(resultFuture).isCompletedWithValue(null));
    }

    @Test
    public void monitorAndAcknowledgeIfNoSendSuccessButShouldNotAcknowledge() {
        final DefaultExceptionToAcknowledgementConverter exceptionConverter =
                DefaultExceptionToAcknowledgementConverter.getInstance();
        final Acknowledgement acknowledgement = Mockito.mock(Acknowledgement.class);
        Mockito.when(acknowledgement.getStatusCode()).thenReturn(HttpStatusCode.CONFLICT);
        Mockito.when(acknowledgement.getLabel()).thenReturn(ACKNOWLEDGEMENT_LABEL);
        final Sending underTest =
                new Sending(sendingContext, CompletableFuture.completedStage(acknowledgement), logger);

        final Optional<CompletionStage<Acknowledgement>> result =
                underTest.monitorAndAcknowledge(exceptionConverter);

        Mockito.verifyNoInteractions(publishedMonitor);
        assertThat(result).hasValueSatisfying(
                resultFuture -> assertThat(resultFuture).isCompletedWithValue(acknowledgement));
    }

    @Test
    public void monitorAndAcknowledgeNullAcknowledgement() {
        Mockito.when(autoAckTarget.getIssuedAcknowledgementLabel()).thenReturn(Optional.of(ACKNOWLEDGEMENT_LABEL));
        final Signal source = Mockito.mock(Signal.class);
        final ThingId thingId = ThingId.generateRandom();
        Mockito.when(source.getEntityId()).thenReturn(thingId);
        Mockito.when(source.getDittoHeaders()).thenReturn(dittoHeaders);
        Mockito.when(mappedOutboundSignal.getSource()).thenReturn(source);
        final DefaultExceptionToAcknowledgementConverter exceptionConverter =
                DefaultExceptionToAcknowledgementConverter.getInstance();
        final Acknowledgement expectedAcknowledgement =
                exceptionConverter.convertException(Sending.NULL_ACK_EXCEPTION, ACKNOWLEDGEMENT_LABEL, thingId,
                        dittoHeaders);
        final Sending underTest = new Sending(sendingContext, CompletableFuture.completedStage(null), logger);

        final Optional<CompletionStage<Acknowledgement>> result =
                underTest.monitorAndAcknowledge(exceptionConverter);

        Mockito.verify(publishedMonitor).failure(externalMessage, Sending.NULL_ACK_EXCEPTION);
        assertThat(result).hasValueSatisfying(
                resultFuture -> assertThat(resultFuture).isCompletedWithValue(expectedAcknowledgement));
    }

}