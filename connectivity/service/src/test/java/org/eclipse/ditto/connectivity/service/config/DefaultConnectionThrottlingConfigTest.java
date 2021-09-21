/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.config;

import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Duration;

import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.base.service.config.ThrottlingConfig;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link org.eclipse.ditto.connectivity.service.config.DefaultConnectionThrottlingConfig}.
 */
public class DefaultConnectionThrottlingConfigTest {

    private static Config connectionThrottlingTestConf;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void initTestFixture() {
        connectionThrottlingTestConf = ConfigFactory.load("connection-throttling-test");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultConnectionThrottlingConfig.class,
                areImmutable(),
                provided(ThrottlingConfig.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultConnectionThrottlingConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void underTestReturnsDefaultValuesIfBaseConfigWasEmpty() {
        final ConnectionThrottlingConfig underTest = DefaultConnectionThrottlingConfig.of(ConfigFactory.empty());
        softly.assertThat(underTest.getMaxInFlightFactor())
                .as(ConnectionThrottlingConfig.ConfigValue.MAX_IN_FLIGHT_FACTOR.getConfigPath())
                .isEqualTo(ConnectionThrottlingConfig.ConfigValue.MAX_IN_FLIGHT_FACTOR.getDefaultValue());
        softly.assertThat(underTest.getThrottlingDetectionTolerance())
                .as(ConnectionThrottlingConfig.ConfigValue.THROTTLING_DETECTION_TOLERANCE.getConfigPath())
                .isEqualTo(ConnectionThrottlingConfig.ConfigValue.THROTTLING_DETECTION_TOLERANCE.getDefaultValue());
        softly.assertThat(underTest.getInterval())
                .as(ThrottlingConfig.ConfigValue.INTERVAL.getConfigPath())
                .isEqualTo(ThrottlingConfig.ConfigValue.INTERVAL.getDefaultValue());
        softly.assertThat(underTest.getLimit())
                .as(ThrottlingConfig.ConfigValue.LIMIT.getConfigPath())
                .isEqualTo(ThrottlingConfig.ConfigValue.LIMIT.getDefaultValue());
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final ConnectionThrottlingConfig underTest = DefaultConnectionThrottlingConfig.of(connectionThrottlingTestConf);
        softly.assertThat(underTest.getMaxInFlightFactor())
                .as(ConnectionThrottlingConfig.ConfigValue.MAX_IN_FLIGHT_FACTOR.getConfigPath())
                .isEqualTo(2.0);
        softly.assertThat(underTest.getThrottlingDetectionTolerance())
                .as(ConnectionThrottlingConfig.ConfigValue.THROTTLING_DETECTION_TOLERANCE.getConfigPath())
                .isEqualTo(0.05);
        softly.assertThat(underTest.getLimit())
                .as(ThrottlingConfig.ConfigValue.LIMIT.getConfigPath())
                .isEqualTo(4711);
        softly.assertThat(underTest.getInterval())
                .as(ThrottlingConfig.ConfigValue.INTERVAL.getConfigPath())
                .isEqualTo(Duration.ofSeconds(42));
    }

}
