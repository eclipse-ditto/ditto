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

import java.time.Duration;

import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.internal.utils.cacheloaders.config.AskWithRetryConfig;
import org.eclipse.ditto.internal.utils.cacheloaders.config.RetryStrategy;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link DefaultEnforcementConfig}.
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

    @Test
    public void createdPolicyRollbackMakesOneAttemptIfNotConfigured() {
        final DefaultEnforcementConfig underTest = DefaultEnforcementConfig.of(ConfigFactory.empty());

        softly.assertThat(underTest.getCreatedPolicyRollbackConfig().getRetryStrategy())
                .isEqualTo(RetryStrategy.OFF);
    }

    @Test
    public void createdPolicyRollbackIsReadFromTheConfigFile() {
        final DefaultEnforcementConfig underTest = DefaultEnforcementConfig.of(enforcementTestConf);

        softly.assertThat(underTest.getCreatedPolicyRollbackConfig().getAskTimeout())
                .isEqualTo(Duration.ofSeconds(44L));
    }

    @Test
    public void shippedCreatedPolicyRollbackRetriesWithBackOff() {
        final DefaultEnforcementConfig underTest = DefaultEnforcementConfig.of(
                ConfigFactory.parseResources("ditto-enforcement.conf").resolve().getConfig("ditto"));
        final AskWithRetryConfig rollback = underTest.getCreatedPolicyRollbackConfig();

        softly.assertThat(rollback.getAskTimeout()).isEqualTo(Duration.ofSeconds(3L));
        softly.assertThat(rollback.getRetryStrategy()).isEqualTo(RetryStrategy.BACKOFF_DELAY);
        softly.assertThat(rollback.getRetryAttempts()).isEqualTo(3);
        softly.assertThat(rollback.getBackoffDelayMin()).isEqualTo(Duration.ofMillis(500L));
        softly.assertThat(rollback.getBackoffDelayMax()).isEqualTo(Duration.ofSeconds(4L));
        softly.assertThat(rollback.getBackoffDelayRandomFactor()).isEqualTo(0.5);
    }

}
