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

import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.time.Duration;

import org.assertj.core.api.JUnitSoftAssertions;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link org.eclipse.ditto.services.gateway.endpoints.config.DefaultMessageConfig}.
 */
public final class DefaultMessageConfigTest {

    private static Config messageTestConfig;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void initTestFixture() {
        messageTestConfig = ConfigFactory.load("message-test");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultMessageConfig.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultMessageConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void testSerializationAndDeserialization() throws IOException, ClassNotFoundException {
        final DefaultMessageConfig underTest = DefaultMessageConfig.of(messageTestConfig);

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
        final DefaultMessageConfig underTest = DefaultMessageConfig.of(ConfigFactory.empty());

        softly.assertThat(underTest.getDefaultTimeout())
                .as(MessageConfig.MessageConfigValue.DEFAULT_TIMEOUT.getConfigPath())
                .isEqualTo(Duration.ofSeconds(35L));
        softly.assertThat(underTest.getMaxTimeout())
                .as(MessageConfig.MessageConfigValue.MAX_TIMEOUT.getConfigPath())
                .isEqualTo(Duration.ofSeconds(330L));
    }

    @Test
    public void underTestReturnsValuesOfBaseConfig() {
        final DefaultMessageConfig underTest = DefaultMessageConfig.of(messageTestConfig);

        softly.assertThat(underTest.getDefaultTimeout())
                .as(MessageConfig.MessageConfigValue.DEFAULT_TIMEOUT.getConfigPath())
                .isEqualTo(Duration.ofSeconds(23L));
        softly.assertThat(underTest.getMaxTimeout())
                .as(MessageConfig.MessageConfigValue.MAX_TIMEOUT.getConfigPath())
                .isEqualTo(Duration.ofSeconds(42L));
    }

}