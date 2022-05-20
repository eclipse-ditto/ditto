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
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.datatypes.MqttTopicFilter;
import com.hivemq.client.mqtt.mqtt3.message.subscribe.Mqtt3Subscribe;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5Subscribe;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link GenericMqttSubscribe}.
 */
public final class GenericMqttSubscribeTest {

    private static final GenericMqttSubscription GENERIC_MQTT_SUBSCRIPTION_FOO =
            GenericMqttSubscription.newInstance(MqttTopicFilter.of("source/foo"), MqttQos.AT_MOST_ONCE);
    private static final GenericMqttSubscription GENERIC_MQTT_SUBSCRIPTION_BAR =
            GenericMqttSubscription.newInstance(MqttTopicFilter.of("source/bar"), MqttQos.AT_LEAST_ONCE);
    private static final GenericMqttSubscription GENERIC_MQTT_SUBSCRIPTION_BAZ =
            GenericMqttSubscription.newInstance(MqttTopicFilter.of("source/baz"), MqttQos.EXACTLY_ONCE);

    private Set<GenericMqttSubscription> genericMqttSubscriptions;

    @Before
    public void before() {
        genericMqttSubscriptions = new LinkedHashSet<>();
        Collections.addAll(genericMqttSubscriptions,
                GENERIC_MQTT_SUBSCRIPTION_FOO,
                GENERIC_MQTT_SUBSCRIPTION_BAR,
                GENERIC_MQTT_SUBSCRIPTION_BAZ);
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(GenericMqttSubscribe.class,
                areImmutable(),
                provided(GenericMqttSubscription.class).isAlsoImmutable(),
                assumingFields("genericMqttSubscriptions").areNotModifiedAndDoNotEscape());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(GenericMqttSubscribe.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void ofWithNullGenericSubscriptionsThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> GenericMqttSubscribe.of(null))
                .withMessage("The genericMqttSubscriptions must not be null!")
                .withNoCause();
    }

    @Test
    public void ofWithEmptyGenericSubscriptionsThrowsException() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> GenericMqttSubscribe.of(Set.of()))
                .withMessage("The argument 'genericMqttSubscriptions' must not be empty!")
                .withNoCause();
    }

    @Test
    public void genericMqttSubscriptionsReturnsExpectedStream() {
        final var underTest = GenericMqttSubscribe.of(genericMqttSubscriptions);

        assertThat(underTest.genericMqttSubscriptions())
                .containsExactly(GENERIC_MQTT_SUBSCRIPTION_FOO,
                        GENERIC_MQTT_SUBSCRIPTION_BAR,
                        GENERIC_MQTT_SUBSCRIPTION_BAZ);
    }

    @Test
    public void getAsMqtt3SubscribeReturnsExpected() {
        final var underTest = GenericMqttSubscribe.of(genericMqttSubscriptions);

        assertThat(underTest.getAsMqtt3Subscribe())
                .isEqualTo(Mqtt3Subscribe.builder()
                        .addSubscription(GENERIC_MQTT_SUBSCRIPTION_FOO.getAsMqtt3Subscription())
                        .addSubscription(GENERIC_MQTT_SUBSCRIPTION_BAR.getAsMqtt3Subscription())
                        .addSubscription(GENERIC_MQTT_SUBSCRIPTION_BAZ.getAsMqtt3Subscription())
                        .build());
    }

    @Test
    public void getAsMqtt5SubscribeReturnsExpected() {
        final var underTest = GenericMqttSubscribe.of(genericMqttSubscriptions);

        assertThat(underTest.getAsMqtt5Subscribe())
                .isEqualTo(Mqtt5Subscribe.builder()
                        .addSubscription(GENERIC_MQTT_SUBSCRIPTION_FOO.getAsMqtt5Subscription())
                        .addSubscription(GENERIC_MQTT_SUBSCRIPTION_BAR.getAsMqtt5Subscription())
                        .addSubscription(GENERIC_MQTT_SUBSCRIPTION_BAZ.getAsMqtt5Subscription())
                        .build());
    }

}