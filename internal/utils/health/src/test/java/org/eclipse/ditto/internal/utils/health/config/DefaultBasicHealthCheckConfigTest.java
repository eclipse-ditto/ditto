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
 * Unit test for {@link DefaultBasicHealthCheckConfig}.
 */
public final class DefaultBasicHealthCheckConfigTest {

    private static Config healthCheckConfig;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void initTestFixture() {
        healthCheckConfig = ConfigFactory.load("health-check-test");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultBasicHealthCheckConfig.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultBasicHealthCheckConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void underTestReturnsDefaultValuesIfBaseConfigIsEmpty() {
        final DefaultBasicHealthCheckConfig underTest = DefaultBasicHealthCheckConfig.of(ConfigFactory.empty());

        softly.assertThat(underTest.isEnabled())
                .as(BasicHealthCheckConfig.HealthCheckConfigValue.ENABLED.getConfigPath())
                .isEqualTo(BasicHealthCheckConfig.HealthCheckConfigValue.ENABLED.getDefaultValue());
        softly.assertThat(underTest.getInterval())
                .as(BasicHealthCheckConfig.HealthCheckConfigValue.INTERVAL.getConfigPath())
                .isEqualTo(BasicHealthCheckConfig.HealthCheckConfigValue.INTERVAL.getDefaultValue());
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final DefaultBasicHealthCheckConfig underTest = DefaultBasicHealthCheckConfig.of(healthCheckConfig);

        softly.assertThat(underTest.isEnabled())
                .as(BasicHealthCheckConfig.HealthCheckConfigValue.ENABLED.getConfigPath())
                .isTrue();
        softly.assertThat(underTest.getInterval())
                .as(BasicHealthCheckConfig.HealthCheckConfigValue.INTERVAL.getConfigPath())
                .isEqualTo(Duration.ofSeconds(13L));
    }

}
