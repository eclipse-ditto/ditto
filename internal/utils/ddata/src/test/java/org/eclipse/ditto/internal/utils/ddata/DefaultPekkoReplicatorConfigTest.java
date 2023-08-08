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
package org.eclipse.ditto.internal.utils.ddata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Duration;

import org.assertj.core.api.JUnitSoftAssertions;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.mutabilitydetector.unittesting.AllowedReason;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link DefaultPekkoReplicatorConfig}.
 */
public final class DefaultPekkoReplicatorConfigTest {

    private static Config testConfig;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void initTestFixture() {
        testConfig = ConfigFactory.load("pekko-replicator-ddata-test");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultPekkoReplicatorConfig.class, areImmutable(),
                AllowedReason.provided(Config.class).areAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultPekkoReplicatorConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void underTestReturnsDefaultValuesIfBaseConfigWasEmpty() {
        final DefaultPekkoReplicatorConfig underTest = DefaultPekkoReplicatorConfig.of(ConfigFactory.empty());

        softly.assertThat(underTest.getName())
                .as(PekkoReplicatorConfig.PekkoReplicatorConfigValue.NAME.getConfigPath())
                .isEqualTo(PekkoReplicatorConfig.PekkoReplicatorConfigValue.NAME.getDefaultValue());
        softly.assertThat(underTest.getRole())
                .as(PekkoReplicatorConfig.PekkoReplicatorConfigValue.ROLE.getConfigPath())
                .isEqualTo(PekkoReplicatorConfig.PekkoReplicatorConfigValue.ROLE.getDefaultValue());
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final DefaultPekkoReplicatorConfig underTest = DefaultPekkoReplicatorConfig.of(testConfig);

        softly.assertThat(underTest.getName())
                .as(PekkoReplicatorConfig.PekkoReplicatorConfigValue.NAME.getConfigPath())
                .isEqualTo("the-name");
        softly.assertThat(underTest.getRole())
                .as(PekkoReplicatorConfig.PekkoReplicatorConfigValue.ROLE.getConfigPath())
                .isEqualTo("a-role");
        softly.assertThat(underTest.getGossipInterval())
                .as(PekkoReplicatorConfig.PekkoReplicatorConfigValue.GOSSIP_INTERVAL.getConfigPath())
                .isEqualTo(Duration.ofMillis(1337));
        softly.assertThat(underTest.getNotifySubscribersInterval())
                .as(PekkoReplicatorConfig.PekkoReplicatorConfigValue.NOTIFY_SUBSCRIBERS_INTERVAL.getConfigPath())
                .isEqualTo(Duration.ofSeconds(1));
    }

    @Test
    public void underTestReturnsValuesOfConfigFileWithOverwrites() {
        final String name = "nomen-est-omen";
        final String role = "da-role";

        final DefaultPekkoReplicatorConfig underTest = DefaultPekkoReplicatorConfig.of(testConfig, name, role);

        softly.assertThat(underTest.getName())
                .as(PekkoReplicatorConfig.PekkoReplicatorConfigValue.NAME.getConfigPath())
                .isEqualTo(name);
        softly.assertThat(underTest.getRole())
                .as(PekkoReplicatorConfig.PekkoReplicatorConfigValue.ROLE.getConfigPath())
                .isEqualTo(role);

        // test that pekko default config values are copied
        final Config completeConfig = underTest.getCompleteConfig();
        assertThat(completeConfig.getDuration("gossip-interval")).isEqualTo(Duration.ofMillis(1337L));
        assertThat(completeConfig.getBoolean("delta-crdt.enabled")).isTrue();
    }

}
