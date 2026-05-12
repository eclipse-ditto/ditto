/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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

import java.time.Duration;

import org.assertj.core.api.JUnitSoftAssertions;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link DefaultSseConfig}.
 */
public final class DefaultSseConfigTest {

    private static Config sseTestConfig;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void initTestFixture() {
        sseTestConfig = ConfigFactory.load("sse-test");
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultSseConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void underTestReturnsDefaultValuesIfBaseConfigWasEmpty() {
        final SseConfig underTest = DefaultSseConfig.of(ConfigFactory.empty());

        softly.assertThat(underTest.getPublisherBackpressureBufferSize())
                .as(SseConfig.SseConfigValue.PUBLISHER_BACKPRESSURE_BUFFER_SIZE.getConfigPath())
                .isEqualTo(SseConfig.SseConfigValue.PUBLISHER_BACKPRESSURE_BUFFER_SIZE.getDefaultValue());
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final SseConfig underTest = DefaultSseConfig.of(sseTestConfig);

        softly.assertThat(underTest.getPublisherBackpressureBufferSize())
                .as(SseConfig.SseConfigValue.PUBLISHER_BACKPRESSURE_BUFFER_SIZE.getConfigPath())
                .isEqualTo(77);
        softly.assertThat(underTest.getThrottlingConfig().getInterval())
                .as("throttling.interval")
                .isEqualTo(Duration.ofSeconds(8L));
        softly.assertThat(underTest.getThrottlingConfig().getLimit())
                .as("throttling.limit")
                .isEqualTo(9);
    }

}
