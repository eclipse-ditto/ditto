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
package org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.consuming;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.ReconnectDelay;
import org.junit.Test;
import org.mockito.Mockito;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ReconnectConsumerClient}.
 */
public final class ReconnectConsumerClientTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(ReconnectConsumerClient.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ReconnectConsumerClient.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void ofWithNullReconnectDelayThrowsException() {
        Assertions.assertThatNullPointerException()
                .isThrownBy(() -> ReconnectConsumerClient.of(null))
                .withMessage("The reconnectDelay must not be null!")
                .withNoCause();
    }

    @Test
    public void getReconnectDelayReturnsExpected() {
        final var reconnectDelay = Mockito.mock(ReconnectDelay.class);
        final var reconnectConsumerClient = ReconnectConsumerClient.of(reconnectDelay);

        assertThat(reconnectConsumerClient.getReconnectDelay()).isEqualTo(reconnectDelay);
    }

}