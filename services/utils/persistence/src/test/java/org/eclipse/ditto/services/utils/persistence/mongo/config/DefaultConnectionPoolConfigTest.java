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
import java.time.Duration;

import org.eclipse.ditto.services.utils.persistence.mongo.config.MongoDbConfig.ConnectionPoolConfig.ConnectionPoolConfigValue;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link org.eclipse.ditto.services.utils.persistence.mongo.config.DefaultConnectionPoolConfig}.
 */
public final class DefaultConnectionPoolConfigTest {

    private static Config connectionPoolTestConfig;

    @BeforeClass
    public static void initTestFixture() {
        connectionPoolTestConfig = ConfigFactory.load("pool-test");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultConnectionPoolConfig.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultConnectionPoolConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void testSerializationAndDeserialization() throws IOException, ClassNotFoundException {
        final DefaultConnectionPoolConfig underTest = DefaultConnectionPoolConfig.of(connectionPoolTestConfig);

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
    public void underTestReturnsDefaultValuesWhenBaseConfigWasEmpty() {
        final DefaultConnectionPoolConfig underTest = DefaultConnectionPoolConfig.of(ConfigFactory.empty());

        assertThat(underTest.getMaxSize())
                .as(ConnectionPoolConfigValue.MAX_SIZE.getConfigPath())
                .isEqualTo(ConnectionPoolConfigValue.MAX_SIZE.getDefaultValue());
        assertThat(underTest.getMaxWaitQueueSize())
                .as(ConnectionPoolConfigValue.MAX_WAIT_QUEUE_SIZE.getConfigPath())
                .isEqualTo(ConnectionPoolConfigValue.MAX_WAIT_QUEUE_SIZE.getDefaultValue());
        assertThat(underTest.getMaxWaitTime())
                .as(ConnectionPoolConfigValue.MAX_WAIT_TIME.getConfigPath())
                .isEqualTo(ConnectionPoolConfigValue.MAX_WAIT_TIME.getDefaultValue());
        assertThat(underTest.isJmxListenerEnabled())
                .as(ConnectionPoolConfigValue.JMX_LISTENER_ENABLED.getConfigPath())
                .isEqualTo(ConnectionPoolConfigValue.JMX_LISTENER_ENABLED.getDefaultValue());
    }

    @Test
    public void underTestReturnsValuesOfBaseConfig() {
        final DefaultConnectionPoolConfig underTest = DefaultConnectionPoolConfig.of(connectionPoolTestConfig);

        assertThat(underTest.getMaxSize())
                .as(ConnectionPoolConfigValue.MAX_SIZE.getConfigPath())
                .isEqualTo(1_000);
        assertThat(underTest.getMaxWaitQueueSize())
                .as(ConnectionPoolConfigValue.MAX_WAIT_QUEUE_SIZE.getConfigPath())
                .isEqualTo(1_000);
        assertThat(underTest.getMaxWaitTime())
                .as(ConnectionPoolConfigValue.MAX_WAIT_TIME.getConfigPath())
                .isEqualTo(Duration.ofSeconds(42L));
        assertThat(underTest.isJmxListenerEnabled())
                .as(ConnectionPoolConfigValue.JMX_LISTENER_ENABLED.getConfigPath())
                .isTrue();
    }

}