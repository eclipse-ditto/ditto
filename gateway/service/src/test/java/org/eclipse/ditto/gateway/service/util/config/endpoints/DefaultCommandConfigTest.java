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
package org.eclipse.ditto.gateway.service.util.config.endpoints;

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
 * Unit test for {@link DefaultCommandConfig}.
 */
public final class DefaultCommandConfigTest {

    private static Config commandTestConfig;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void initTestFixture() {
        commandTestConfig = ConfigFactory.load("command-test");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultCommandConfig.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultCommandConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void underTestReturnsDefaultValuesIfBaseConfigWasEmpty() {
        final DefaultCommandConfig underTest = DefaultCommandConfig.of(ConfigFactory.empty());

        softly.assertThat(underTest.getDefaultTimeout())
                .as(CommandConfig.CommandConfigValue.DEFAULT_TIMEOUT.getConfigPath())
                .isEqualTo(Duration.ofSeconds(10L));
        softly.assertThat(underTest.getMaxTimeout())
                .as(CommandConfig.CommandConfigValue.MAX_TIMEOUT.getConfigPath())
                .isEqualTo(Duration.ofSeconds(60L));
        softly.assertThat(underTest.getSmartChannelBuffer())
                .as(CommandConfig.CommandConfigValue.SMART_CHANNEL_BUFFER.getConfigPath())
                .isEqualTo(Duration.ofSeconds(10L));
        softly.assertThat(underTest.connectionsRetrieveLimit())
                .as(CommandConfig.CommandConfigValue.CONNECTIONS_RETRIEVE_LIMIT.getConfigPath())
                .isEqualTo(100);
    }

    @Test
    public void underTestReturnsValuesOfBaseConfig() {
        final DefaultCommandConfig underTest = DefaultCommandConfig.of(commandTestConfig);

        softly.assertThat(underTest.getDefaultTimeout())
                .as(CommandConfig.CommandConfigValue.DEFAULT_TIMEOUT.getConfigPath())
                .isEqualTo(Duration.ofSeconds(33L));
        softly.assertThat(underTest.getMaxTimeout())
                .as(CommandConfig.CommandConfigValue.MAX_TIMEOUT.getConfigPath())
                .isEqualTo(Duration.ofSeconds(55L));
        softly.assertThat(underTest.getSmartChannelBuffer())
                .as(CommandConfig.CommandConfigValue.SMART_CHANNEL_BUFFER.getConfigPath())
                .isEqualTo(Duration.ofSeconds(66L));
        softly.assertThat(underTest.connectionsRetrieveLimit())
                .as(CommandConfig.CommandConfigValue.CONNECTIONS_RETRIEVE_LIMIT.getConfigPath())
                .isEqualTo(77);
    }

}
