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
package org.eclipse.ditto.services.utils.persistence.mongo.config;

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
 * Unit test for {@link org.eclipse.ditto.services.utils.persistence.mongo.config.DefaultCircuitBreakerConfig}.
 */
public final class DefaultCircuitBreakerConfigTest {

    private static Config circuitBreakerTestConfig;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void initTestFixture() {
        circuitBreakerTestConfig = ConfigFactory.load("circuit-breaker-test");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultCircuitBreakerConfig.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultCircuitBreakerConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void testSerializationAndDeserialization() throws IOException, ClassNotFoundException {
        final DefaultCircuitBreakerConfig underTest = DefaultCircuitBreakerConfig.of(circuitBreakerTestConfig);

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
    public void getMaxFailuresReturnsDefaultValueIfConfigIsEmpty() {
        final DefaultCircuitBreakerConfig underTest = DefaultCircuitBreakerConfig.of(ConfigFactory.empty());

        softly.assertThat(underTest.getMaxFailures()).isEqualTo(5);
    }

    @Test
    public void getMaxFailuresReturnsValueOfConfigFile() {
        final DefaultCircuitBreakerConfig underTest = DefaultCircuitBreakerConfig.of(circuitBreakerTestConfig);

        softly.assertThat(underTest.getMaxFailures()).isEqualTo(23);
    }

    @Test
    public void toStringContainsExpected() {
        final DefaultCircuitBreakerConfig underTest = DefaultCircuitBreakerConfig.of(circuitBreakerTestConfig);

        softly.assertThat(underTest.toString()).contains(underTest.getClass().getSimpleName())
            .contains("maxFailures").contains("5")
            .contains("timeoutConfig");
    }

}