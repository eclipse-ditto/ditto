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

import org.assertj.core.api.JUnitSoftAssertions;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link org.eclipse.ditto.services.connectivity.messaging.config.DefaultReconnectConfig}.
 */
public final class DefaultReconnectConfigTest {

    private static Config reconnectionTestConf;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

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

        softly.assertThat(underTestDeserialized).isEqualTo(underTest);
    }

    @Test
    public void underTestReturnsDefaultValuesIfBaseConfigWasEmpty() {
        final DefaultReconnectConfig underTest = DefaultReconnectConfig.of(ConfigFactory.empty());

        softly.assertThat(underTest.getInitialDelay())
                .as(ReconnectConfig.ReconnectConfigValue.INITIAL_DELAY.getConfigPath())
                .isEqualTo(ReconnectConfig.ReconnectConfigValue.INITIAL_DELAY.getDefaultValue());

        softly.assertThat(underTest.getInterval())
                .as(ReconnectConfig.ReconnectConfigValue.INTERVAL.getConfigPath())
                .isEqualTo(ReconnectConfig.ReconnectConfigValue.INTERVAL.getDefaultValue());

        softly.assertThat(underTest.getRateConfig())
                .as("rateConfig")
                .satisfies(rateConfig -> {
                    softly.assertThat(rateConfig.getEntityAmount())
                            .as(ReconnectConfig.RateConfig.RateConfigValue.ENTITIES.getConfigPath())
                            .isEqualTo(ReconnectConfig.RateConfig.RateConfigValue.ENTITIES.getDefaultValue());
                    softly.assertThat(rateConfig.getFrequency())
                            .as(ReconnectConfig.RateConfig.RateConfigValue.FREQUENCY.getConfigPath())
                            .isEqualTo(ReconnectConfig.RateConfig.RateConfigValue.FREQUENCY.getDefaultValue());
                });
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final DefaultReconnectConfig underTest = DefaultReconnectConfig.of(reconnectionTestConf);

        softly.assertThat(underTest.getInitialDelay())
                .as(ReconnectConfig.ReconnectConfigValue.INITIAL_DELAY.getConfigPath())
                .isEqualTo(Duration.ofSeconds(1L));

        softly.assertThat(underTest.getInterval())
                .as(ReconnectConfig.ReconnectConfigValue.INTERVAL.getConfigPath())
                .isEqualTo(Duration.ofMinutes(5L));

        softly.assertThat(underTest.getRateConfig())
                .as("rateConfig")
                .satisfies(rateConfig -> {
                    softly.assertThat(rateConfig.getEntityAmount())
                            .as(ReconnectConfig.RateConfig.RateConfigValue.ENTITIES.getConfigPath())
                            .isEqualTo(2);
                    softly.assertThat(rateConfig.getFrequency())
                            .as(ReconnectConfig.RateConfig.RateConfigValue.FREQUENCY.getConfigPath())
                            .isEqualTo(Duration.ofSeconds(2L));
                });
    }
}
