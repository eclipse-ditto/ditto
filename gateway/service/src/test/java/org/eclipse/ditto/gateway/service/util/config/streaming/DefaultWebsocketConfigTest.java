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
package org.eclipse.ditto.gateway.service.util.config.streaming;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Duration;

import org.assertj.core.api.JUnitSoftAssertions;
import org.assertj.core.data.Percentage;
import org.eclipse.ditto.base.service.config.ThrottlingConfig;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link DefaultWebsocketConfig}.
 */
public final class DefaultWebsocketConfigTest {

    private static Config webSocketTestConfig;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void initTestFixture() {
        webSocketTestConfig = ConfigFactory.load("websocket-test");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultWebsocketConfig.class, areImmutable(),
                provided(ThrottlingConfig.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultWebsocketConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void underTestReturnsDefaultValuesIfBaseConfigWasEmpty() {
        final WebsocketConfig underTest = DefaultWebsocketConfig.of(ConfigFactory.empty());

        softly.assertThat(underTest.getSubscriberBackpressureQueueSize())
                .as(WebsocketConfig.WebsocketConfigValue.SUBSCRIBER_BACKPRESSURE_QUEUE_SIZE.getConfigPath())
                .isEqualTo(WebsocketConfig.WebsocketConfigValue.SUBSCRIBER_BACKPRESSURE_QUEUE_SIZE.getDefaultValue());
        softly.assertThat(underTest.getPublisherBackpressureBufferSize())
                .as(WebsocketConfig.WebsocketConfigValue.PUBLISHER_BACKPRESSURE_BUFFER_SIZE.getConfigPath())
                .isEqualTo(WebsocketConfig.WebsocketConfigValue.PUBLISHER_BACKPRESSURE_BUFFER_SIZE.getDefaultValue());
        softly.assertThat(underTest.getThrottlingRejectionFactor())
                .as(WebsocketConfig.WebsocketConfigValue.THROTTLING_REJECTION_FACTOR.getConfigPath())
                .isCloseTo((Double) WebsocketConfig.WebsocketConfigValue.THROTTLING_REJECTION_FACTOR.getDefaultValue(),
                        Percentage.withPercentage(1.0));
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final WebsocketConfig underTest = DefaultWebsocketConfig.of(webSocketTestConfig);

        softly.assertThat(underTest.getSubscriberBackpressureQueueSize())
                .as(WebsocketConfig.WebsocketConfigValue.SUBSCRIBER_BACKPRESSURE_QUEUE_SIZE.getConfigPath())
                .isEqualTo(23);
        softly.assertThat(underTest.getPublisherBackpressureBufferSize())
                .as(WebsocketConfig.WebsocketConfigValue.PUBLISHER_BACKPRESSURE_BUFFER_SIZE.getConfigPath())
                .isEqualTo(42);
        softly.assertThat(underTest.getThrottlingRejectionFactor())
                .as(WebsocketConfig.WebsocketConfigValue.THROTTLING_REJECTION_FACTOR.getConfigPath())
                .isCloseTo(1.875, Percentage.withPercentage(1.0));
        softly.assertThat(underTest.getThrottlingConfig().getInterval())
                .as("throttling.interval")
                .isEqualTo(Duration.ofSeconds(8L));
        softly.assertThat(underTest.getThrottlingConfig().getLimit())
                .as("throttling.limit")
                .isEqualTo(9);
    }

    @Test
    public void render() {
        final WebsocketConfig underTest = DefaultWebsocketConfig.of(webSocketTestConfig);
        final Config rendered = underTest.render();
        final WebsocketConfig reconstructed = DefaultWebsocketConfig.of(rendered);
        assertThat(reconstructed).isEqualTo(underTest);
    }

}
