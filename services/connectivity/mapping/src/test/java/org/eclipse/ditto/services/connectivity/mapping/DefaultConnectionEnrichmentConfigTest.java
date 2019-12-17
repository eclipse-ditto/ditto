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
package org.eclipse.ditto.services.connectivity.mapping;

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
 * Tests {@link org.eclipse.ditto.services.connectivity.mapping.DefaultConnectionEnrichmentConfig}.
 */
public final class DefaultConnectionEnrichmentConfigTest {

    private static Config connectionEnrichmentTestConf;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void initTestFixture() {
        connectionEnrichmentTestConf = ConfigFactory.load("connection-enrichment-test");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultConnectionEnrichmentConfig.class,
                areImmutable(),
                provided(Config.class).isAlsoImmutable()
        );
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultConnectionEnrichmentConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final ConnectionEnrichmentConfig underTest =
                DefaultConnectionEnrichmentConfig.forActorSystemConfig(connectionEnrichmentTestConf);

        softly.assertThat(underTest.getProvider())
                .as(ConnectionEnrichmentConfig.ConfigValue.PROVIDER.getConfigPath())
                .isEqualTo("MyConnectionEnrichmentProvider");

        softly.assertThat(underTest.getConfig().root())
                .as(ConnectionEnrichmentConfig.ConfigValue.CONFIG.getConfigPath())
                .containsOnlyKeys("key")
                .containsValue(ConfigValueFactory.fromAnyRef("value"));
    }
}
