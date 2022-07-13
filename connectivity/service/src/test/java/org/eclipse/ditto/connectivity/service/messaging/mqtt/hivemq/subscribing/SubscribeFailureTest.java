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
package org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.subscribing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.client.MqttSubscribeException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link SubscribeFailure}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class SubscribeFailureTest {

    @Mock private Source connectionSource;

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(SubscribeFailure.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void newInstanceWithNullConnectionSourceThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> SubscribeFailure.newInstance(null, new MqttSubscribeException()))
                .withMessage("The connectionSource must not be null!")
                .withNoCause();
    }

    @Test
    public void newInstanceWithNullMqttSubscribeExceptionThrowsException() {
        assertThatNullPointerException()
                .isThrownBy(() -> SubscribeFailure.newInstance(connectionSource, null))
                .withMessage("The mqttSubscribeException must not be null!")
                .withNoCause();
    }

    @Test
    public void isSuccessReturnsFalse() {
        final var underTest = SubscribeFailure.newInstance(connectionSource, new MqttSubscribeException());

        assertThat(underTest.isSuccess()).isFalse();
    }

    @Test
    public void isFailureReturnsTrue() {
        final var underTest = SubscribeFailure.newInstance(connectionSource, new MqttSubscribeException());

        assertThat(underTest.isFailure()).isTrue();
    }

    @Test
    public void getConnectionSourceReturnsExpected() {
        final var underTest = SubscribeFailure.newInstance(connectionSource, new MqttSubscribeException());

        assertThat(underTest.getConnectionSource()).isEqualTo(connectionSource);
    }

    @Test
    public void getMqttPublishSourceThrowsException() {
        final var underTest = SubscribeFailure.newInstance(connectionSource, new MqttSubscribeException());

        assertThatIllegalStateException()
                .isThrownBy(underTest::getMqttPublishSourceOrThrow)
                .withMessage("Failure cannot provide a MQTT Publish Source.")
                .withNoCause();
    }

    @Test
    public void getErrorReturnsExpectedError() {
        final var mqttSubscribeException = new MqttSubscribeException();
        final var underTest = SubscribeFailure.newInstance(connectionSource, mqttSubscribeException);

        assertThat(underTest.getErrorOrThrow()).isEqualTo(mqttSubscribeException);
    }

}