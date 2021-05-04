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

package org.eclipse.ditto.connectivity.service.messaging.backoff;

import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Duration;

import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.connectivity.service.config.DefaultTimeoutConfig;
import org.eclipse.ditto.connectivity.service.config.TimeoutConfig;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link org.eclipse.ditto.connectivity.service.config.DefaultTimeoutConfig}.
 */
public final class DefaultTimeoutConfigTest {

    private static Config timeoutTestConf;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void initTestFixture() {
        timeoutTestConf = ConfigFactory.load("timeout-test");
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final DefaultTimeoutConfig underTest = DefaultTimeoutConfig.of(timeoutTestConf);

        softly.assertThat(underTest.getMinTimeout())
                .as(TimeoutConfig.TimeoutConfigValue.MIN_TIMEOUT.getConfigPath())
                .isEqualTo(Duration.ofSeconds(1L));
        softly.assertThat(underTest.getMaxTimeout())
                .as(TimeoutConfig.TimeoutConfigValue.MAX_TIMEOUT.getConfigPath())
                .isEqualTo(Duration.ofSeconds(600L));
    }

    @Test
    public void testEqualsAndHashcode() {
        EqualsVerifier.forClass(DefaultTimeoutConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void testImmutability() {
        assertInstancesOf(DefaultTimeoutConfig.class,
                areImmutable());
    }

}
