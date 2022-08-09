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

import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Duration;

import org.assertj.core.api.JUnitSoftAssertions;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link DefaultStreamingConfig}.
 */
public final class DefaultStreamingConfigTest {

    private static Config streamingTestConfig;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void initTestFixture() {
        streamingTestConfig = ConfigFactory.load("streaming-test");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultStreamingConfig.class, areImmutable(),
                provided(Config.class, WebsocketConfig.class, SseConfig.class).areAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultStreamingConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void underTestReturnsDefaultValuesIfBaseConfigWasEmpty() {
        final StreamingConfig underTest = DefaultStreamingConfig.of(ConfigFactory.empty());

        softly.assertThat(underTest.getSessionCounterScrapeInterval())
                .as(StreamingConfig.StreamingConfigValue.SESSION_COUNTER_SCRAPE_INTERVAL.getConfigPath())
                .isEqualTo(StreamingConfig.StreamingConfigValue.SESSION_COUNTER_SCRAPE_INTERVAL.getDefaultValue());
        softly.assertThat(underTest.getParallelism())
                .as(StreamingConfig.StreamingConfigValue.PARALLELISM.getConfigPath())
                .isEqualTo(StreamingConfig.StreamingConfigValue.PARALLELISM.getDefaultValue());
        softly.assertThat(underTest.getSearchIdleTimeout())
                .as(StreamingConfig.StreamingConfigValue.SEARCH_IDLE_TIMEOUT.getConfigPath())
                .isEqualTo(StreamingConfig.StreamingConfigValue.SEARCH_IDLE_TIMEOUT.getDefaultValue());
        softly.assertThat(underTest.getSubscriptionRefreshDelay())
                .as(StreamingConfig.StreamingConfigValue.SUBSCRIPTION_REFRESH_DELAY.getConfigPath())
                .isEqualTo(StreamingConfig.StreamingConfigValue.SUBSCRIPTION_REFRESH_DELAY.getDefaultValue());
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final StreamingConfig underTest = DefaultStreamingConfig.of(streamingTestConfig);

        softly.assertThat(underTest.getSessionCounterScrapeInterval())
                .as(StreamingConfig.StreamingConfigValue.SESSION_COUNTER_SCRAPE_INTERVAL.getConfigPath())
                .isEqualTo(Duration.ofSeconds(67L));
        softly.assertThat(underTest.getParallelism())
                .as(StreamingConfig.StreamingConfigValue.PARALLELISM.getConfigPath())
                .isEqualTo(1024);
        softly.assertThat(underTest.getSearchIdleTimeout())
                .as(StreamingConfig.StreamingConfigValue.SEARCH_IDLE_TIMEOUT.getConfigPath())
                .isEqualTo(Duration.ofHours(7L));
        softly.assertThat(underTest.getSubscriptionRefreshDelay())
                .as(StreamingConfig.StreamingConfigValue.SUBSCRIPTION_REFRESH_DELAY.getConfigPath())
                .isEqualTo(Duration.ofHours(8));
        softly.assertThat(underTest.getWebsocketConfig().getThrottlingConfig().getInterval())
                .as("websocket.throttling.interval")
                .isEqualTo(Duration.ofSeconds(8L));
        softly.assertThat(underTest.getWebsocketConfig().getThrottlingConfig().getLimit())
                .as("websocket.throttling.limit")
                .isEqualTo(9);
    }

}
