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

import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link TransformationSuccess}.
 */
public final class TransformationSuccessTest {

    private static final GenericMqttPublish GENERIC_MQTT_PUBLISH =
            GenericMqttPublish.ofMqtt5Publish(Mockito.mock(Mqtt5Publish.class));

    private static final ExternalMessage EXTERNAL_MESSAGE = Mockito.mock(ExternalMessage.class);

    private TransformationSuccess<GenericMqttPublish, ExternalMessage> underTest;

    @Before
    public void before() {
        underTest = TransformationSuccess.of(GENERIC_MQTT_PUBLISH, EXTERNAL_MESSAGE);
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(TransformationSuccess.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void ofWithNullTransformationInputThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> TransformationSuccess.of(null, EXTERNAL_MESSAGE))
                .withMessage("The transformationInput must not be null!")
                .withNoCause();
    }

    @Test
    public void ofWithNullSuccessValueThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> TransformationSuccess.of(GENERIC_MQTT_PUBLISH, null))
                .withMessage("The successValue must not be null!")
                .withNoCause();
    }

    @Test
    public void isSuccessReturnsTrue() {
        assertThat(underTest.isSuccess()).isTrue();
    }

    @Test
    public void isFailureReturnsFalse() {
        assertThat(underTest.isFailure()).isFalse();
    }

    @Test
    public void getTransformationInputReturnsExpected() {
        assertThat(underTest.getTransformationInput()).isEqualTo(GENERIC_MQTT_PUBLISH);
    }

    @Test
    public void getSuccessValueOrThrowReturnsSuccessValue() {
        assertThat(underTest.getSuccessValueOrThrow()).isEqualTo(EXTERNAL_MESSAGE);
    }

    @Test
    public void getErrorOrThrowThrowsException() {
        assertThatIllegalStateException()
                .isThrownBy(underTest::getErrorOrThrow)
                .withMessage("Success cannot provide an error.")
                .withNoCause();
    }

}