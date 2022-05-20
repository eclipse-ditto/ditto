/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.publishing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.correlationid.TestNameCorrelationId;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgement;
import org.eclipse.ditto.connectivity.model.Target;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.publish.GenericMqttPublish;
import org.eclipse.ditto.placeholders.ExpressionResolver;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThing;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link MqttPublishingContext}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class MqttPublishingContextTest {

    private static final GenericMqttPublish GENERIC_MQTT_PUBLISH =
            GenericMqttPublish.ofMqtt3Publish(Mockito.mock(Mqtt3Publish.class));

    @Rule
    public final TestNameCorrelationId testNameCorrelationId = TestNameCorrelationId.newInstance();

    @Mock
    private Signal<?> signal;

    @Mock
    private ExpressionResolver connectionIdResolver;

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(MqttPublishingContext.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void getGenericMqttPublishReturnsExpected() {
        final var underTest = MqttPublishingContext.newInstance(GENERIC_MQTT_PUBLISH,
                signal,
                null,
                connectionIdResolver);

        assertThat(underTest.getGenericMqttPublish()).isEqualTo(GENERIC_MQTT_PUBLISH);
    }

    @Test
    public void getSignalDittoHeadersReturnsExpected() {
        final var dittoHeaders =
                DittoHeaders.newBuilder().correlationId(testNameCorrelationId.getCorrelationId()).build();
        Mockito.when(signal.getDittoHeaders()).thenReturn(dittoHeaders);
        final var underTest = MqttPublishingContext.newInstance(GENERIC_MQTT_PUBLISH,
                signal,
                null,
                connectionIdResolver);

        assertThat(underTest.getSignalDittoHeaders()).isEqualTo(dittoHeaders);
    }

    @Test
    public void getAutoAcknowledgementOnInstanceWithoutAutoAckTargetReturnsEmptyOptional() {
        final var underTest = MqttPublishingContext.newInstance(GENERIC_MQTT_PUBLISH,
                signal,
                null,
                connectionIdResolver);

        assertThat(underTest.getAutoAcknowledgement()).isEmpty();
    }

    @Test
    public void getAutoAcknowledgementOnInstanceWithAutoAckTargetReturnsExpectedOptional() {
        final var retrieveThing = RetrieveThing.of(ThingId.generateRandom(),
                DittoHeaders.newBuilder().correlationId(testNameCorrelationId.getCorrelationId()).build());
        final var issuedAcknowledgementLabel = Mockito.mock(AcknowledgementLabel.class);
        Mockito.when(issuedAcknowledgementLabel.isFullyResolved()).thenReturn(true);
        final var autoAckTarget = Mockito.mock(Target.class);
        Mockito.when(autoAckTarget.getIssuedAcknowledgementLabel()).thenReturn(Optional.of(issuedAcknowledgementLabel));

        final var underTest = MqttPublishingContext.newInstance(GENERIC_MQTT_PUBLISH,
                retrieveThing,
                autoAckTarget,
                connectionIdResolver);

        assertThat(underTest.getAutoAcknowledgement())
                .hasValue(Acknowledgement.of(issuedAcknowledgementLabel,
                        retrieveThing.getEntityId(),
                        HttpStatus.OK,
                        retrieveThing.getDittoHeaders()));
    }

    @Test
    public void getSendResultCompletableFutureOnNewInstanceReturnsPristineCompletableFuture() {
        final var underTest = MqttPublishingContext.newInstance(GENERIC_MQTT_PUBLISH,
                signal,
                null,
                connectionIdResolver);

        final var sendResultCompletableFuture = underTest.getSendResultCompletableFuture();

        assertThat(sendResultCompletableFuture).isNotDone();
    }

}