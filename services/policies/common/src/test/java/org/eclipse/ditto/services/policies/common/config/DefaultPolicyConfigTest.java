/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.policies.common.config;

import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Duration;

import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.services.base.config.supervision.DefaultSupervisorConfig;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link DefaultPolicyConfig}.
 */
public final class DefaultPolicyConfigTest {

    private static Config commandTestConfig;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void initTestFixture() {
        commandTestConfig = ConfigFactory.load("policy-test");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultPolicyConfig.class, areImmutable(),
                provided(DefaultSupervisorConfig.class)
                        .areAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultPolicyConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void underTestReturnsDefaultValuesIfBaseConfigWasEmpty() {
        final DefaultPolicyConfig underTest = DefaultPolicyConfig.of(ConfigFactory.parseString("policy.supervisor {}"));

        softly.assertThat(underTest.getSubjectExpiryGranularity())
                .as(PolicyConfig.PolicyConfigValue.SUBJECT_EXPIRY_GRANULARITY.getConfigPath())
                .isEqualTo(Duration.ofHours(1L));
    }

    @Test
    public void underTestReturnsValuesOfBaseConfig() {
        final DefaultPolicyConfig underTest = DefaultPolicyConfig.of(commandTestConfig);

        softly.assertThat(underTest.getSubjectExpiryGranularity())
                .as(PolicyConfig.PolicyConfigValue.SUBJECT_EXPIRY_GRANULARITY.getConfigPath())
                .isEqualTo(Duration.ofSeconds(10));
    }

}
