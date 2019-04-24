/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.gateway.health.config;

import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.time.Duration;

import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.services.gateway.health.config.HealthCheckConfig.ClusterRolesConfig.ClusterRolesConfigValue;
import org.eclipse.ditto.services.utils.health.config.BasicHealthCheckConfig;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link org.eclipse.ditto.services.gateway.health.config.DefaultHealthCheckConfig}.
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

    @Test
    public void testSerializationAndDeserialization() throws IOException, ClassNotFoundException {
        final DefaultHealthCheckConfig underTest = DefaultHealthCheckConfig.of(healthCheckTestConfig);

        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        final ObjectOutput objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
        objectOutputStream.writeObject(underTest);
        objectOutputStream.close();

        final byte[] underTestSerialized = byteArrayOutputStream.toByteArray();

        final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(underTestSerialized);
        final ObjectInput objectInputStream = new ObjectInputStream(byteArrayInputStream);
        final Object underTestDeserialized = objectInputStream.readObject();

        softly.assertThat(underTestDeserialized).isEqualTo(underTest);
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
                    .as(ClusterRolesConfigValue.ENABLED.getConfigPath())
                    .isEqualTo(ClusterRolesConfigValue.ENABLED.getDefaultValue());
            softly.assertThat(clusterRolesConfig.getExpectedClusterRoles())
                    .as(ClusterRolesConfigValue.EXPECTED.getConfigPath())
                    .containsOnlyElementsOf((Iterable) ClusterRolesConfigValue.EXPECTED.getDefaultValue());
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
                    .as(ClusterRolesConfigValue.ENABLED.getConfigPath())
                    .isTrue();
            softly.assertThat(clusterRolesConfig.getExpectedClusterRoles())
                    .as(ClusterRolesConfigValue.EXPECTED.getConfigPath())
                    .containsOnly("foo", "bar", "baz");
        });
    }

}