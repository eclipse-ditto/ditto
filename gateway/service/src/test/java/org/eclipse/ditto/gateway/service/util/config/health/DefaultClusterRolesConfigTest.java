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

import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.assertj.core.api.JUnitSoftAssertions;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link DefaultClusterRolesConfig}.
 */
public final class DefaultClusterRolesConfigTest {

    private static Config clusterRolesTestConfig;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void initTestFixture() {
        clusterRolesTestConfig = ConfigFactory.load("cluster-roles-test");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultClusterRolesConfig.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultClusterRolesConfig.class)
                .usingGetClass()
                .verify();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void underTestReturnsDefaultValuesIfBaseConfigWasEmpty() {
        final DefaultClusterRolesConfig underTest = DefaultClusterRolesConfig.of(ConfigFactory.empty());

        softly.assertThat(underTest.isEnabled())
                .as(HealthCheckConfig.ClusterRolesConfig.ClusterRolesConfigValue.ENABLED.getConfigPath())
                .isEqualTo(HealthCheckConfig.ClusterRolesConfig.ClusterRolesConfigValue.ENABLED.getDefaultValue());
        softly.assertThat(underTest.getExpectedClusterRoles())
                .as(HealthCheckConfig.ClusterRolesConfig.ClusterRolesConfigValue.EXPECTED.getConfigPath())
                .containsOnlyElementsOf((Iterable) HealthCheckConfig.ClusterRolesConfig.ClusterRolesConfigValue.EXPECTED.getDefaultValue());
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final DefaultClusterRolesConfig underTest = DefaultClusterRolesConfig.of(clusterRolesTestConfig);

        softly.assertThat(underTest.isEnabled())
                .as(HealthCheckConfig.ClusterRolesConfig.ClusterRolesConfigValue.ENABLED.getConfigPath())
                .isTrue();
        softly.assertThat(underTest.getExpectedClusterRoles())
                .as(HealthCheckConfig.ClusterRolesConfig.ClusterRolesConfigValue.EXPECTED.getConfigPath())
                .containsOnly("foo", "bar", "baz");
    }

}
