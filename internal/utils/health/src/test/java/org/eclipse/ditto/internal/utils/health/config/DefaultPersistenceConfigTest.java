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
package org.eclipse.ditto.internal.utils.health.config;

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
 * Unit test for {@link DefaultPersistenceConfig}.
 */
public final class DefaultPersistenceConfigTest {

    private static Config persistenceConfig;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void initTestFixture() {
        final Config healthCheckConfig = ConfigFactory.load("health-check-test");
        persistenceConfig = healthCheckConfig.getConfig("health-check");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultPersistenceConfig.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultPersistenceConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void underTestReturnsDefaultValuesIfBaseConfigIsEmpty() {
        final DefaultPersistenceConfig underTest = DefaultPersistenceConfig.of(ConfigFactory.empty());

        softly.assertThat(underTest.isEnabled())
                .as(PersistenceConfig.PersistenceConfigValue.ENABLED.getConfigPath())
                .isEqualTo(PersistenceConfig.PersistenceConfigValue.ENABLED.getDefaultValue());
        softly.assertThat(underTest.getTimeout())
                .as(PersistenceConfig.PersistenceConfigValue.TIMEOUT.getConfigPath())
                .isEqualTo(PersistenceConfig.PersistenceConfigValue.TIMEOUT.getDefaultValue());
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final DefaultPersistenceConfig underTest = DefaultPersistenceConfig.of(persistenceConfig);

        softly.assertThat(underTest.isEnabled())
                .as(PersistenceConfig.PersistenceConfigValue.ENABLED.getConfigPath())
                .isTrue();
        softly.assertThat(underTest.getTimeout())
                .as(PersistenceConfig.PersistenceConfigValue.TIMEOUT.getConfigPath())
                .isEqualTo(Duration.ofSeconds(42L));
    }

}
