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
package org.eclipse.ditto.internal.utils.persistence.mongo.config;

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
 * Unit test for {@link DefaultCircuitBreakerConfig}.
 */
public final class DefaultCircuitBreakerConfigTest {

    private static Config circuitBreakerTestConfig;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void initTestFixture() {
        circuitBreakerTestConfig = ConfigFactory.load("circuit-breaker-test");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultCircuitBreakerConfig.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultCircuitBreakerConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void getMaxFailuresReturnsDefaultValueIfConfigIsEmpty() {
        final DefaultCircuitBreakerConfig underTest = DefaultCircuitBreakerConfig.of(ConfigFactory.empty());

        softly.assertThat(underTest.getMaxFailures()).isEqualTo(5);
    }

    @Test
    public void getMaxFailuresReturnsValueOfConfigFile() {
        final DefaultCircuitBreakerConfig underTest = DefaultCircuitBreakerConfig.of(circuitBreakerTestConfig);

        softly.assertThat(underTest.getMaxFailures()).isEqualTo(23);
    }

    @Test
    public void toStringContainsExpected() {
        final DefaultCircuitBreakerConfig underTest = DefaultCircuitBreakerConfig.of(circuitBreakerTestConfig);

        softly.assertThat(underTest.toString()).contains(underTest.getClass().getSimpleName())
            .contains("maxFailures").contains("5")
            .contains("timeoutConfig");
    }

}
