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
package org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.clients;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.hivemq.client.mqtt.mqtt3.message.connect.connack.Mqtt3ConnAckReturnCode;
import com.hivemq.client.mqtt.mqtt5.message.connect.connack.Mqtt5ConnAckReasonCode;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link GenericMqttConnAckStatus}.
 */
@RunWith(Enclosed.class)
public final class GenericMqttConnAckStatusTest {

    public static final class  GeneralFunctionalityTest {

        @Test
        public void assertImmutability() {
            assertInstancesOf(GenericMqttConnAckStatus.class, areImmutable());
        }

        @Test
        public void testHashCodeAndEquals() {
            EqualsVerifier.forClass(GenericMqttConnAckStatus.class)
                    .usingGetClass()
                    .verify();
        }

        @Test
        public void ofMqtt3ConnAckReturnCodeWithNullMqtt3ConnAckReturnCodeThrowsException() {
            assertThatNullPointerException()
                    .isThrownBy(() -> GenericMqttConnAckStatus.ofMqtt3ConnAckReturnCode(null))
                    .withMessage("The mqtt3ConnAckReturnCode must not be null!")
                    .withNoCause();
        }

        @Test
        public void ofMqtt5ConnAckReasonCodeWithNullMqtt5ConnAckReasonCodeThrowsException() {
            assertThatNullPointerException()
                    .isThrownBy(() -> GenericMqttConnAckStatus.ofMqtt5ConnAckReasonCode(null))
                    .withMessage("The mqtt5ConnAckReasonCode must not be null!")
                    .withNoCause();
        }

        @Test
        public void toStringForMqtt3ConnAckReturnCodeSuccessReturnsExpected() {
            final var mqtt3ConnAckReturnCode = Mqtt3ConnAckReturnCode.SUCCESS;
            final var underTest = GenericMqttConnAckStatus.ofMqtt3ConnAckReturnCode(mqtt3ConnAckReturnCode);

            assertThat(underTest).hasToString("Success: SUCCESS(0)");
        }

        @Test
        public void toStringForMqtt3ConnAckReturnCodeErrorReturnsExpected() {
            final var mqtt3ConnAckReturnCode = Mqtt3ConnAckReturnCode.SERVER_UNAVAILABLE;
            final var underTest = GenericMqttConnAckStatus.ofMqtt3ConnAckReturnCode(mqtt3ConnAckReturnCode);

            assertThat(underTest).hasToString("Error: SERVER_UNAVAILABLE(3)");
        }

    }

    @RunWith(Parameterized.class)
    public static final class Mqtt3ConnAckReturnCodeParameterizedTest {

        @Parameterized.Parameter
        public Mqtt3ConnAckReturnCode mqtt3ConnAckReturnCode;

        private GenericMqttConnAckStatus underTest;

        @Parameterized.Parameters(name = "{0}")
        public static Mqtt3ConnAckReturnCode[] parameters() {
            return Mqtt3ConnAckReturnCode.values();
        }

        @Before
        public void before() {
            underTest = GenericMqttConnAckStatus.ofMqtt3ConnAckReturnCode(mqtt3ConnAckReturnCode);
        }

        @Test
        public void getCodeReturnsExpected() {
            assertThat(underTest.getCode()).isEqualTo(mqtt3ConnAckReturnCode.getCode());
        }

        @Test
        public void getNameReturnsExpected() {
            assertThat(underTest.getName()).isEqualTo(mqtt3ConnAckReturnCode.name());
        }

        @Test
        public void isErrorReturnsExpected() {
            assertThat(underTest.isError()).isEqualTo(mqtt3ConnAckReturnCode.isError());
        }

    }

    @RunWith(Parameterized.class)
    public static final class Mqtt5ConnAckReturnCodeParameterizedTest {

        @Parameterized.Parameter
        public Mqtt5ConnAckReasonCode mqtt5ConnAckReasonCode;

        private GenericMqttConnAckStatus underTest;

        @Parameterized.Parameters(name = "{0}")
        public static Mqtt5ConnAckReasonCode[] parameters() {
            return Mqtt5ConnAckReasonCode.values();
        }

        @Before
        public void before() {
            underTest = GenericMqttConnAckStatus.ofMqtt5ConnAckReasonCode(mqtt5ConnAckReasonCode);
        }

        @Test
        public void getCodeReasonsExpected() {
            assertThat(underTest.getCode()).isEqualTo(mqtt5ConnAckReasonCode.getCode());
        }

        @Test
        public void getNameReasonsExpected() {
            assertThat(underTest.getName()).isEqualTo(mqtt5ConnAckReasonCode.name());
        }

        @Test
        public void isErrorReasonsExpected() {
            assertThat(underTest.isError()).isEqualTo(mqtt5ConnAckReasonCode.isError());
        }

    }

}