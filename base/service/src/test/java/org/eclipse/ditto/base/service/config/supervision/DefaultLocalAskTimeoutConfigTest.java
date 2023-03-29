/*
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
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
 * Unit test for {@link DefaultLocalAskTimeoutConfigTest}.
 */
public class DefaultLocalAskTimeoutConfigTest {

    private static Config supervisorLocalAskTimeoutConfig;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void initTestFixture() {
        supervisorLocalAskTimeoutConfig = ConfigFactory.load("local-ask-timout-test");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultLocalAskTimeoutConfig.class,
                areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultLocalAskTimeoutConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void underTestReturnsDefaultValuesIfBaseConfigWasEmpty() {
        final DefaultLocalAskTimeoutConfig underTest = DefaultLocalAskTimeoutConfig.of(ConfigFactory.empty());

        softly.assertThat(underTest.getLocalAckTimeout())
                .as(LocalAskTimeoutConfig.LocalAskTimeoutConfigValue.ASK_TIMEOUT.getConfigPath())
                .isEqualTo(LocalAskTimeoutConfig.LocalAskTimeoutConfigValue.ASK_TIMEOUT.getDefaultValue());
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final DefaultLocalAskTimeoutConfig underTest = DefaultLocalAskTimeoutConfig.of(supervisorLocalAskTimeoutConfig);

        softly.assertThat(underTest.getLocalAckTimeout())
                .as(LocalAskTimeoutConfig.LocalAskTimeoutConfigValue.ASK_TIMEOUT.getConfigPath())
                .isEqualTo(Duration.ofSeconds(10L));
    }
}