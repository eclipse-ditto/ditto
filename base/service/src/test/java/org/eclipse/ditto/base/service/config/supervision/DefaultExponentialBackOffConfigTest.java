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
package org.eclipse.ditto.base.service.config.supervision;

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
 * Unit test for {@link DefaultExponentialBackOffConfig}.
 */
public final class DefaultExponentialBackOffConfigTest {

    private static Config exponentialBackOffTestConf;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void initTestFixture() {
        exponentialBackOffTestConf = ConfigFactory.load("exponentialBackOff-test");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultExponentialBackOffConfig.class,
                areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultExponentialBackOffConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void underTestReturnsDefaultValuesIfBaseConfigWasEmpty() {
        final DefaultExponentialBackOffConfig underTest = DefaultExponentialBackOffConfig.of(ConfigFactory.empty());

        softly.assertThat(underTest.getMin())
                .as(ExponentialBackOffConfig.ExponentialBackOffConfigValue.MIN.getConfigPath())
                .isEqualTo(ExponentialBackOffConfig.ExponentialBackOffConfigValue.MIN.getDefaultValue());

        softly.assertThat(underTest.getMax())
                .as(ExponentialBackOffConfig.ExponentialBackOffConfigValue.MAX.getConfigPath())
                .isEqualTo(ExponentialBackOffConfig.ExponentialBackOffConfigValue.MAX.getDefaultValue());

        softly.assertThat(underTest.getRandomFactor())
                .as(ExponentialBackOffConfig.ExponentialBackOffConfigValue.RANDOM_FACTOR.getConfigPath())
                .isEqualTo(ExponentialBackOffConfig.ExponentialBackOffConfigValue.RANDOM_FACTOR.getDefaultValue());
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final DefaultExponentialBackOffConfig underTest = DefaultExponentialBackOffConfig.of(exponentialBackOffTestConf);

        softly.assertThat(underTest.getMin())
                .as(ExponentialBackOffConfig.ExponentialBackOffConfigValue.MIN.getConfigPath())
                .isEqualTo(Duration.ofSeconds(2L));

        softly.assertThat(underTest.getMax())
                .as(ExponentialBackOffConfig.ExponentialBackOffConfigValue.MAX.getConfigPath())
                .isEqualTo(Duration.ofSeconds(20L));

        softly.assertThat(underTest.getRandomFactor())
                .as(ExponentialBackOffConfig.ExponentialBackOffConfigValue.RANDOM_FACTOR.getConfigPath())
                .isEqualTo(0.3D);
    }
}
