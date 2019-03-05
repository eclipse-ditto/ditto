/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.utils.protocol.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;

import org.eclipse.ditto.services.utils.protocol.config.ProtocolConfig.ProtocolConfigValue;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link org.eclipse.ditto.services.utils.protocol.config.DefaultProtocolConfig}.
 */
public final class DefaultProtocolConfigTest {

    private static Config protocolTestConfig;

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

    @Test
    public void testSerializationAndDeserialization() throws IOException, ClassNotFoundException {
        final DefaultProtocolConfig underTest = DefaultProtocolConfig.of(protocolTestConfig);

        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        final ObjectOutput objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
        objectOutputStream.writeObject(underTest);
        objectOutputStream.close();

        final byte[] underTestSerialized = byteArrayOutputStream.toByteArray();

        final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(underTestSerialized);
        final ObjectInput objectInputStream = new ObjectInputStream(byteArrayInputStream);
        final Object underTestDeserialized = objectInputStream.readObject();

        assertThat(underTestDeserialized).isEqualTo(underTest);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void underTestReturnsDefaultValuesIfBaseConfigWasEmpty() {
        final DefaultProtocolConfig underTest = DefaultProtocolConfig.of(ConfigFactory.empty());

        assertThat(underTest.getProviderClassName())
                .as(ProtocolConfigValue.PROVIDER.getConfigPath())
                .isEqualTo(ProtocolConfigValue.PROVIDER.getDefaultValue());
        assertThat(underTest.getBlacklistedHeaderKeys())
                .as(ProtocolConfigValue.BLACKLIST.getConfigPath())
                .containsOnlyElementsOf((Iterable) ProtocolConfigValue.BLACKLIST.getDefaultValue());
        assertThat(underTest.getIncompatibleBlacklist())
                .as(ProtocolConfigValue.INCOMPATIBLE_BLACKLIST.getConfigPath())
                .containsOnlyElementsOf((Iterable) ProtocolConfigValue.INCOMPATIBLE_BLACKLIST.getDefaultValue());
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final DefaultProtocolConfig underTest = DefaultProtocolConfig.of(protocolTestConfig);

        assertThat(underTest.getProviderClassName())
                .as(ProtocolConfigValue.PROVIDER.getConfigPath())
                .isEqualTo("org.example.ditto.MyProtocolAdapterProvider");
        assertThat(underTest.getBlacklistedHeaderKeys())
                .as(ProtocolConfigValue.BLACKLIST.getConfigPath())
                .containsOnly("foo", "bar", "baz");
        assertThat(underTest.getIncompatibleBlacklist())
                .as(ProtocolConfigValue.INCOMPATIBLE_BLACKLIST.getConfigPath())
                .containsOnly("tick", "trick", "track");
    }

}