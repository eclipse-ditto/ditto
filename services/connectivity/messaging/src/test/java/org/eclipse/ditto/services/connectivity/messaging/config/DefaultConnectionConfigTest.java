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

import org.eclipse.ditto.services.base.config.supervision.SupervisorConfig;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link org.eclipse.ditto.services.connectivity.messaging.config.DefaultConnectionConfig}.
 */
public final class DefaultConnectionConfigTest {

    private static Config connectionTestConf;

    @BeforeClass
    public static void initTestFixture() {
        connectionTestConf = ConfigFactory.load("connection-test");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultConnectionConfig.class,
                areImmutable(),
                provided(SupervisorConfig.class).isAlsoImmutable(),
                provided(ConnectionConfig.SnapshotConfig.class).isAlsoImmutable(),
                provided(ConnectionConfig.MqttConfig.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultConnectionConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void testSerializationAndDeserialization() throws IOException, ClassNotFoundException {
        final DefaultConnectionConfig underTest = DefaultConnectionConfig.of(connectionTestConf);

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
    public void underTestReturnsValuesOfConfigFile() {
        final DefaultConnectionConfig underTest = DefaultConnectionConfig.of(connectionTestConf);


        assertThat(underTest.getClientActorAskTimeout())
                .as("getClientActorAskTimeout")
                .isEqualTo(Duration.ofSeconds(10L));

        assertThat(underTest.getFlushPendingResponsesTimeout())
                .as("getFlushPendingResponsesTimeout")
                .isEqualTo(Duration.ofSeconds(2L));

        assertThat(underTest.getSupervisorConfig())
                .as("supervisorConfig")
                .satisfies(supervisorConfig -> assertThat(supervisorConfig.getExponentialBackOffConfig())
                        .as("getExponentialBackOffConfig")
                        .satisfies(exponentialBackOffConfig -> {
                            assertThat(exponentialBackOffConfig.getMin())
                                    .as("getMin")
                                    .isEqualTo(Duration.ofSeconds(2L));
                            assertThat(exponentialBackOffConfig.getMax())
                                    .as("getMax")
                                    .isEqualTo(Duration.ofSeconds(50L));
                            assertThat(exponentialBackOffConfig.getRandomFactor())
                                    .as("getRandomFactor")
                                    .isEqualTo(0.1D);
                        }));

        assertThat(underTest.getSnapshotConfig())
                .as("snapshotConfig")
                .satisfies(snapshotConfig -> assertThat(snapshotConfig.getThreshold())
                        .as("getThreshold")
                        .isEqualTo(20)
                );

        assertThat(underTest.getMqttConfig())
                .as("mqttConfig")
                .satisfies(mqttConfig -> assertThat(mqttConfig.getSourceBufferSize())
                        .as("getSourceBufferSize")
                        .isEqualTo(7)
                );
    }
}
