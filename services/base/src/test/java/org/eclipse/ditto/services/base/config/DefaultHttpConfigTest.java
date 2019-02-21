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
package org.eclipse.ditto.services.base.config;

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

import org.eclipse.ditto.services.base.config.ServiceSpecificConfig.HttpConfig.HttpConfigValue;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link org.eclipse.ditto.services.base.config.DefaultHttpConfig}.
 */
public final class DefaultHttpConfigTest {

    private static Config httpTestConfig;

    @BeforeClass
    public static void initTestFixture() {
        httpTestConfig = ConfigFactory.load("http-test");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultHttpConfig.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultHttpConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void testSerializationAndDeserialization() throws IOException, ClassNotFoundException {
        final DefaultHttpConfig underTest = DefaultHttpConfig.of(httpTestConfig);

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
    public void getHostnameReturnsDefaultValueIfConfigIsEmpty() {
        final DefaultHttpConfig underTest = DefaultHttpConfig.of(ConfigFactory.empty());

        assertThat(underTest.getHostname()).isEqualTo(HttpConfigValue.HOSTNAME.getDefaultValue());
    }

    @Test
    public void getHostnameReturnsValueOfConfigFile() {
        final DefaultHttpConfig underTest = DefaultHttpConfig.of(httpTestConfig);

        assertThat(underTest.getHostname()).isEqualTo("example.com");
    }

    @Test
    public void getPortReturnsDefaultValueIfConfigIsEmpty() {
        final DefaultHttpConfig underTest = DefaultHttpConfig.of(ConfigFactory.empty());

        assertThat(underTest.getPort()).isEqualTo(HttpConfigValue.PORT.getDefaultValue());
    }

    @Test
    public void getPortReturnsValueOfConfigFile() {
        final DefaultHttpConfig underTest = DefaultHttpConfig.of(httpTestConfig);

        assertThat(underTest.getPort()).isEqualTo(4711);
    }

    @Test
    public void toStringContainsExpected() {
        final DefaultHttpConfig underTest = DefaultHttpConfig.of(httpTestConfig);

        assertThat(underTest.toString()).contains(underTest.getClass().getSimpleName())
                .contains("hostname").contains("example.com")
                .contains("port").contains("4711");
    }

}