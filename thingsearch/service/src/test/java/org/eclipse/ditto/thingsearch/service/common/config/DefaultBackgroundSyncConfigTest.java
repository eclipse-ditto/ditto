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
package org.eclipse.ditto.thingsearch.service.common.config;

import static org.mutabilitydetector.unittesting.AllowedReason.provided;
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
 * Tests {@link DefaultBackgroundSyncConfig}.
 */
public final class DefaultBackgroundSyncConfigTest {

    private static Config backgroundSyncTestConfig;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void initTestFixture() {
        backgroundSyncTestConfig = ConfigFactory.load("background-sync-config-test");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultBackgroundSyncConfig.class, areImmutable(), provided(Config.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultBackgroundSyncConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void gettersReturnConfiguredValues() {
        final BackgroundSyncConfig underTest = DefaultBackgroundSyncConfig.fromUpdaterConfig(backgroundSyncTestConfig);

        softly.assertThat(underTest.isEnabled())
                .as(BackgroundSyncConfig.ConfigValue.ENABLED.getConfigPath())
                .isEqualTo(false);
        softly.assertThat(underTest.getQuietPeriod())
                .as(BackgroundSyncConfig.ConfigValue.QUIET_PERIOD.getConfigPath())
                .isEqualTo(Duration.ofHours(1L));
        softly.assertThat(underTest.getIdleTimeout())
                .as(BackgroundSyncConfig.ConfigValue.IDLE_TIMEOUT.getConfigPath())
                .isEqualTo(Duration.ofHours(2L));
        softly.assertThat(underTest.getKeptEvents())
                .as(BackgroundSyncConfig.ConfigValue.KEEP_EVENTS.getConfigPath())
                .isEqualTo(3);
        softly.assertThat(underTest.getThrottleThroughput())
                .as(BackgroundSyncConfig.ConfigValue.THROTTLE_THROUGHPUT.getConfigPath())
                .isEqualTo(4);
        softly.assertThat(underTest.getThrottlePeriod())
                .as(BackgroundSyncConfig.ConfigValue.THROTTLE_PERIOD.getConfigPath())
                .isEqualTo(Duration.ofHours(5L));
        softly.assertThat(underTest.getMinBackoff())
                .as(BackgroundSyncConfig.ConfigValue.MIN_BACKOFF.getConfigPath())
                .isEqualTo(Duration.ofHours(6L));
        softly.assertThat(underTest.getMaxBackoff())
                .as(BackgroundSyncConfig.ConfigValue.MAX_BACKOFF.getConfigPath())
                .isEqualTo(Duration.ofHours(7L));
        softly.assertThat(underTest.getMaxRestarts())
                .as(BackgroundSyncConfig.ConfigValue.MAX_RESTARTS.getConfigPath())
                .isEqualTo(8);
        softly.assertThat(underTest.getRecovery())
                .as(BackgroundSyncConfig.ConfigValue.RECOVERY.getConfigPath())
                .isEqualTo(Duration.ofHours(9L));
        softly.assertThat(underTest.getToleranceWindow())
                .as(BackgroundSyncConfig.ConfigValue.TOLERANCE_WINDOW.getConfigPath())
                .isEqualTo(Duration.ofHours(10L));
        softly.assertThat(underTest.getPolicyAskTimeout())
                .as(BackgroundSyncConfig.ConfigValue.POLICY_ASK_TIMEOUT.getConfigPath())
                .isEqualTo(Duration.ofHours(11L));
    }
}
