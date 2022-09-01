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
package org.eclipse.ditto.gateway.service.proxy.config;

import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.assertj.core.api.JUnitSoftAssertions;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Tests {@link DefaultStatisticsShardConfig}.
 */
public final class DefaultStatisticsShardConfigTest {

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultStatisticsShardConfig.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultStatisticsShardConfig.class).verify();
    }

    @Test
    public void underTestReturnsDefaultValuesIfBaseConfigWasEmpty() {
        final StatisticsShardConfig underTest = DefaultStatisticsShardConfig.of(ConfigFactory.empty());

        softly.assertThat(underTest.getRegion())
                .as(StatisticsShardConfig.ConfigValues.REGION.getConfigPath())
                .isEmpty();

        softly.assertThat(underTest.getRole())
                .as(StatisticsShardConfig.ConfigValues.ROLE.getConfigPath())
                .isEmpty();

        softly.assertThat(underTest.getRoot())
                .as(StatisticsShardConfig.ConfigValues.ROOT.getConfigPath())
                .isEmpty();
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final StatisticsShardConfig underTest =
                DefaultStatisticsShardConfig.of(ConfigFactory.load("test-statistics-shard.conf"));

        softly.assertThat(underTest.getRegion())
                .as(StatisticsShardConfig.ConfigValues.REGION.getConfigPath())
                .isEqualTo("metal");

        softly.assertThat(underTest.getRole())
                .as(StatisticsShardConfig.ConfigValues.ROLE.getConfigPath())
                .isEqualTo("shylock");

        softly.assertThat(underTest.getRoot())
                .as(StatisticsShardConfig.ConfigValues.ROOT.getConfigPath())
                .isEqualTo("/user/radish");

    }

}
