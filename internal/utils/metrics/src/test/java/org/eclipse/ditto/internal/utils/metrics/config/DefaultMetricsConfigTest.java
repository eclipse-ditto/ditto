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
package org.eclipse.ditto.internal.utils.metrics.config;

import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.internal.utils.metrics.config.MetricsConfig.MetricsConfigValue;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link DefaultMetricsConfig}.
 */
public final class DefaultMetricsConfigTest {

    private static Config metricsTestConf;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void initTestFixture() {
        metricsTestConf = ConfigFactory.load("metrics-test");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultMetricsConfig.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultMetricsConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void gettersReturnDefaultValuesIfNotConfigured() {
        final DefaultMetricsConfig underTest = DefaultMetricsConfig.of(ConfigFactory.empty());

        softly.assertThat(underTest.isSystemMetricsEnabled())
                .as(MetricsConfigValue.SYSTEM_METRICS_ENABLED.getConfigPath())
                .isEqualTo(MetricsConfigValue.SYSTEM_METRICS_ENABLED.getDefaultValue());
        softly.assertThat(underTest.isPrometheusEnabled())
                .as(MetricsConfigValue.PROMETHEUS_ENABLED.getConfigPath())
                .isEqualTo(MetricsConfigValue.PROMETHEUS_ENABLED.getDefaultValue());
        softly.assertThat(underTest.getPrometheusHostname())
                .as(MetricsConfigValue.PROMETHEUS_HOSTNAME.getConfigPath())
                .isEqualTo(MetricsConfigValue.PROMETHEUS_HOSTNAME.getDefaultValue());
        softly.assertThat(underTest.getPrometheusPort())
                .as(MetricsConfigValue.PROMETHEUS_PORT.getConfigPath())
                .isEqualTo(MetricsConfigValue.PROMETHEUS_PORT.getDefaultValue());
    }

    @Test
    public void gettersReturnConfiguredValues() {
        final DefaultMetricsConfig underTest = DefaultMetricsConfig.of(metricsTestConf);

        softly.assertThat(underTest.isSystemMetricsEnabled())
                .as(MetricsConfigValue.SYSTEM_METRICS_ENABLED.getConfigPath())
                .isTrue();
        softly.assertThat(underTest.isPrometheusEnabled())
                .as(MetricsConfigValue.PROMETHEUS_ENABLED.getConfigPath())
                .isTrue();
        softly.assertThat(underTest.getPrometheusHostname())
                .as(MetricsConfigValue.PROMETHEUS_HOSTNAME.getConfigPath())
                .isEqualTo("1.1.1.1");
        softly.assertThat(underTest.getPrometheusPort())
                .as(MetricsConfigValue.PROMETHEUS_PORT.getConfigPath())
                .isEqualTo(9999);
    }

}
