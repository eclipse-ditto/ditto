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
package org.eclipse.ditto.policies.service.common.config;

import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Duration;

import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.base.service.config.supervision.DefaultSupervisorConfig;
import org.eclipse.ditto.internal.utils.persistentactors.cleanup.CleanupConfig;
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
        commandTestConfig = ConfigFactory.load("default-policy-config-test");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultPolicyConfig.class, areImmutable(),
                provided(DefaultSupervisorConfig.class, PolicyAnnouncementConfig.class, CleanupConfig.class)
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

        softly.assertThat(underTest.getSubjectDeletionAnnouncementGranularity())
                .as(PolicyConfig.PolicyConfigValue.SUBJECT_DELETION_ANNOUNCEMENT_GRANULARITY.getConfigPath())
                .isEqualTo(Duration.ofMinutes(1L));

        softly.assertThat(underTest.getSubjectIdResolver())
                .as(PolicyConfig.PolicyConfigValue.SUBJECT_ID_RESOLVER.getConfigPath())
                .isEqualTo(PolicyConfig.PolicyConfigValue.SUBJECT_ID_RESOLVER.getDefaultValue());
    }

    @Test
    public void underTestReturnsValuesOfBaseConfig() {
        final DefaultPolicyConfig underTest = DefaultPolicyConfig.of(commandTestConfig);

        softly.assertThat(underTest.getSubjectExpiryGranularity())
                .as(PolicyConfig.PolicyConfigValue.SUBJECT_EXPIRY_GRANULARITY.getConfigPath())
                .isEqualTo(Duration.ofSeconds(10));

        softly.assertThat(underTest.getSubjectDeletionAnnouncementGranularity())
                .as(PolicyConfig.PolicyConfigValue.SUBJECT_DELETION_ANNOUNCEMENT_GRANULARITY.getConfigPath())
                .isEqualTo(Duration.ofSeconds(11L));

        softly.assertThat(underTest.getSubjectIdResolver())
                .as(PolicyConfig.PolicyConfigValue.SUBJECT_ID_RESOLVER.getConfigPath())
                .isEqualTo("IrredeemableSubjectIdResolver");

        softly.assertThat(underTest.getPolicyAnnouncementConfig())
                .as(DefaultPolicyAnnouncementConfig.CONFIG_PATH)
                .isEqualTo(PolicyAnnouncementConfig.of(ConfigFactory.load("policy-announcement-config-test.conf")));
    }

}
