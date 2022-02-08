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
package org.eclipse.ditto.thingsearch.service.common.config;

import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.thingsearch.service.common.config.UpdaterConfig.UpdaterConfigValue;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link DefaultUpdaterConfig}.
 */
public final class DefaultUpdaterConfigTest {

    private static Config updaterTestConfig;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void initTestFixture() {
        updaterTestConfig = ConfigFactory.load("updater-test");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultUpdaterConfig.class, areImmutable(),
                provided(BackgroundSyncConfig.class, DefaultStreamConfig.class, DefaultUpdaterPersistenceConfig.class)
                        .isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultUpdaterConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void gettersReturnDefaultValuesIfNotConfigured() {
        final DefaultUpdaterConfig underTest = DefaultUpdaterConfig.of(ConfigFactory.empty());

        softly.assertThat(underTest.isEventProcessingActive())
                .as(UpdaterConfigValue.EVENT_PROCESSING_ACTIVE.getConfigPath())
                .isEqualTo(UpdaterConfigValue.EVENT_PROCESSING_ACTIVE.getDefaultValue());

        softly.assertThat(underTest.getMaxIdleTime())
                .as(UpdaterConfigValue.MAX_IDLE_TIME.getConfigPath())
                .isEqualTo(UpdaterConfigValue.MAX_IDLE_TIME.getDefaultValue());

        softly.assertThat(underTest.getForceUpdateAfterStartRandomFactor())
                .as(UpdaterConfigValue.FORCE_UPDATE_PROBABILITY.getConfigPath())
                .isEqualTo(UpdaterConfigValue.FORCE_UPDATE_AFTER_START_RANDOM_FACTOR.getDefaultValue());

        softly.assertThat(underTest.isForceUpdateAfterStartEnabled())
                .as(UpdaterConfigValue.FORCE_UPDATE_AFTER_START_ENABLED.getConfigPath())
                .isEqualTo(UpdaterConfigValue.FORCE_UPDATE_AFTER_START_ENABLED.getDefaultValue());
    }

    @Test
    public void gettersReturnConfiguredValues() {
        final DefaultUpdaterConfig underTest = DefaultUpdaterConfig.of(updaterTestConfig);
        final Config updaterScopedRawConfig = updaterTestConfig.getConfig(DefaultUpdaterConfig.CONFIG_PATH);

        softly.assertThat(underTest.isEventProcessingActive())
                .as(UpdaterConfigValue.EVENT_PROCESSING_ACTIVE.getConfigPath())
                .isEqualTo(
                        updaterScopedRawConfig.getBoolean(UpdaterConfigValue.EVENT_PROCESSING_ACTIVE.getConfigPath()));

        softly.assertThat(underTest.getMaxIdleTime())
                .as(UpdaterConfigValue.MAX_IDLE_TIME.getConfigPath())
                .isEqualTo(updaterScopedRawConfig.getDuration(UpdaterConfigValue.MAX_IDLE_TIME.getConfigPath()));

        softly.assertThat(underTest.getForceUpdateProbability())
                .as(UpdaterConfigValue.FORCE_UPDATE_PROBABILITY.getConfigPath())
                .isEqualTo(0.125);

        softly.assertThat(underTest.getForceUpdateAfterStartRandomFactor())
                .as(UpdaterConfigValue.FORCE_UPDATE_PROBABILITY.getConfigPath())
                .isEqualTo(3.0);

        softly.assertThat(underTest.isForceUpdateAfterStartEnabled())
                .as(UpdaterConfigValue.FORCE_UPDATE_AFTER_START_ENABLED.getConfigPath())
                .isEqualTo(false);
    }

}
