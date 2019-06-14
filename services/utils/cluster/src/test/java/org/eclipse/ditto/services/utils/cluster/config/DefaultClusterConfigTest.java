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
package org.eclipse.ditto.services.utils.cluster.config;

import static org.mutabilitydetector.unittesting.AllowedReason.provided;
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
 * Unit test for {@link org.eclipse.ditto.services.utils.cluster.config.DefaultClusterConfig}.
 */
public final class DefaultClusterConfigTest {

    private static Config clusterTestConf;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void initTestFixture() {
        clusterTestConf = ConfigFactory.load("cluster-test");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultClusterConfig.class,
                areImmutable(),
                provided(Config.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultClusterConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void gettersReturnDefaultValuesIfNotConfigured() {
        final DefaultClusterConfig underTest = DefaultClusterConfig.of(ConfigFactory.empty());

        softly.assertThat(underTest.getNumberOfShards())
                .as(ClusterConfig.ClusterConfigValue.NUMBER_OF_SHARDS.getConfigPath())
                .isEqualTo(ClusterConfig.ClusterConfigValue.NUMBER_OF_SHARDS.getDefaultValue());
    }

    @Test
    public void gettersReturnConfiguredValues() {
        final DefaultClusterConfig underTest = DefaultClusterConfig.of(clusterTestConf);

        softly.assertThat(underTest.getNumberOfShards())
                .as(ClusterConfig.ClusterConfigValue.NUMBER_OF_SHARDS.getConfigPath())
                .isEqualTo(100);
    }

    @Test
    public void toStringReturnsExpected() {
        final DefaultClusterConfig underTest = DefaultClusterConfig.of(ConfigFactory.empty());

        softly.assertThat(underTest.toString())
                .contains(underTest.getClass().getSimpleName())
                .contains("numberOfShards");
    }

}
