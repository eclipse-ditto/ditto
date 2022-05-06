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

import org.assertj.core.api.Assertions;
import org.assertj.core.api.AutoCloseableSoftAssertions;
import org.eclipse.ditto.connectivity.model.GenericTarget;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.common.InvalidMqttQosCodeException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.datatypes.MqttTopic;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link MqttPublishTarget}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class MqttPublishTargetTest {

    private static final MqttTopic VALID_MQTT_TOPIC = MqttTopic.of("target/status");
    private static final MqttQos VALID_MQTT_QOS = MqttQos.EXACTLY_ONCE;

    @Mock
    private GenericTarget genericTarget;

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(MqttPublishTarget.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void tryNewInstanceWithNullGenericTargetTopicThrowsException() {
        Assertions.assertThatNullPointerException()
                .isThrownBy(() -> MqttPublishTarget.tryNewInstance(null))
                .withMessage("The genericTarget must not be null!")
                .withNoCause();
    }

    @Test
    public void tryNewInstanceWithEmptyMqttTopicReturnsFailedTry() {
        Mockito.when(genericTarget.getAddress()).thenReturn("");

        final var mqttPublishTargetTry = MqttPublishTarget.tryNewInstance(genericTarget);

        assertThat(mqttPublishTargetTry.isFailure()).isTrue();

        final var failed = mqttPublishTargetTry.failed();

        assertThat(failed.get())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Topic must be at least one character long.")
                .hasNoCause();
    }

    @Test
    public void tryNewInstanceWithBlankMqttTopicReturnsFailedTry() {
        Mockito.when(genericTarget.getAddress()).thenReturn("  ");

        final var mqttPublishTargetTry = MqttPublishTarget.tryNewInstance(genericTarget);

        assertThat(mqttPublishTargetTry.isFailure()).isTrue();

        final var failed = mqttPublishTargetTry.failed();

        assertThat(failed.get())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Topic must not be blank.")
                .hasNoCause();
    }

    /*
     * In fact there are many possibilities of an invalid MQTT topic
     * CharSequence.
     * However, all result in an IllegalArgumentException and thus this test
     * specimen is sufficient.
     */
    @Test
    public void tryNewInstanceWithWildcardCharacterInMqttTopicCharSequenceReturnsFailedTry() {
        final var mqttTopicCharSequence = "target/#";
        Mockito.when(genericTarget.getAddress()).thenReturn(mqttTopicCharSequence);

        final var mqttPublishTargetTry = MqttPublishTarget.tryNewInstance(genericTarget);

        assertThat(mqttPublishTargetTry.isFailure()).isTrue();

        final var failed = mqttPublishTargetTry.failed();

        assertThat(failed.get())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Topic [%s] must not contain multi level wildcard (%s), found at index %d.",
                        mqttTopicCharSequence,
                        "#",
                        mqttTopicCharSequence.length() - 1)
                .hasNoCause();
    }

    @Test
    public void tryNewInstanceWithInvalidMqttQosCodeReturnsFailedTry() {
        final var invalidMqttQosCode = 42;
        Mockito.when(genericTarget.getAddress()).thenReturn(VALID_MQTT_TOPIC.toString());
        Mockito.when(genericTarget.getQos()).thenReturn(Optional.of(invalidMqttQosCode));

        final var mqttPublishTargetTry = MqttPublishTarget.tryNewInstance(genericTarget);

        assertThat(mqttPublishTargetTry.isFailure()).isTrue();

        final var failed = mqttPublishTargetTry.failed();

        assertThat(failed.get())
                .isInstanceOf(InvalidMqttQosCodeException.class)
                .hasMessage("<%s> is not a valid MQTT QoS code.", invalidMqttQosCode)
                .hasNoCause();
    }

    @Test
    public void tryNewInstanceWithMissingQosCodeReturnsSuccessTryWithFallBackQos() {
        Mockito.when(genericTarget.getAddress()).thenReturn(VALID_MQTT_TOPIC.toString());
        final var mqttPublishTargetTry = MqttPublishTarget.tryNewInstance(genericTarget);

        final var mqttPublishTarget = mqttPublishTargetTry.get();

        assertThat(mqttPublishTarget.getQos()).isEqualTo(MqttPublishTarget.DEFAULT_TARGET_QOS);
    }

    @Test
    public void tryNewInstanceWithValidArgumentsReturnsSuccessTry() {
        final var topic = VALID_MQTT_TOPIC;
        final var qos = VALID_MQTT_QOS;
        Mockito.when(genericTarget.getAddress()).thenReturn(topic.toString());
        Mockito.when(genericTarget.getQos()).thenReturn(Optional.of(qos.getCode()));

        final var mqttPublishTargetTry = MqttPublishTarget.tryNewInstance(genericTarget);

        try (final var softly = new AutoCloseableSoftAssertions()) {
            softly.assertThat(mqttPublishTargetTry.get())
                    .as("MqttPublishTarget")
                            .satisfies(mqttPublishTarget -> {
                                softly.assertThat(mqttPublishTarget.getTopic()).as("topic").isEqualTo(topic);
                                softly.assertThat(mqttPublishTarget.getQos()).as("QoS").isEqualTo(qos);
                            });
        }
    }

}