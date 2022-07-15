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
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.datatypes.MqttTopicFilter;
import com.hivemq.client.mqtt.mqtt3.message.subscribe.Mqtt3Subscription;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5Subscription;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link GenericMqttSubscription}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class GenericMqttSubscriptionTest {

    private static final MqttTopicFilter MQTT_TOPIC_FILTER = MqttTopicFilter.of("source/status");

    @Test
    public void assertImmutability() {
        assertInstancesOf(GenericMqttSubscription.class,
                areImmutable(),
                provided(MqttTopicFilter.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(GenericMqttSubscription.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void newInstanceWithNullMqttTopicFilterThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> GenericMqttSubscription.newInstance(null, MqttQos.AT_LEAST_ONCE))
                .withMessage("The mqttTopicFilter must not be null!")
                .withNoCause();
    }

    @Test
    public void newInstanceWithNullMqttQosThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> GenericMqttSubscription.newInstance(MQTT_TOPIC_FILTER, null))
                .withMessage("The mqttQos must not be null!")
                .withNoCause();
    }

    @Test
    public void getMqttTopicFilterReturnsExpected() {
        final var underTest = GenericMqttSubscription.newInstance(MQTT_TOPIC_FILTER, MqttQos.EXACTLY_ONCE);

        assertThat(underTest.getMqttTopicFilter()).isEqualTo(MQTT_TOPIC_FILTER);
    }

    @Test
    public void getMqttQosReturnsExpected() {
        final var mqttQos = MqttQos.AT_MOST_ONCE;
        final var underTest = GenericMqttSubscription.newInstance(MQTT_TOPIC_FILTER, mqttQos);

        assertThat(underTest.getMqttQos()).isEqualTo(mqttQos);
    }

    @Test
    public void getAsMqtt3SubscriptionReturnsExpected() {
        final var mqttQos = MqttQos.AT_LEAST_ONCE;
        final var underTest = GenericMqttSubscription.newInstance(MQTT_TOPIC_FILTER, mqttQos);

        assertThat(underTest.getAsMqtt3Subscription())
                .isEqualTo(Mqtt3Subscription.builder()
                        .topicFilter(MQTT_TOPIC_FILTER)
                        .qos(mqttQos)
                        .build());
    }

    @Test
    public void getAsMqtt5SubscriptionReturnsExpected() {
        final var mqttQos = MqttQos.AT_LEAST_ONCE;
        final var underTest = GenericMqttSubscription.newInstance(MQTT_TOPIC_FILTER, mqttQos);

        assertThat(underTest.getAsMqtt5Subscription())
                .isEqualTo(Mqtt5Subscription.builder()
                        .topicFilter(MQTT_TOPIC_FILTER)
                        .qos(mqttQos)
                        .build());
    }

}