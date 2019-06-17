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

import static org.mutabilitydetector.unittesting.AllowedReason.provided;
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
 * Tests {@link org.eclipse.ditto.services.concierge.common.DefaultPersistenceCleanupConfig}.
 */
public final class DefaultPersistenceCleanupConfigTest {

    private static final Config PERSISTENCE_CLEANUP_CONFIG = ConfigFactory.load("persistence-cleanup-test");

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultPersistenceCleanupConfig.class, areImmutable(),
                provided(CreditDecisionConfig.class, PersistenceIdsConfig.class).areAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultPersistenceCleanupConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void underTestReturnsDefaultValuesIfBaseConfigWasEmpty() {
        final PersistenceCleanupConfig underTest = DefaultPersistenceCleanupConfig.of(ConfigFactory.empty());

        softly.assertThat(underTest.getQuietPeriod())
                .as(PersistenceCleanupConfig.ConfigValue.QUIET_PERIOD.getConfigPath())
                .isEqualTo(PersistenceCleanupConfig.ConfigValue.QUIET_PERIOD.getDefaultValue());
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final PersistenceCleanupConfig underTest = createFromConfig();

        softly.assertThat(underTest.getQuietPeriod())
                .as(PersistenceCleanupConfig.ConfigValue.QUIET_PERIOD.getConfigPath())
                .isEqualTo(Duration.ofSeconds(100L));
    }

    private PersistenceCleanupConfig createFromConfig() {
        return DefaultPersistenceCleanupConfig.of(PERSISTENCE_CLEANUP_CONFIG);
    }

}
