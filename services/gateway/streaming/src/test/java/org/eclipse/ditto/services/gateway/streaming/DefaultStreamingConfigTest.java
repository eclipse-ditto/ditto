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
package org.eclipse.ditto.services.gateway.streaming;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Duration;

import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.services.base.config.DefaultSignalEnrichmentConfig;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link org.eclipse.ditto.services.gateway.streaming.DefaultStreamingConfig}.
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
                provided(Config.class, WebsocketConfig.class, DefaultSignalEnrichmentConfig.class).areAlsoImmutable());
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
        softly.assertThat(underTest.getSignalEnrichmentConfig().getProvider())
                .as("signal-enrichment.provider")
                .isEqualTo("MyEnrichmentProvider");
        softly.assertThat(underTest.getSignalEnrichmentConfig().getProviderConfig().root())
                .as("signal-enrichment.provider-config")
                .containsOnlyKeys("key")
                .containsValue(ConfigValueFactory.fromAnyRef("value"));
        softly.assertThat(underTest.getWebsocketConfig().getThrottlingConfig().getInterval())
                .as("websocket.throttling.interval")
                .isEqualTo(Duration.ofSeconds(8L));
        softly.assertThat(underTest.getWebsocketConfig().getThrottlingConfig().getLimit())
                .as("websocket.throttling.limit")
                .isEqualTo(9);
    }

    @Test
    public void render() {
        final StreamingConfig underTest = DefaultStreamingConfig.of(streamingTestConfig);
        final Config rendered = underTest.render();
        final StreamingConfig reconstructed = DefaultStreamingConfig.of(rendered);
        assertThat(reconstructed).isEqualTo(underTest);
    }

}
