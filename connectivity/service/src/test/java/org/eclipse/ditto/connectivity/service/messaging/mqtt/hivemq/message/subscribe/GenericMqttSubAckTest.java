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
package org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.subscribe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.List;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.mockito.Mockito;

import com.hivemq.client.mqtt.mqtt3.message.subscribe.suback.Mqtt3SubAck;
import com.hivemq.client.mqtt.mqtt3.message.subscribe.suback.Mqtt3SubAckReturnCode;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.suback.Mqtt5SubAck;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.suback.Mqtt5SubAckReasonCode;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link GenericMqttSubAck}.
 */
public final class GenericMqttSubAckTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(GenericMqttSubAck.class,
                areImmutable(),
                assumingFields("genericMqttSubAckStatuses").areSafelyCopiedUnmodifiableCollectionsWithImmutableElements());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(GenericMqttSubAck.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void ofMqtt3SubAckWithNullMqtt3SubAckThrowsException() {
        Assertions.assertThatNullPointerException()
                .isThrownBy(() -> GenericMqttSubAck.ofMqtt3SubAck(null))
                .withMessage("The mqtt3SubAck must not be null!")
                .withNoCause();
    }

    @Test
    public void ofMqtt3SubAckReturnsExpectedInstance() {
        final var mqtt3SubAck = Mockito.mock(Mqtt3SubAck.class);
        final var mqtt3SubAckReturnCodes = List.of(Mqtt3SubAckReturnCode.values());
        Mockito.when(mqtt3SubAck.getReturnCodes()).thenReturn(mqtt3SubAckReturnCodes);

        final var underTest = GenericMqttSubAck.ofMqtt3SubAck(mqtt3SubAck);

        assertThat(underTest.getGenericMqttSubAckStatuses())
                .hasSameElementsAs(mqtt3SubAckReturnCodes.stream()
                        .map(GenericMqttSubAckStatus::ofMqtt3SubAckReturnCode)
                        .toList());
    }

    @Test
    public void ofMqtt5SubAckWithNullMqtt5SubAckThrowsException() {
        Assertions.assertThatNullPointerException()
                .isThrownBy(() -> GenericMqttSubAck.ofMqtt5SubAck(null))
                .withMessage("The mqtt5SubAck must not be null!")
                .withNoCause();
    }

    @Test
    public void ofMqtt5SubAckReturnsExpectedInstance() {
        final var mqtt5SubAck = Mockito.mock(Mqtt5SubAck.class);
        final var mqtt5SubAckReasonCodes = List.of(Mqtt5SubAckReasonCode.values());
        Mockito.when(mqtt5SubAck.getReasonCodes()).thenReturn(mqtt5SubAckReasonCodes);

        final var underTest = GenericMqttSubAck.ofMqtt5SubAck(mqtt5SubAck);

        assertThat(underTest.getGenericMqttSubAckStatuses())
                .hasSameElementsAs(mqtt5SubAckReasonCodes.stream()
                        .map(GenericMqttSubAckStatus::ofMqtt5SubAckReasonCode)
                        .toList());
    }

}