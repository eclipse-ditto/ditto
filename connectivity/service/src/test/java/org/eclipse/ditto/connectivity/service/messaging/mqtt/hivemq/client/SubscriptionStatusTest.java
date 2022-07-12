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
package org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Random;
import java.util.function.Supplier;

import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.subscribe.GenericMqttSubAckStatus;
import org.junit.BeforeClass;
import org.junit.Test;

import com.hivemq.client.mqtt.datatypes.MqttTopicFilter;
import com.hivemq.client.mqtt.mqtt3.message.subscribe.suback.Mqtt3SubAckReturnCode;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.suback.Mqtt5SubAckReasonCode;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link SubscriptionStatus}.
 */
public final class SubscriptionStatusTest {
    
    private static final MqttTopicFilter MQTT_TOPIC_FILTER = MqttTopicFilter.of("source/+/+");

    private static Supplier<GenericMqttSubAckStatus> genericMqttSubAckStatusSupplier;

    @BeforeClass
    public static void beforeClass() {
        final var subAckCodeClasses = new Class[]{Mqtt3SubAckReturnCode.class, Mqtt5SubAckReasonCode.class};
        final var mqtt3SubAckReturnCodes = Mqtt3SubAckReturnCode.values();
        final var mqtt5SubAckReasonCodes = Mqtt5SubAckReasonCode.values();
        final var random = new Random();
        genericMqttSubAckStatusSupplier = () -> {
            final GenericMqttSubAckStatus result;
            final var subAckCodeClass = subAckCodeClasses[random.nextInt(subAckCodeClasses.length)];
            if (Mqtt3SubAckReturnCode.class.equals(subAckCodeClass)) {
                result = GenericMqttSubAckStatus.ofMqtt3SubAckReturnCode(
                        mqtt3SubAckReturnCodes[random.nextInt(mqtt3SubAckReturnCodes.length)]
                );
            } else {
                result = GenericMqttSubAckStatus.ofMqtt5SubAckReasonCode(
                        mqtt5SubAckReasonCodes[random.nextInt(mqtt5SubAckReasonCodes.length)]
                );
            }
            return result;
        };
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(SubscriptionStatus.class, areImmutable(), provided(MqttTopicFilter.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(SubscriptionStatus.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void newInstanceWithNullMqttTopicFilterThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> SubscriptionStatus.newInstance(null, genericMqttSubAckStatusSupplier.get()))
                .withMessage("The mqttTopicFilter must not be null!")
                .withNoCause();
    }

    @Test
    public void newInstanceWithNullGenericMqttSubAckThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> SubscriptionStatus.newInstance(MQTT_TOPIC_FILTER, null))
                .withMessage("The genericMqttSubAckStatus must not be null!")
                .withNoCause();
    }

    @Test
    public void getMqttTopicFilterReturnsExpected() {
        final var underTest = SubscriptionStatus.newInstance(MQTT_TOPIC_FILTER, genericMqttSubAckStatusSupplier.get());

        assertThat(underTest.getMqttTopicFilter()).isEqualTo(MQTT_TOPIC_FILTER);
    }

    @Test
    public void getGenericMqttSubAckStatusReturnsExpected() {
        final var genericMqttSubAckStatus = genericMqttSubAckStatusSupplier.get();
        final var underTest = SubscriptionStatus.newInstance(MQTT_TOPIC_FILTER, genericMqttSubAckStatus);

        assertThat(underTest.getGenericMqttSubAckStatus()).isEqualTo(genericMqttSubAckStatus);
    }

}