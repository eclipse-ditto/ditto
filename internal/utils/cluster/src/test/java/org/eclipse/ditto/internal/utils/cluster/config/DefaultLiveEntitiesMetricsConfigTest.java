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
package org.eclipse.ditto.internal.utils.cluster.config;

import java.time.Duration;

import org.assertj.core.api.JUnitSoftAssertions;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link DefaultLiveEntitiesMetricsConfig}.
 */
public final class DefaultLiveEntitiesMetricsConfigTest {

    private static Config testConfig;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void initTestFixture() {
        testConfig = ConfigFactory.load("live-entities-metrics-test");
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultLiveEntitiesMetricsConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void gettersReturnDefaultValuesIfNotConfigured() {
        final DefaultLiveEntitiesMetricsConfig underTest = DefaultLiveEntitiesMetricsConfig.of(ConfigFactory.empty());

        softly.assertThat(underTest.isEnabled())
                .as(LiveEntitiesMetricsConfig.LiveEntitiesMetricsConfigValue.ENABLED.getConfigPath())
                .isEqualTo(LiveEntitiesMetricsConfig.LiveEntitiesMetricsConfigValue.ENABLED.getDefaultValue());
        softly.assertThat(underTest.getRefreshInterval())
                .as(LiveEntitiesMetricsConfig.LiveEntitiesMetricsConfigValue.REFRESH_INTERVAL.getConfigPath())
                .isEqualTo(LiveEntitiesMetricsConfig.LiveEntitiesMetricsConfigValue.REFRESH_INTERVAL.getDefaultValue());
    }

    @Test
    public void gettersReturnConfiguredValues() {
        final DefaultLiveEntitiesMetricsConfig underTest = DefaultLiveEntitiesMetricsConfig.of(testConfig);

        softly.assertThat(underTest.isEnabled())
                .as(LiveEntitiesMetricsConfig.LiveEntitiesMetricsConfigValue.ENABLED.getConfigPath())
                .isFalse();
        softly.assertThat(underTest.getRefreshInterval())
                .as(LiveEntitiesMetricsConfig.LiveEntitiesMetricsConfigValue.REFRESH_INTERVAL.getConfigPath())
                .isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    public void ofDefaultsReturnsConfigWithDefaultValues() {
        final DefaultLiveEntitiesMetricsConfig underTest = DefaultLiveEntitiesMetricsConfig.ofDefaults();

        softly.assertThat(underTest.isEnabled())
                .as("enabled")
                .isTrue();
        softly.assertThat(underTest.getRefreshInterval())
                .as("refresh-interval")
                .isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    public void toStringReturnsExpected() {
        final DefaultLiveEntitiesMetricsConfig underTest = DefaultLiveEntitiesMetricsConfig.of(ConfigFactory.empty());

        softly.assertThat(underTest.toString())
                .contains(underTest.getClass().getSimpleName())
                .contains("enabled")
                .contains("refreshInterval");
    }

}
