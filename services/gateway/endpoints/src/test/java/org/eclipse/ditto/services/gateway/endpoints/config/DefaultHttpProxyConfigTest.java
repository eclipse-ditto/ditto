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
package org.eclipse.ditto.services.gateway.endpoints.config;

import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;

import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.services.gateway.endpoints.config.HttpProxyConfig.HttpProxyConfigValue;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link org.eclipse.ditto.services.gateway.endpoints.config.DefaultHttpProxyConfig}.
 */
public final class DefaultHttpProxyConfigTest {

    private static Config httpProxyConfig;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

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

        softly.assertThat(underTestDeserialized).isEqualTo(underTest);
    }

    @Test
    public void underTestReturnsDefaultValuesIfBaseConfigWasEmpty() {
        final DefaultHttpProxyConfig underTest = DefaultHttpProxyConfig.of(ConfigFactory.empty());

        softly.assertThat(underTest.isEnabled())
                .as(HttpProxyConfigValue.ENABLED.getConfigPath())
                .isEqualTo(HttpProxyConfigValue.ENABLED.getDefaultValue());
        softly.assertThat(underTest.getHostname())
                .as(HttpProxyConfigValue.HOST_NAME.getConfigPath())
                .isEqualTo(HttpProxyConfigValue.HOST_NAME.getDefaultValue());
        softly.assertThat(underTest.getPort())
                .as(HttpProxyConfigValue.PORT.getConfigPath())
                .isEqualTo(HttpProxyConfigValue.PORT.getDefaultValue());
        softly.assertThat(underTest.getUsername())
                .as(HttpProxyConfigValue.USER_NAME.getConfigPath())
                .isEqualTo(HttpProxyConfigValue.USER_NAME.getDefaultValue());
        softly.assertThat(underTest.getPassword())
                .as(HttpProxyConfigValue.PASSWORD.getConfigPath())
                .isEqualTo(HttpProxyConfigValue.PASSWORD.getDefaultValue());
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final DefaultHttpProxyConfig underTest = DefaultHttpProxyConfig.of(httpProxyConfig);

        softly.assertThat(underTest.isEnabled())
                .as(HttpProxyConfigValue.ENABLED.getConfigPath())
                .isTrue();
        softly.assertThat(underTest.getHostname())
                .as(HttpProxyConfigValue.HOST_NAME.getConfigPath())
                .isEqualTo("example.com");
        softly.assertThat(underTest.getPort())
                .as(HttpProxyConfigValue.PORT.getConfigPath())
                .isEqualTo(4711);
        softly.assertThat(underTest.getUsername())
                .as(HttpProxyConfigValue.USER_NAME.getConfigPath())
                .isEqualTo("john.frume");
        softly.assertThat(underTest.getPassword())
                .as(HttpProxyConfigValue.PASSWORD.getConfigPath())
                .isEqualTo("verySecretPW!");
    }

}