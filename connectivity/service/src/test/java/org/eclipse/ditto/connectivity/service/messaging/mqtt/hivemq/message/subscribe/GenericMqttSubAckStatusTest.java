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
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.hivemq.client.mqtt.mqtt3.message.subscribe.suback.Mqtt3SubAckReturnCode;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.suback.Mqtt5SubAckReasonCode;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link GenericMqttSubAckStatus}.
 */
@RunWith(Enclosed.class)
public final class GenericMqttSubAckStatusTest {

    public static final class  GeneralFunctionalityTest {

        @Test
        public void assertImmutability() {
            assertInstancesOf(GenericMqttSubAckStatus.class, areImmutable());
        }

        @Test
        public void testHashCodeAndEquals() {
            EqualsVerifier.forClass(GenericMqttSubAckStatus.class)
                    .usingGetClass()
                    .verify();
        }

        @Test
        public void ofMqtt3SubAckReturnCodeWithNullMqtt3SubAckReturnCodeThrowsException() {
            assertThatNullPointerException()
                    .isThrownBy(() -> GenericMqttSubAckStatus.ofMqtt3SubAckReturnCode(null))
                    .withMessage("The mqtt3SubAckReturnCode must not be null!")
                    .withNoCause();
        }

        @Test
        public void ofMqtt5SubAckReasonCodeWithNullMqtt5SubAckReasonCodeThrowsException() {
            assertThatNullPointerException()
                    .isThrownBy(() -> GenericMqttSubAckStatus.ofMqtt5SubAckReasonCode(null))
                    .withMessage("The mqtt5SubAckReasonCode must not be null!")
                    .withNoCause();
        }

        @Test
        public void toStringForMqtt3SubAckReturnCodeSuccessReturnsExpected() {
            final var mqtt3SubAckReturnCode = Mqtt3SubAckReturnCode.SUCCESS_MAXIMUM_QOS_1;
            final var underTest = GenericMqttSubAckStatus.ofMqtt3SubAckReturnCode(mqtt3SubAckReturnCode);

            assertThat(underTest).hasToString("Success: SUCCESS_MAXIMUM_QOS_1(1)");
        }

        @Test
        public void toStringForMqtt3SubAckReturnCodeErrorReturnsExpected() {
            final var mqtt3SubAckReturnCode = Mqtt3SubAckReturnCode.FAILURE;
            final var underTest = GenericMqttSubAckStatus.ofMqtt3SubAckReturnCode(mqtt3SubAckReturnCode);

            assertThat(underTest).hasToString("Error: FAILURE(128)");
        }

    }

    @RunWith(Parameterized.class)
    public static final class Mqtt3SubAckReturnCodeParameterizedTest {

        @Parameterized.Parameter
        public Mqtt3SubAckReturnCode mqtt3SubAckReturnCode;

        private GenericMqttSubAckStatus underTest;

        @Parameterized.Parameters(name = "{0}")
        public static Mqtt3SubAckReturnCode[] parameters() {
            return Mqtt3SubAckReturnCode.values();
        }

        @Before
        public void before() {
            underTest = GenericMqttSubAckStatus.ofMqtt3SubAckReturnCode(mqtt3SubAckReturnCode);
        }

        @Test
        public void getCodeReturnsExpected() {
            assertThat(underTest.getCode()).isEqualTo(mqtt3SubAckReturnCode.getCode());
        }

        @Test
        public void getNameReturnsExpected() {
            assertThat(underTest.getName()).isEqualTo(mqtt3SubAckReturnCode.name());
        }

        @Test
        public void isErrorReturnsExpected() {
            assertThat(underTest.isError()).isEqualTo(mqtt3SubAckReturnCode.isError());
        }

    }

    @RunWith(Parameterized.class)
    public static final class Mqtt5SubAckReturnCodeParameterizedTest {

        @Parameterized.Parameter
        public Mqtt5SubAckReasonCode mqtt5SubAckReasonCode;

        private GenericMqttSubAckStatus underTest;

        @Parameterized.Parameters(name = "{0}")
        public static Mqtt5SubAckReasonCode[] parameters() {
            return Mqtt5SubAckReasonCode.values();
        }

        @Before
        public void before() {
            underTest = GenericMqttSubAckStatus.ofMqtt5SubAckReasonCode(mqtt5SubAckReasonCode);
        }

        @Test
        public void getCodeReasonsExpected() {
            assertThat(underTest.getCode()).isEqualTo(mqtt5SubAckReasonCode.getCode());
        }

        @Test
        public void getNameReasonsExpected() {
            assertThat(underTest.getName()).isEqualTo(mqtt5SubAckReasonCode.name());
        }

        @Test
        public void isErrorReasonsExpected() {
            assertThat(underTest.isError()).isEqualTo(mqtt5SubAckReasonCode.isError());
        }

    }

}