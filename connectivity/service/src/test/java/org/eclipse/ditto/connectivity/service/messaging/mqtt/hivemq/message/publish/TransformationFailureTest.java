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
package org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.publish;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.Map;

import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.MqttHeader;
import org.eclipse.ditto.placeholders.UnresolvedPlaceholderException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link TransformationFailure}.
 */
public final class TransformationFailureTest {

    private static final GenericMqttPublish GENERIC_MQTT_PUBLISH =
            GenericMqttPublish.ofMqtt3Publish(Mockito.mock(Mqtt3Publish.class));

    private static final Map<String, String> MQTT_PUBLISH_HEADERS = Map.of(
            MqttHeader.MQTT_TOPIC.getName(), "source/my-connection",
            MqttHeader.MQTT_QOS.getName(), String.valueOf(MqttQos.AT_LEAST_ONCE.getCode()),
            MqttHeader.MQTT_RETAIN.getName(), String.valueOf(true)
    );

    private static final UnresolvedPlaceholderException UNRESOLVED_PLACEHOLDER_EXCEPTION =
            UnresolvedPlaceholderException.newBuilder("{{myPlaceholder}}").build();

    private static final MqttPublishTransformationException MQTT_PUBLISH_TRANSFORMATION_EXCEPTION =
            new MqttPublishTransformationException("Failed to transform GenericMqttPublish to ExternalMessage.",
                    UNRESOLVED_PLACEHOLDER_EXCEPTION,
                    MQTT_PUBLISH_HEADERS);

    private TransformationFailure<GenericMqttPublish, ExternalMessage> underTest;

    @Before
    public void before() {
        underTest = TransformationFailure.of(GENERIC_MQTT_PUBLISH, MQTT_PUBLISH_TRANSFORMATION_EXCEPTION);
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(TransformationFailure.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void ofWithNullTransformationInputThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> TransformationFailure.of(null, MQTT_PUBLISH_TRANSFORMATION_EXCEPTION))
                .withMessage("The transformationInput must not be null!")
                .withNoCause();
    }

    @Test
    public void ofWithNullErrorThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> TransformationFailure.of(GENERIC_MQTT_PUBLISH, null))
                .withMessage("The error must not be null!")
                .withNoCause();
    }

    @Test
    public void isSuccessReturnsFalse() {
        assertThat(underTest.isSuccess()).isFalse();
    }

    @Test
    public void isFailureReturnsTrue() {
        assertThat(underTest.isFailure()).isTrue();
    }

    @Test
    public void getSuccessValueOrThrowThrowsIllegalStateException() {
        assertThatIllegalStateException()
                .isThrownBy(underTest::getSuccessValueOrThrow)
                .withMessage("Failure cannot provide a success value.")
                .withNoCause();
    }

    @Test
    public void getErrorOrThrowReturnsExpectedError() {
        assertThat(underTest.getErrorOrThrow()).isEqualTo(MQTT_PUBLISH_TRANSFORMATION_EXCEPTION);
    }

    @Test
    public void getGenericMqttPublishReturnsExpected() {
        assertThat(underTest.getTransformationInput()).isEqualTo(GENERIC_MQTT_PUBLISH);
    }

}