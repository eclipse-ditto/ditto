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
package org.eclipse.ditto.internal.utils.protocol.config;

import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.assertj.core.api.JUnitSoftAssertions;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link DefaultProtocolConfig}.
 */
public final class DefaultProtocolConfigTest {

    private static Config protocolTestConfig;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void initTestFixture() {
        protocolTestConfig = ConfigFactory.load("protocol-test");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultProtocolConfig.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultProtocolConfig.class)
                .usingGetClass()
                .verify();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void underTestReturnsDefaultValuesIfBaseConfigWasEmpty() {
        final DefaultProtocolConfig underTest = DefaultProtocolConfig.of(ConfigFactory.empty());

        softly.assertThat(underTest.getProviderClassName())
                .as(ProtocolConfig.ProtocolConfigValue.PROVIDER.getConfigPath())
                .isEqualTo(ProtocolConfig.ProtocolConfigValue.PROVIDER.getDefaultValue());
        softly.assertThat(underTest.getBlockedHeaderKeys())
                .as(ProtocolConfig.ProtocolConfigValue.BLOCKLIST.getConfigPath())
                .containsOnlyElementsOf((Iterable) ProtocolConfig.ProtocolConfigValue.BLOCKLIST.getDefaultValue());
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final DefaultProtocolConfig underTest = DefaultProtocolConfig.of(protocolTestConfig);

        softly.assertThat(underTest.getProviderClassName())
                .as(ProtocolConfig.ProtocolConfigValue.PROVIDER.getConfigPath())
                .isEqualTo("org.example.ditto.MyProtocolAdapterProvider");
        softly.assertThat(underTest.getBlockedHeaderKeys())
                .as(ProtocolConfig.ProtocolConfigValue.BLOCKLIST.getConfigPath())
                .containsOnly("foo", "bar", "baz");
    }

}
