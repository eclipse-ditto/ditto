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
 * Unit test for {@link DefaultAkkaReplicatorConfig}.
 */
public final class DefaultAkkaReplicatorConfigTest {

    private static Config testConfig;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void initTestFixture() {
        testConfig = ConfigFactory.load("akka-replicator-ddata-test");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultAkkaReplicatorConfig.class, areImmutable(),
                AllowedReason.provided(Config.class).areAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultAkkaReplicatorConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void underTestReturnsDefaultValuesIfBaseConfigWasEmpty() {
        final DefaultAkkaReplicatorConfig underTest = DefaultAkkaReplicatorConfig.of(ConfigFactory.empty());

        softly.assertThat(underTest.getName())
                .as(AkkaReplicatorConfig.AkkaReplicatorConfigValue.NAME.getConfigPath())
                .isEqualTo(AkkaReplicatorConfig.AkkaReplicatorConfigValue.NAME.getDefaultValue());
        softly.assertThat(underTest.getRole())
                .as(AkkaReplicatorConfig.AkkaReplicatorConfigValue.ROLE.getConfigPath())
                .isEqualTo(AkkaReplicatorConfig.AkkaReplicatorConfigValue.ROLE.getDefaultValue());
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final DefaultAkkaReplicatorConfig underTest = DefaultAkkaReplicatorConfig.of(testConfig);

        softly.assertThat(underTest.getName())
                .as(AkkaReplicatorConfig.AkkaReplicatorConfigValue.NAME.getConfigPath())
                .isEqualTo("the-name");
        softly.assertThat(underTest.getRole())
                .as(AkkaReplicatorConfig.AkkaReplicatorConfigValue.ROLE.getConfigPath())
                .isEqualTo("a-role");
        softly.assertThat(underTest.getGossipInterval())
                .as(AkkaReplicatorConfig.AkkaReplicatorConfigValue.GOSSIP_INTERVAL.getConfigPath())
                .isEqualTo(Duration.ofMillis(1337));
        softly.assertThat(underTest.getNotifySubscribersInterval())
                .as(AkkaReplicatorConfig.AkkaReplicatorConfigValue.NOTIFY_SUBSCRIBERS_INTERVAL.getConfigPath())
                .isEqualTo(Duration.ofSeconds(1));
    }

    @Test
    public void underTestReturnsValuesOfConfigFileWithOverwrites() {
        final String name = "nomen-est-omen";
        final String role = "da-role";

        final DefaultAkkaReplicatorConfig underTest = DefaultAkkaReplicatorConfig.of(testConfig, name, role);

        softly.assertThat(underTest.getName())
                .as(AkkaReplicatorConfig.AkkaReplicatorConfigValue.NAME.getConfigPath())
                .isEqualTo(name);
        softly.assertThat(underTest.getRole())
                .as(AkkaReplicatorConfig.AkkaReplicatorConfigValue.ROLE.getConfigPath())
                .isEqualTo(role);

        // test that akka default config values are copied
        final Config completeConfig = underTest.getCompleteConfig();
        assertThat(completeConfig.getDuration("gossip-interval")).isEqualTo(Duration.ofMillis(1337L));
        assertThat(completeConfig.getBoolean("delta-crdt.enabled")).isTrue();
    }

}
