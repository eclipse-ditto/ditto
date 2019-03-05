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
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;

import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link org.eclipse.ditto.services.gateway.endpoints.config.DefaultAuthenticationConfig}.
 */
public final class DefaultAuthenticationConfigTest {

    private static Config authenticationTestConf;

    @BeforeClass
    public static void initTestFixture() {
        authenticationTestConf = ConfigFactory.load("authentication-test");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultAuthenticationConfig.class,
                areImmutable(),
                provided(AuthenticationConfig.HttpProxyConfig.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultAuthenticationConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void testSerializationAndDeserialization() throws IOException, ClassNotFoundException {
        final DefaultAuthenticationConfig underTest = DefaultAuthenticationConfig.of(authenticationTestConf);

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
        final DefaultAuthenticationConfig underTest = DefaultAuthenticationConfig.of(ConfigFactory.empty());

        assertThat(underTest.isDummyAuthenticationEnabled())
                .as("isDummyAuthenticationEnabled")
                .isEqualTo(AuthenticationConfig.AuthenticationConfigValue.DUMMY_AUTH_ENABLED.getDefaultValue());
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final DefaultAuthenticationConfig underTest = DefaultAuthenticationConfig.of(authenticationTestConf);

        assertThat(underTest.isDummyAuthenticationEnabled())
                .as("isDummyAuthenticationEnabled")
                .isTrue();
        assertThat(underTest.getHttpProxyConfig())
                .as("httpProxyConfig")
                .satisfies(httpProxyConfig -> {
                    assertThat(httpProxyConfig.isEnabled())
                            .as("isEnabled")
                            .isTrue();
                    assertThat(httpProxyConfig.getHostname())
                            .as("hostName")
                            .isEqualTo("example.com");
                    assertThat(httpProxyConfig.getPort())
                            .as("port")
                            .isEqualTo(4711);
                    assertThat(httpProxyConfig.getUsername())
                            .as("userName")
                            .isEqualTo("john.frume");
                    assertThat(httpProxyConfig.getPassword())
                            .as("password")
                            .isEqualTo("verySecretPW!");
                });
    }

}