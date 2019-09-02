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
package org.eclipse.ditto.services.connectivity.messaging.config;

import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Duration;

import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.services.base.config.supervision.DefaultSupervisorConfig;
import org.eclipse.ditto.services.base.config.supervision.ExponentialBackOffConfig;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link org.eclipse.ditto.services.connectivity.messaging.config.DefaultConnectionConfig}.
 */
public final class DefaultConnectionConfigTest {

    private static Config connectionTestConf;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void initTestFixture() {
        connectionTestConf = ConfigFactory.load("connection-test");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultConnectionConfig.class,
                areImmutable(),
                provided(DefaultSupervisorConfig.class).isAlsoImmutable(),
                provided(SnapshotConfig.class).isAlsoImmutable(),
                provided(MqttConfig.class).isAlsoImmutable(),
                provided(DefaultKafkaConfig.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultConnectionConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final DefaultConnectionConfig underTest = DefaultConnectionConfig.of(connectionTestConf);

        softly.assertThat(underTest.getClientActorAskTimeout())
                .as(ConnectionConfig.ConnectionConfigValue.CLIENT_ACTOR_ASK_TIMEOUT.getConfigPath())
                .isEqualTo(Duration.ofSeconds(10L));

        softly.assertThat(underTest.getSupervisorConfig())
                .as("supervisorConfig")
                .satisfies(supervisorConfig -> softly.assertThat(supervisorConfig.getExponentialBackOffConfig())
                        .as("exponentialBackOffConfig")
                        .satisfies(exponentialBackOffConfig -> {
                            softly.assertThat(exponentialBackOffConfig.getMin())
                                    .as(ExponentialBackOffConfig.ExponentialBackOffConfigValue.MIN.getConfigPath())
                                    .isEqualTo(Duration.ofSeconds(2L));
                            softly.assertThat(exponentialBackOffConfig.getMax())
                                    .as(ExponentialBackOffConfig.ExponentialBackOffConfigValue.MAX.getConfigPath())
                                    .isEqualTo(Duration.ofSeconds(50L));
                            softly.assertThat(exponentialBackOffConfig.getRandomFactor())
                                    .as(ExponentialBackOffConfig.ExponentialBackOffConfigValue.RANDOM_FACTOR.getConfigPath())
                                    .isEqualTo(0.1D);
                        }));

        softly.assertThat(underTest.getSnapshotConfig())
                .as("snapshotConfig")
                .satisfies(snapshotConfig -> softly.assertThat(snapshotConfig.getThreshold())
                        .as(SnapshotConfig.SnapshotConfigValue.THRESHOLD.getConfigPath())
                        .isEqualTo(20));

        softly.assertThat(underTest.getMqttConfig())
                .as("mqttConfig")
                .satisfies(mqttConfig -> softly.assertThat(mqttConfig.getSourceBufferSize())
                        .as(MqttConfig.MqttConfigValue.SOURCE_BUFFER_SIZE.getConfigPath())
                        .isEqualTo(7));
    }

}
