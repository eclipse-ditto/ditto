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
package org.eclipse.ditto.services.concierge.common;

import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Duration;

import org.assertj.core.api.JUnitSoftAssertions;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Tests {@link org.eclipse.ditto.services.concierge.common.DefaultPersistenceIdsConfig}.
 */
public class DefaultPersistenceIdsConfigTest {

    private static final Config PERSISTENCE_CLEANUP_CONFIG = ConfigFactory.load("persistence-cleanup-test");

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultPersistenceIdsConfig.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultPersistenceIdsConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void underTestReturnsDefaultValuesIfBaseConfigWasEmpty() {
        final PersistenceIdsConfig underTest = DefaultPersistenceIdsConfig.of(ConfigFactory.empty());

        softly.assertThat(underTest.getBurst())
                .as(PersistenceIdsConfig.ConfigValue.BURST.getConfigPath())
                .isEqualTo(PersistenceIdsConfig.ConfigValue.BURST.getDefaultValue());
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final PersistenceIdsConfig underTest = createFromConfig();

        softly.assertThat(underTest.getBurst())
                .as(PersistenceIdsConfig.ConfigValue.BURST.getConfigPath())
                .isEqualTo(600);

        softly.assertThat(underTest.getStreamRequestTimeout())
                .as(PersistenceIdsConfig.ConfigValue.STREAM_REQUEST_TIMEOUT.getConfigPath())
                .isEqualTo(Duration.ofSeconds(700L));

        softly.assertThat(underTest.getStreamIdleTimeout())
                .as(PersistenceIdsConfig.ConfigValue.STREAM_IDLE_TIMEOUT.getConfigPath())
                .isEqualTo(Duration.ofSeconds(800L));

        softly.assertThat(underTest.getMinBackoff())
                .as(PersistenceIdsConfig.ConfigValue.MIN_BACKOFF.getConfigPath())
                .isEqualTo(Duration.ofSeconds(900L));

        softly.assertThat(underTest.getMaxBackoff())
                .as(PersistenceIdsConfig.ConfigValue.MAX_BACKOFF.getConfigPath())
                .isEqualTo(Duration.ofSeconds(1000L));

        softly.assertThat(underTest.getMaxRestarts())
                .as(PersistenceIdsConfig.ConfigValue.MAX_RESTARTS.getConfigPath())
                .isEqualTo(1100);

        softly.assertThat(underTest.getRecovery())
                .as(PersistenceIdsConfig.ConfigValue.RECOVERY.getConfigPath())
                .isEqualTo(Duration.ofSeconds(1200L));
    }

    private PersistenceIdsConfig createFromConfig() {
        return DefaultPersistenceCleanupConfig.of(PERSISTENCE_CLEANUP_CONFIG).getPersistenceIdsConfig();
    }

}
