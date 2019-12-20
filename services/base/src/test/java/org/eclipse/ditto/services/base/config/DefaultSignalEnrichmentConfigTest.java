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
package org.eclipse.ditto.services.base.config;

import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.assertj.core.api.JUnitSoftAssertions;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Tests {@link org.eclipse.ditto.services.base.config.DefaultSignalEnrichmentConfig}.
 */
public final class DefaultSignalEnrichmentConfigTest {

    private static Config signalEnrichmentTestConf;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void initTestFixture() {
        signalEnrichmentTestConf = ConfigFactory.load("signal-enrichment-test");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultSignalEnrichmentConfig.class,
                areImmutable(),
                provided(Config.class).isAlsoImmutable()
        );
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultSignalEnrichmentConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final SignalEnrichmentConfig underTest =
                DefaultSignalEnrichmentConfig.of(signalEnrichmentTestConf);

        softly.assertThat(underTest.getProvider())
                .as(SignalEnrichmentConfig.ConfigValue.PROVIDER.getConfigPath())
                .isEqualTo("MySignalEnrichmentProvider");

        softly.assertThat(underTest.getConfig().root())
                .as(SignalEnrichmentConfig.ConfigValue.CONFIG.getConfigPath())
                .containsOnlyKeys("key")
                .containsValue(ConfigValueFactory.fromAnyRef("value"));
    }

    @Test
    public void renderIsInverseOfConstructor() {
        final SignalEnrichmentConfig underTest =
                DefaultSignalEnrichmentConfig.of(signalEnrichmentTestConf);

        softly.assertThat(DefaultSignalEnrichmentConfig.of(underTest.render())).isEqualTo(underTest);
    }
}
