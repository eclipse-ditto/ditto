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
package org.eclipse.ditto.internal.utils.health.config;

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
 * Unit test for {@link DefaultMetricsReporterConfig}.
 */
public final class DefaultMetricsReporterConfigTest {

    private static Config metricsReporterConfig;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void initTestFixture() {
        final Config healthCheckConfig = ConfigFactory.load("health-check-test");
        metricsReporterConfig = healthCheckConfig.getConfig("health-check").getConfig("persistence");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultMetricsReporterConfig.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultMetricsReporterConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void underTestReturnsDefaultValuesIfBaseConfigIsEmpty() {
        final DefaultMetricsReporterConfig underTest = DefaultMetricsReporterConfig.of(ConfigFactory.empty());

        softly.assertThat(underTest.getResolution())
                .as(MetricsReporterConfig.MetricsReporterConfigValue.RESOLUTION.getConfigPath())
                .isEqualTo(MetricsReporterConfig.MetricsReporterConfigValue.RESOLUTION.getDefaultValue());
        softly.assertThat(underTest.getHistory())
                .as(MetricsReporterConfig.MetricsReporterConfigValue.HISTORY.getConfigPath())
                .isEqualTo(MetricsReporterConfig.MetricsReporterConfigValue.HISTORY.getDefaultValue());
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final DefaultMetricsReporterConfig underTest = DefaultMetricsReporterConfig.of(metricsReporterConfig);

        softly.assertThat(underTest.getResolution())
                .as(MetricsReporterConfig.MetricsReporterConfigValue.RESOLUTION.getConfigPath())
                .isEqualTo(Duration.ofSeconds(23));
        softly.assertThat(underTest.getHistory())
                .as(MetricsReporterConfig.MetricsReporterConfigValue.HISTORY.getConfigPath())
                .isEqualTo(7);
    }

}
