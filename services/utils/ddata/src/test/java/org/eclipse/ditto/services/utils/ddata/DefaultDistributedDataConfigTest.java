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
package org.eclipse.ditto.services.utils.ddata;

import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Duration;

import org.assertj.core.api.JUnitSoftAssertions;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.mutabilitydetector.unittesting.AllowedReason;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link DefaultDistributedDataConfig}.
 */
public final class DefaultDistributedDataConfigTest {

    private static Config testConfig;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void initTestFixture() {
        testConfig = ConfigFactory.load("ditto-ddata-test");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultDistributedDataConfig.class, areImmutable(),
                AllowedReason.provided(DefaultAkkaReplicatorConfig.class).areAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultDistributedDataConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void underTestReturnsDefaultValuesIfBaseConfigWasEmpty() {
        final DefaultDistributedDataConfig underTest = DefaultDistributedDataConfig.of(ConfigFactory.empty());

        softly.assertThat(underTest.getReadTimeout())
                .as(DistributedDataConfig.DistributedDataConfigValue.READ_TIMEOUT.getConfigPath())
                .isEqualTo(DistributedDataConfig.DistributedDataConfigValue.READ_TIMEOUT.getDefaultValue());
        softly.assertThat(underTest.getWriteTimeout())
                .as(DistributedDataConfig.DistributedDataConfigValue.WRITE_TIMEOUT.getConfigPath())
                .isEqualTo(DistributedDataConfig.DistributedDataConfigValue.WRITE_TIMEOUT.getDefaultValue());
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final DefaultDistributedDataConfig underTest = DefaultDistributedDataConfig.of(testConfig);

        softly.assertThat(underTest.getReadTimeout())
                .as(DistributedDataConfig.DistributedDataConfigValue.READ_TIMEOUT.getConfigPath())
                .isEqualTo(Duration.ofSeconds(42));
        softly.assertThat(underTest.getWriteTimeout())
                .as(DistributedDataConfig.DistributedDataConfigValue.WRITE_TIMEOUT.getConfigPath())
                .isEqualTo(Duration.ofSeconds(1337));
    }

    @Test
    public void underTestReturnsValuesOfConfigFileWithOverwrites() {
        final String name = "nomen-est-omen";
        final String role = "da-role";

        final DefaultDistributedDataConfig underTest = DefaultDistributedDataConfig.of(testConfig, name, role);

        softly.assertThat(underTest.getReadTimeout())
                .as(DistributedDataConfig.DistributedDataConfigValue.READ_TIMEOUT.getConfigPath())
                .isEqualTo(Duration.ofSeconds(42));
        softly.assertThat(underTest.getWriteTimeout())
                .as(DistributedDataConfig.DistributedDataConfigValue.WRITE_TIMEOUT.getConfigPath())
                .isEqualTo(Duration.ofSeconds(1337));
    }

}
