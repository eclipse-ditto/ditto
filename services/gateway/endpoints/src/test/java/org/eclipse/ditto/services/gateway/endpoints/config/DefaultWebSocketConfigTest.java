/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.gateway.endpoints.config;

import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.services.gateway.endpoints.config.WebSocketConfig.WebSocketConfigValue;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link org.eclipse.ditto.services.gateway.endpoints.config.DefaultWebSocketConfig}.
 */
public final class DefaultWebSocketConfigTest {

    private static Config webSocketTestConfig;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void initTestFixture() {
        webSocketTestConfig = ConfigFactory.load("websocket-test");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultWebSocketConfig.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultWebSocketConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void underTestReturnsDefaultValuesIfBaseConfigWasEmpty() {
        final DefaultWebSocketConfig underTest = DefaultWebSocketConfig.of(ConfigFactory.empty());

        softly.assertThat(underTest.getSubscriberBackpressureQueueSize())
                .as(WebSocketConfigValue.SUBSCRIBER_BACKPRESSURE_QUEUE_SIZE.getConfigPath())
                .isEqualTo(WebSocketConfigValue.SUBSCRIBER_BACKPRESSURE_QUEUE_SIZE.getDefaultValue());
        softly.assertThat(underTest.getPublisherBackpressureBufferSize())
                .as(WebSocketConfigValue.PUBLISHER_BACKPRESSURE_BUFFER_SIZE.getConfigPath())
                .isEqualTo(WebSocketConfigValue.PUBLISHER_BACKPRESSURE_BUFFER_SIZE.getDefaultValue());
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final DefaultWebSocketConfig underTest = DefaultWebSocketConfig.of(webSocketTestConfig);

        softly.assertThat(underTest.getSubscriberBackpressureQueueSize())
                .as(WebSocketConfigValue.SUBSCRIBER_BACKPRESSURE_QUEUE_SIZE.getConfigPath())
                .isEqualTo(23);
        softly.assertThat(underTest.getPublisherBackpressureBufferSize())
                .as(WebSocketConfigValue.PUBLISHER_BACKPRESSURE_BUFFER_SIZE.getConfigPath())
                .isEqualTo(42);
    }

}
