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
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.publish.GenericMqttPublish;
import org.junit.Test;
import org.mockito.Mockito;

import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link GenericMqttPublishResult}.
 */
public final class GenericMqttPublishResultTest {

    private static final GenericMqttPublish GENERIC_MQTT_PUBLISH =
            GenericMqttPublish.ofMqtt3Publish(Mockito.mock(Mqtt3Publish.class));
    private static final IllegalStateException ERROR =
            new IllegalStateException("The Publish message could not be sent.");

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(GenericMqttPublishResult.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void successWithNullGenericPublishResultThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> GenericMqttPublishResult.success(null))
                .withMessage("The genericMqttPublish must not be null!")
                .withNoCause();
    }

    @Test
    public void isSuccessOnSuccessReturnsTrue() {
        final var underTest = GenericMqttPublishResult.success(GENERIC_MQTT_PUBLISH);

        assertThat(underTest.isSuccess()).isTrue();
    }

    @Test
    public void isFailureOnSuccessReturnsFalse() {
        final var underTest = GenericMqttPublishResult.success(GENERIC_MQTT_PUBLISH);

        assertThat(underTest.isFailure()).isFalse();
    }

    @Test
    public void getErrorOnSuccessReturnsEmptyOptional() {
        final var underTest = GenericMqttPublishResult.success(GENERIC_MQTT_PUBLISH);

        assertThat(underTest.getError()).isEmpty();
    }

    @Test
    public void getGenericMqttPublishOnSuccessReturnsExpected() {
        final var underTest = GenericMqttPublishResult.success(GENERIC_MQTT_PUBLISH);

        assertThat(underTest.getGenericMqttPublish()).isEqualTo(GENERIC_MQTT_PUBLISH);
    }

    @Test
    public void failureWithNullGenericMqttPublishThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> GenericMqttPublishResult.failure(null, ERROR))
                .withMessage("The genericMqttPublish must not be null!")
                .withNoCause();
    }

    @Test
    public void failureWithNullErrorThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> GenericMqttPublishResult.failure(GENERIC_MQTT_PUBLISH, null))
                .withMessage("The error must not be null!")
                .withNoCause();
    }

    @Test
    public void isSuccessOnFailureReturnsFalse() {
        final var underTest = GenericMqttPublishResult.failure(GENERIC_MQTT_PUBLISH, ERROR);

        assertThat(underTest.isSuccess()).isFalse();
    }

    @Test
    public void isFailureOnFailureReturnsTrue() {
        final var underTest = GenericMqttPublishResult.failure(GENERIC_MQTT_PUBLISH, ERROR);

        assertThat(underTest.isFailure()).isTrue();
    }

    @Test
    public void getErrorOnFailureReturnsOptionalContainingTheError() {
        final var underTest = GenericMqttPublishResult.failure(GENERIC_MQTT_PUBLISH, ERROR);

        assertThat(underTest.getError()).hasValue(ERROR);
    }

    @Test
    public void getErrorOrThrowOnSuccessThrowsException() {
        final var underTest = GenericMqttPublishResult.success(GENERIC_MQTT_PUBLISH);

        assertThatIllegalStateException()
                .isThrownBy(underTest::getErrorOrThrow)
                .withMessage("Success cannot provide an error.")
                .withNoCause();
    }

    @Test
    public void getErrorOrThrowOnFailureReturnsExpectedError() {
        final var underTest = GenericMqttPublishResult.failure(GENERIC_MQTT_PUBLISH, ERROR);

        assertThat(underTest.getErrorOrThrow()).isEqualTo(ERROR);
    }

    @Test
    public void getGenericMqttPublishOnFailureReturnsExpected() {
        final var underTest = GenericMqttPublishResult.failure(GENERIC_MQTT_PUBLISH, ERROR);

        assertThat(underTest.getGenericMqttPublish()).isEqualTo(GENERIC_MQTT_PUBLISH);
    }

}