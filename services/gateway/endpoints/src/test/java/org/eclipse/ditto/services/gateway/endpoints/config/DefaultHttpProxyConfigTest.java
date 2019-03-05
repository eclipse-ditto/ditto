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
package org.eclipse.ditto.services.gateway.endpoints.config;

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

import org.eclipse.ditto.services.gateway.endpoints.config.AuthenticationConfig.HttpProxyConfig.HttpProxyConfigValue;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link org.eclipse.ditto.services.gateway.endpoints.config.DefaultHttpProxyConfig}.
 */
public final class DefaultHttpProxyConfigTest {

    private static Config httpProxyConfig;

    @BeforeClass
    public static void initTestFixture() {
        httpProxyConfig = ConfigFactory.load("http-proxy-test");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultHttpProxyConfig.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultHttpProxyConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void testSerializationAndDeserialization() throws IOException, ClassNotFoundException {
        final DefaultHttpProxyConfig underTest = DefaultHttpProxyConfig.of(httpProxyConfig);

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

    @Test
    public void underTestReturnsDefaultValuesIfBaseConfigWasEmpty() {
        final DefaultHttpProxyConfig underTest = DefaultHttpProxyConfig.of(ConfigFactory.empty());

        assertThat(underTest.isEnabled())
                .as(HttpProxyConfigValue.ENABLED.getConfigPath())
                .isEqualTo(HttpProxyConfigValue.ENABLED.getDefaultValue());
        assertThat(underTest.getHostname())
                .as(HttpProxyConfigValue.HOST_NAME.getConfigPath())
                .isEqualTo(HttpProxyConfigValue.HOST_NAME.getDefaultValue());
        assertThat(underTest.getPort())
                .as(HttpProxyConfigValue.PORT.getConfigPath())
                .isEqualTo(HttpProxyConfigValue.PORT.getDefaultValue());
        assertThat(underTest.getUsername())
                .as(HttpProxyConfigValue.USER_NAME.getConfigPath())
                .isEqualTo(HttpProxyConfigValue.USER_NAME.getDefaultValue());
        assertThat(underTest.getPassword())
                .as(HttpProxyConfigValue.PASSWORD.getConfigPath())
                .isEqualTo(HttpProxyConfigValue.PASSWORD.getDefaultValue());
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final DefaultHttpProxyConfig underTest = DefaultHttpProxyConfig.of(httpProxyConfig);

        assertThat(underTest.isEnabled())
                .as(HttpProxyConfigValue.ENABLED.getConfigPath())
                .isTrue();
        assertThat(underTest.getHostname())
                .as(HttpProxyConfigValue.HOST_NAME.getConfigPath())
                .isEqualTo("example.com");
        assertThat(underTest.getPort())
                .as(HttpProxyConfigValue.PORT.getConfigPath())
                .isEqualTo(4711);
        assertThat(underTest.getUsername())
                .as(HttpProxyConfigValue.USER_NAME.getConfigPath())
                .isEqualTo("john.frume");
        assertThat(underTest.getPassword())
                .as(HttpProxyConfigValue.PASSWORD.getConfigPath())
                .isEqualTo("verySecretPW!");
    }

}