/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Duration;

import org.eclipse.ditto.base.service.config.ThrottlingConfig;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Tests DefaultKafkaConfig.
 */
public class DefaultKafkaConfigTest {

    private static final Duration DEFAULT_POLL_INTERVAL = Duration.ofMillis(50);
    private static final Duration DEFAULT_POLL_TIMEOUT = Duration.ofMillis(100);
    private static final Duration DEFAULT_CLOSE_TIMEOUT = Duration.ofSeconds(10);
    private static final int DEFAULT_PARALLELISM = 10000;
    private static Config kafkaTestConf;

    @BeforeClass
    public static void initTestFixture() {
        kafkaTestConf = ConfigFactory.load("kafka-test");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultKafkaConfig.class,
                areImmutable(),
                provided(Config.class, ThrottlingConfig.class).areAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultKafkaConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final DefaultKafkaConfig underTest = DefaultKafkaConfig.of(kafkaTestConf);
        assertThat(underTest.getConsumerThrottlingConfig().getLimit()).isEqualTo(100);
        assertThat(underTest.getConsumerThrottlingConfig().getInterval()).isEqualTo(Duration.ofSeconds(1));

        assertThat(underTest.getConsumerConfig().getDuration("poll-interval")) // from akka.kafka.consumer
                .isEqualTo(DEFAULT_POLL_INTERVAL);
        assertThat(underTest.getConsumerConfig().getDuration("poll-timeout")) // from kafka-test.conf
                .isEqualTo(DEFAULT_POLL_TIMEOUT);

        assertThat(underTest.getProducerConfig().getInt("parallelism")) // from akka.kafka.producer
                .isEqualTo(DEFAULT_PARALLELISM);
        assertThat(underTest.getProducerConfig().getDuration("close-timeout")) // from kafka-test.conf
                .isEqualTo(DEFAULT_CLOSE_TIMEOUT);
    }
}