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
package org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.message.connect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Duration;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.connectivity.model.mqtt.IllegalReceiveMaximumValueException;
import org.eclipse.ditto.connectivity.model.mqtt.IllegalSessionExpiryIntervalSecondsException;
import org.eclipse.ditto.connectivity.model.mqtt.ReceiveMaximum;
import org.eclipse.ditto.connectivity.model.mqtt.SessionExpiryInterval;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.IllegalKeepAliveIntervalSecondsException;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.KeepAliveInterval;
import org.junit.Test;

import com.hivemq.client.mqtt.mqtt3.message.connect.Mqtt3Connect;
import com.hivemq.client.mqtt.mqtt5.message.connect.Mqtt5Connect;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link GenericMqttConnect}.
 */
public final class GenericMqttConnectTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(GenericMqttConnect.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(GenericMqttConnect.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void newInstanceWithNullKeepAliveIntervalThrowsException() {
        Assertions.assertThatNullPointerException()
                .isThrownBy(() -> GenericMqttConnect.newInstance(true,
                        null,
                        SessionExpiryInterval.defaultSessionExpiryInterval(),
                        ReceiveMaximum.defaultReceiveMaximum()))
                .withMessage("The keepAliveInterval must not be null!")
                .withNoCause();
    }

    @Test
    public void newInstanceWithNullSessionExpiryIntervalThrowsException() {
        Assertions.assertThatNullPointerException()
                .isThrownBy(() -> GenericMqttConnect.newInstance(true,
                        KeepAliveInterval.defaultKeepAlive(),
                        null,
                        ReceiveMaximum.defaultReceiveMaximum()))
                .withMessage("The sessionExpiryInterval must not be null!")
                .withNoCause();
    }

    @Test
    public void newInstanceWithNullReceiveMaximumThrowsException() {
        Assertions.assertThatNullPointerException()
                .isThrownBy(() -> GenericMqttConnect.newInstance(true,
                        KeepAliveInterval.defaultKeepAlive(),
                        SessionExpiryInterval.defaultSessionExpiryInterval(),
                        null))
                .withMessage("The receiveMaximum must not be null!")
                .withNoCause();
    }

    @Test
    public void getAsMqtt3ConnectReturnsExpected() throws IllegalKeepAliveIntervalSecondsException {
        final var cleanSession = true;
        final var keepAliveInterval = KeepAliveInterval.of(Duration.ofSeconds(13L));
        final var underTest = GenericMqttConnect.newInstance(cleanSession,
                keepAliveInterval,
                SessionExpiryInterval.defaultSessionExpiryInterval(),
                ReceiveMaximum.defaultReceiveMaximum());

        assertThat(underTest.getAsMqtt3Connect())
                .isEqualTo(Mqtt3Connect.builder()
                        .cleanSession(cleanSession)
                        .keepAlive(keepAliveInterval.getSeconds())
                        .build());
    }

    @Test
    public void getAsMqtt5ConnectReturnsExpected()
            throws IllegalKeepAliveIntervalSecondsException, IllegalSessionExpiryIntervalSecondsException,
            IllegalReceiveMaximumValueException {

        final var cleanSession = false;
        final var keepAliveInterval = KeepAliveInterval.of(Duration.ofSeconds(42L));
        final var sessionExpiryInterval = SessionExpiryInterval.of(Duration.ofSeconds(23L));
        final var receiveMaximum = ReceiveMaximum.of(35_000);
        final var underTest =
                GenericMqttConnect.newInstance(cleanSession, keepAliveInterval, sessionExpiryInterval, receiveMaximum);

        assertThat(underTest.getAsMqtt5Connect())
                .isEqualTo(Mqtt5Connect.builder()
                        .cleanStart(cleanSession)
                        .keepAlive(keepAliveInterval.getSeconds())
                        .sessionExpiryInterval(sessionExpiryInterval.getSeconds())
                        .restrictions().receiveMaximum(receiveMaximum.getValue()).applyRestrictions()
                        .build());
    }

}