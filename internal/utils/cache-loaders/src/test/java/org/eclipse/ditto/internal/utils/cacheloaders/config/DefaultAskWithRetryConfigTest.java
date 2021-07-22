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
package org.eclipse.ditto.internal.utils.cacheloaders.config;

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
 * Unit test for {@link org.eclipse.ditto.internal.utils.cacheloaders.config.DefaultAskWithRetryConfig}.
 */
public final class DefaultAskWithRetryConfigTest {

    private static final String KNOWN_CONFIG_PATH = "test-ask-with-retry";

    private static Config askWithRetryTestConfig;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void initTestFixture() {
        askWithRetryTestConfig = ConfigFactory.load("ask-with-retry-test");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultAskWithRetryConfig.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultAskWithRetryConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void underTestReturnsDefaultValuesIfBaseConfigWasEmpty() {
        final DefaultAskWithRetryConfig underTest =
                DefaultAskWithRetryConfig.of(ConfigFactory.empty(), KNOWN_CONFIG_PATH);

        softly.assertThat(underTest.getAskTimeout())
                .as(AskWithRetryConfig.AskWithRetryConfigValue.ASK_TIMEOUT.getConfigPath())
                .isEqualTo(AskWithRetryConfig.AskWithRetryConfigValue.ASK_TIMEOUT.getDefaultValue());

        softly.assertThat(underTest.getRetryStrategy())
                .as(AskWithRetryConfig.AskWithRetryConfigValue.RETRY_STRATEGY.getConfigPath())
                .isEqualTo(RetryStrategy.valueOf(
                        String.valueOf(AskWithRetryConfig.AskWithRetryConfigValue.RETRY_STRATEGY.getDefaultValue())));

        softly.assertThat(underTest.getRetryAttempts())
                .as(AskWithRetryConfig.AskWithRetryConfigValue.RETRY_ATTEMPTS.getConfigPath())
                .isEqualTo(AskWithRetryConfig.AskWithRetryConfigValue.RETRY_ATTEMPTS.getDefaultValue());

        softly.assertThat(underTest.getFixedDelay())
                .as(AskWithRetryConfig.AskWithRetryConfigValue.FIXED_DELAY.getConfigPath())
                .isEqualTo(AskWithRetryConfig.AskWithRetryConfigValue.FIXED_DELAY.getDefaultValue());

        softly.assertThat(underTest.getBackoffDelayMin())
                .as(AskWithRetryConfig.AskWithRetryConfigValue.BACKOFF_DELAY_MIN.getConfigPath())
                .isEqualTo(AskWithRetryConfig.AskWithRetryConfigValue.BACKOFF_DELAY_MIN.getDefaultValue());

        softly.assertThat(underTest.getBackoffDelayMax())
                .as(AskWithRetryConfig.AskWithRetryConfigValue.BACKOFF_DELAY_MAX.getConfigPath())
                .isEqualTo(AskWithRetryConfig.AskWithRetryConfigValue.BACKOFF_DELAY_MAX.getDefaultValue());

        softly.assertThat(underTest.getBackoffDelayRandomFactor())
                .as(AskWithRetryConfig.AskWithRetryConfigValue.BACKOFF_DELAY_RANDOM_FACTOR.getConfigPath())
                .isEqualTo(AskWithRetryConfig.AskWithRetryConfigValue.BACKOFF_DELAY_RANDOM_FACTOR.getDefaultValue());
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final DefaultAskWithRetryConfig underTest =
                DefaultAskWithRetryConfig.of(askWithRetryTestConfig, KNOWN_CONFIG_PATH);

        softly.assertThat(underTest.getAskTimeout())
                .as(AskWithRetryConfig.AskWithRetryConfigValue.ASK_TIMEOUT.getConfigPath())
                .isEqualTo(Duration.ofSeconds(7));

        softly.assertThat(underTest.getRetryStrategy())
                .as(AskWithRetryConfig.AskWithRetryConfigValue.RETRY_STRATEGY.getConfigPath())
                .isEqualTo(RetryStrategy.BACKOFF_DELAY);

        softly.assertThat(underTest.getRetryAttempts())
                .as(AskWithRetryConfig.AskWithRetryConfigValue.RETRY_ATTEMPTS.getConfigPath())
                .isEqualTo(42);

        softly.assertThat(underTest.getFixedDelay())
                .as(AskWithRetryConfig.AskWithRetryConfigValue.FIXED_DELAY.getConfigPath())
                .isEqualTo(Duration.ofSeconds(99));

        softly.assertThat(underTest.getBackoffDelayMin())
                .as(AskWithRetryConfig.AskWithRetryConfigValue.BACKOFF_DELAY_MIN.getConfigPath())
                .isEqualTo(Duration.ofSeconds(4));

        softly.assertThat(underTest.getBackoffDelayMax())
                .as(AskWithRetryConfig.AskWithRetryConfigValue.BACKOFF_DELAY_MAX.getConfigPath())
                .isEqualTo(Duration.ofSeconds(44));

        softly.assertThat(underTest.getBackoffDelayRandomFactor())
                .as(AskWithRetryConfig.AskWithRetryConfigValue.BACKOFF_DELAY_RANDOM_FACTOR.getConfigPath())
                .isEqualTo(1.44);
    }

}
