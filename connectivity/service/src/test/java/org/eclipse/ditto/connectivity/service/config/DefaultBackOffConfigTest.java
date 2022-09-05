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

import static org.assertj.core.api.Assertions.assertThat;
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
 * Unit test for {@link org.eclipse.ditto.connectivity.service.config.DefaultBackOffConfig}.
 */
public final class DefaultBackOffConfigTest {

    private static Config backOffTestConf;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void initTestFixture() {
        backOffTestConf = ConfigFactory.load("backoff-test");
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final DefaultBackOffConfig underTest = DefaultBackOffConfig.of(backOffTestConf);

        assertThat(underTest.getTimeoutConfig())
                .as("timeoutConfig")
                .satisfies(timeoutConfig -> {
                            softly.assertThat(timeoutConfig.getMinTimeout())
                                    .as(TimeoutConfig.TimeoutConfigValue.MIN_TIMEOUT.getConfigPath())
                                    .isEqualTo(Duration.ofSeconds(1L));
                            softly.assertThat(timeoutConfig.getMaxTimeout())
                                    .as(TimeoutConfig.TimeoutConfigValue.MAX_TIMEOUT.getConfigPath())
                                    .isEqualTo(Duration.ofSeconds(600L));
                        });
    }

    @Test
    public void testEqualsAndHashcode() {
        EqualsVerifier.forClass(DefaultBackOffConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void testImmutability() {
        assertInstancesOf(DefaultBackOffConfig.class,
                areImmutable());
    }

}
