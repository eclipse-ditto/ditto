/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.policies.enforcement.config;

import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Duration;

import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.internal.utils.cacheloaders.config.AskWithRetryConfig;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link org.eclipse.ditto.policies.enforcement.config.DefaultEnforcementConfig}.
 */
public final class DefaultEnforcementConfigTest {

    private static Config enforcementTestConf;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void initTestFixture() {
        enforcementTestConf = ConfigFactory.load("enforcement-test");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultEnforcementConfig.class, areImmutable(),
                provided(EntityCreationConfig.class).areAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultEnforcementConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void underTestReturnsDefaultValuesIfBaseConfigWasEmpty() {
        final DefaultEnforcementConfig underTest = DefaultEnforcementConfig.of(ConfigFactory.empty());

        softly.assertThat(underTest.getAskWithRetryConfig().getAskTimeout())
                .as(AskWithRetryConfig.AskWithRetryConfigValue.ASK_TIMEOUT.getConfigPath())
                .isEqualTo(AskWithRetryConfig.AskWithRetryConfigValue.ASK_TIMEOUT.getDefaultValue());
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final DefaultEnforcementConfig underTest = DefaultEnforcementConfig.of(enforcementTestConf);

        softly.assertThat(underTest.getAskWithRetryConfig().getAskTimeout())
                .as(AskWithRetryConfig.AskWithRetryConfigValue.ASK_TIMEOUT.getConfigPath())
                .isEqualTo(Duration.ofSeconds(33L));
    }

}
