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
package org.eclipse.ditto.services.connectivity.messaging.config;

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
import java.time.Duration;

import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link org.eclipse.ditto.services.connectivity.messaging.config.DefaultReconnectConfig}.
 */
public final class DefaultReconnectConfigTest {

    private static Config reconnectionTestConf;

    @BeforeClass
    public static void initTestFixture() {
        reconnectionTestConf = ConfigFactory.load("reconnection-test");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultReconnectConfig.class,
                areImmutable(),
                provided(ReconnectConfig.RateConfig.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultReconnectConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void testSerializationAndDeserialization() throws IOException, ClassNotFoundException {
        final DefaultReconnectConfig underTest = DefaultReconnectConfig.of(reconnectionTestConf);

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
        final DefaultReconnectConfig underTest = DefaultReconnectConfig.of(ConfigFactory.empty());

        assertThat(underTest.getInitialDelay())
                .as("getInitialDelay")
                .isEqualTo(ReconnectConfig.ReconnectConfigValue.INITIAL_DELAY.getDefaultValue());

        assertThat(underTest.getInterval())
                .as("getInterval")
                .isEqualTo(ReconnectConfig.ReconnectConfigValue.INTERVAL.getDefaultValue());

        assertThat(underTest.getRateConfig())
                .as("rateConfig")
                .satisfies(rateConfig -> {
                    assertThat(rateConfig.getEntityAmount())
                            .as("getEntityAmount")
                            .isEqualTo(ReconnectConfig.RateConfig.RateConfigValue.ENTITIES.getDefaultValue());
                    assertThat(rateConfig.getFrequency())
                            .as("getFrequency")
                            .isEqualTo(ReconnectConfig.RateConfig.RateConfigValue.FREQUENCY.getDefaultValue());
                });
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final DefaultReconnectConfig underTest = DefaultReconnectConfig.of(reconnectionTestConf);

        assertThat(underTest.getInitialDelay())
                .as("getInitialDelay")
                .isEqualTo(Duration.ofSeconds(1L));

        assertThat(underTest.getInterval())
                .as("getInterval")
                .isEqualTo(Duration.ofMinutes(5L));

        assertThat(underTest.getRateConfig())
                .as("rateConfig")
                .satisfies(rateConfig -> {
                    assertThat(rateConfig.getEntityAmount())
                            .as("getEntityAmount")
                            .isEqualTo(2);
                    assertThat(rateConfig.getFrequency())
                            .as("getFrequency")
                            .isEqualTo(Duration.ofSeconds(2L));
                });
    }
}
