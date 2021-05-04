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
package org.eclipse.ditto.gateway.service.util.config.health;

import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Duration;

import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.internal.utils.health.config.BasicHealthCheckConfig;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link DefaultHealthCheckConfig}.
 */
public final class DefaultHealthCheckConfigTest {

    private static Config healthCheckTestConfig;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void initTestFixture() {
        healthCheckTestConfig = ConfigFactory.load("health-check-test");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultHealthCheckConfig.class,
                areImmutable(),
                provided(BasicHealthCheckConfig.class, HealthCheckConfig.ClusterRolesConfig.class).areAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultHealthCheckConfig.class)
                .usingGetClass()
                .verify();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void underTestReturnsDefaultValuesIfBaseConfigWasEmpty() {
        final DefaultHealthCheckConfig underTest = DefaultHealthCheckConfig.of(ConfigFactory.empty());

        softly.assertThat(underTest.getServiceTimeout())
                .as(HealthCheckConfig.HealthCheckConfigValue.SERVICE_TIMEOUT.getConfigPath())
                .isEqualTo(HealthCheckConfig.HealthCheckConfigValue.SERVICE_TIMEOUT.getDefaultValue());
        softly.assertThat(underTest.isEnabled())
                .as(BasicHealthCheckConfig.HealthCheckConfigValue.ENABLED.getConfigPath())
                .isEqualTo(BasicHealthCheckConfig.HealthCheckConfigValue.ENABLED.getDefaultValue());
        softly.assertThat(underTest.getInterval())
                .as(BasicHealthCheckConfig.HealthCheckConfigValue.INTERVAL.getConfigPath())
                .isEqualTo(BasicHealthCheckConfig.HealthCheckConfigValue.INTERVAL.getDefaultValue());
        softly.assertThat(underTest.getClusterRolesConfig()).satisfies(clusterRolesConfig -> {
            softly.assertThat(clusterRolesConfig.isEnabled())
                    .as(HealthCheckConfig.ClusterRolesConfig.ClusterRolesConfigValue.ENABLED.getConfigPath())
                    .isEqualTo(HealthCheckConfig.ClusterRolesConfig.ClusterRolesConfigValue.ENABLED.getDefaultValue());
            softly.assertThat(clusterRolesConfig.getExpectedClusterRoles())
                    .as(HealthCheckConfig.ClusterRolesConfig.ClusterRolesConfigValue.EXPECTED.getConfigPath())
                    .containsOnlyElementsOf((Iterable) HealthCheckConfig.ClusterRolesConfig.ClusterRolesConfigValue.EXPECTED.getDefaultValue());
        });
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final DefaultHealthCheckConfig underTest = DefaultHealthCheckConfig.of(healthCheckTestConfig);

        softly.assertThat(underTest.getServiceTimeout())
                .as(HealthCheckConfig.HealthCheckConfigValue.SERVICE_TIMEOUT.getConfigPath())
                .isEqualTo(Duration.ofSeconds(23L));
        softly.assertThat(underTest.isEnabled())
                .as(BasicHealthCheckConfig.HealthCheckConfigValue.ENABLED.getConfigPath())
                .isTrue();
        softly.assertThat(underTest.getInterval())
                .as(BasicHealthCheckConfig.HealthCheckConfigValue.INTERVAL.getConfigPath())
                .isEqualTo(Duration.ofSeconds(42L));
        softly.assertThat(underTest.getClusterRolesConfig()).satisfies(clusterRolesConfig -> {
            softly.assertThat(clusterRolesConfig.isEnabled())
                    .as(HealthCheckConfig.ClusterRolesConfig.ClusterRolesConfigValue.ENABLED.getConfigPath())
                    .isTrue();
            softly.assertThat(clusterRolesConfig.getExpectedClusterRoles())
                    .as(HealthCheckConfig.ClusterRolesConfig.ClusterRolesConfigValue.EXPECTED.getConfigPath())
                    .containsOnly("foo", "bar", "baz");
        });
    }

}
