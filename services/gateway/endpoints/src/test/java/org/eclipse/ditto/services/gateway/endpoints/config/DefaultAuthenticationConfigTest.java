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

import org.assertj.core.api.JUnitSoftAssertions;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link org.eclipse.ditto.services.gateway.endpoints.config.DefaultAuthenticationConfig}.
 */
public final class DefaultAuthenticationConfigTest {

    private static Config authenticationTestConf;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void initTestFixture() {
        authenticationTestConf = ConfigFactory.load("authentication-test");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultAuthenticationConfig.class,
                areImmutable(),
                provided(HttpProxyConfig.class).isAlsoImmutable());
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

        softly.assertThat(underTestDeserialized).isEqualTo(underTest);
    }

    @Test
    public void underTestReturnsDefaultValuesIfBaseConfigWasEmpty() {
        final DefaultAuthenticationConfig underTest = DefaultAuthenticationConfig.of(ConfigFactory.empty());

        softly.assertThat(underTest.isDummyAuthenticationEnabled())
                .as(AuthenticationConfig.AuthenticationConfigValue.DUMMY_AUTH_ENABLED.getConfigPath())
                .isEqualTo(AuthenticationConfig.AuthenticationConfigValue.DUMMY_AUTH_ENABLED.getDefaultValue());
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final DefaultAuthenticationConfig underTest = DefaultAuthenticationConfig.of(authenticationTestConf);

        softly.assertThat(underTest.isDummyAuthenticationEnabled())
                .as(AuthenticationConfig.AuthenticationConfigValue.DUMMY_AUTH_ENABLED.getConfigPath())
                .isTrue();
        softly.assertThat(underTest.getHttpProxyConfig())
                .as("httpProxyConfig")
                .satisfies(httpProxyConfig -> {
                    softly.assertThat(httpProxyConfig.isEnabled())
                            .as(HttpProxyConfig.HttpProxyConfigValue.ENABLED.getConfigPath())
                            .isTrue();
                    softly.assertThat(httpProxyConfig.getHostname())
                            .as(HttpProxyConfig.HttpProxyConfigValue.HOST_NAME.getConfigPath())
                            .isEqualTo("example.com");
                    softly.assertThat(httpProxyConfig.getPort())
                            .as(HttpProxyConfig.HttpProxyConfigValue.PORT.getConfigPath())
                            .isEqualTo(4711);
                    softly.assertThat(httpProxyConfig.getUsername())
                            .as(HttpProxyConfig.HttpProxyConfigValue.USER_NAME.getConfigPath())
                            .isEqualTo("john.frume");
                    softly.assertThat(httpProxyConfig.getPassword())
                            .as(HttpProxyConfig.HttpProxyConfigValue.USER_NAME.getConfigPath())
                            .isEqualTo("verySecretPW!");
                });
    }

}