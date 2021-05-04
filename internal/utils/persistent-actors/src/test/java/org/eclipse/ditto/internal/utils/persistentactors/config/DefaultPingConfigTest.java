/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.persistentactors.config;

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
 * Unit test for {@link DefaultPingConfig}.
 */
public final class DefaultPingConfigTest {

    private static Config pingTestConf;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void initTestFixture() {
        pingTestConf = ConfigFactory.load("ping-test");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultPingConfig.class,
                areImmutable(),
                provided(RateConfig.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultPingConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void underTestReturnsDefaultValuesIfBaseConfigWasEmpty() {
        final DefaultPingConfig underTest = DefaultPingConfig.of(ConfigFactory.empty());

        softly.assertThat(underTest.getJournalTag())
                .as(PingConfig.PingConfigValue.JOURNAL_TAG.getConfigPath())
                .isEqualTo(PingConfig.PingConfigValue.JOURNAL_TAG.getDefaultValue());

        softly.assertThat(underTest.getInitialDelay())
                .as(PingConfig.PingConfigValue.INITIAL_DELAY.getConfigPath())
                .isEqualTo(PingConfig.PingConfigValue.INITIAL_DELAY.getDefaultValue());

        softly.assertThat(underTest.getInterval())
                .as(PingConfig.PingConfigValue.INTERVAL.getConfigPath())
                .isEqualTo(PingConfig.PingConfigValue.INTERVAL.getDefaultValue());

        softly.assertThat(underTest.getReadJournalBatchSize())
                .as(PingConfig.PingConfigValue.READ_JOURNAL_BATCH_SIZE.getConfigPath())
                .isEqualTo(PingConfig.PingConfigValue.READ_JOURNAL_BATCH_SIZE.getDefaultValue());

        softly.assertThat(underTest.getStreamingOrder())
                .as(PingConfig.PingConfigValue.STREAMING_ORDER.getConfigPath())
                .isEqualTo(PingConfig.StreamingOrder.valueOf(
                        String.valueOf(PingConfig.PingConfigValue.STREAMING_ORDER.getDefaultValue())));

        softly.assertThat(underTest.getRateConfig())
                .as("rateConfig")
                .satisfies(rateConfig -> {
                    softly.assertThat(rateConfig.getEntityAmount())
                            .as(RateConfig.RateConfigValue.ENTITIES.getConfigPath())
                            .isEqualTo(RateConfig.RateConfigValue.ENTITIES.getDefaultValue());
                    softly.assertThat(rateConfig.getFrequency())
                            .as(RateConfig.RateConfigValue.FREQUENCY.getConfigPath())
                            .isEqualTo(RateConfig.RateConfigValue.FREQUENCY.getDefaultValue());
                });
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final DefaultPingConfig underTest = DefaultPingConfig.of(pingTestConf);

        softly.assertThat(underTest.getJournalTag())
                .as(PingConfig.PingConfigValue.JOURNAL_TAG.getConfigPath())
                .isEqualTo("some-tag");

        softly.assertThat(underTest.getInitialDelay())
                .as(PingConfig.PingConfigValue.INITIAL_DELAY.getConfigPath())
                .isEqualTo(Duration.ofSeconds(1L));

        softly.assertThat(underTest.getInterval())
                .as(PingConfig.PingConfigValue.INTERVAL.getConfigPath())
                .isEqualTo(Duration.ofMinutes(5L));

        softly.assertThat(underTest.getReadJournalBatchSize())
                .as(PingConfig.PingConfigValue.READ_JOURNAL_BATCH_SIZE.getConfigPath())
                .isEqualTo(7);

        softly.assertThat(underTest.getRateConfig())
                .as("rateConfig")
                .satisfies(rateConfig -> {
                    softly.assertThat(rateConfig.getEntityAmount())
                            .as(RateConfig.RateConfigValue.ENTITIES.getConfigPath())
                            .isEqualTo(2);
                    softly.assertThat(rateConfig.getFrequency())
                            .as(RateConfig.RateConfigValue.FREQUENCY.getConfigPath())
                            .isEqualTo(Duration.ofSeconds(2L));
                });

        softly.assertThat(underTest.getStreamingOrder())
                .as(PingConfig.PingConfigValue.STREAMING_ORDER.getConfigPath())
                .isEqualTo(PingConfig.StreamingOrder.TAGS);
    }
}
