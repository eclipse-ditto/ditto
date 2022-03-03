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

import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Duration;
import java.util.Map;

import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.base.service.config.ThrottlingConfig;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link org.eclipse.ditto.connectivity.service.config.DefaultAmqp10Config}.
 */
public final class DefaultAmqp10ConfigTest {

    private static Config amqp10TestConf;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void initTestFixture() {
        amqp10TestConf = ConfigFactory.load("amqp10-test");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultAmqp10Config.class,
                areImmutable(),
                provided(BackOffConfig.class, ThrottlingConfig.class, Amqp10ConsumerConfig.class).isAlsoImmutable(),
                assumingFields("hmacAlgorithms").areSafelyCopiedUnmodifiableCollectionsWithImmutableElements());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultAmqp10Config.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void underTestReturnsDefaultValuesIfBaseConfigWasEmpty() {
        final DefaultAmqp10Config underTest = DefaultAmqp10Config.of(ConfigFactory.empty());

        softly.assertThat(underTest.getConsumerConfig().getRedeliveryExpectationTimeout())
                .as(Amqp10ConsumerConfig.ConfigValue.REDELIVERY_EXPECTATION_TIMEOUT.getConfigPath())
                .isEqualTo(Amqp10ConsumerConfig.ConfigValue.REDELIVERY_EXPECTATION_TIMEOUT.getDefaultValue());
        softly.assertThat(underTest.getProducerCacheSize())
                .as(Amqp10Config.Amqp10ConfigValue.PRODUCER_CACHE_SIZE.getConfigPath())
                .isEqualTo(Amqp10Config.Amqp10ConfigValue.PRODUCER_CACHE_SIZE.getDefaultValue());
        softly.assertThat(underTest.getPublisherConfig().getMaxQueueSize())
                .as(Amqp10PublisherConfig.ConfigValue.MAX_QUEUE_SIZE.getConfigPath())
                .isEqualTo(Amqp10PublisherConfig.ConfigValue.MAX_QUEUE_SIZE.getDefaultValue());
        softly.assertThat(underTest.getPublisherConfig().getParallelism())
                .as(Amqp10PublisherConfig.ConfigValue.PARALLELISM.getConfigPath())
                .isEqualTo(Amqp10PublisherConfig.ConfigValue.PARALLELISM.getDefaultValue());
        softly.assertThat(underTest.getGlobalConnectTimeout())
                .as(Amqp10Config.Amqp10ConfigValue.GLOBAL_CONNECT_TIMEOUT.getConfigPath())
                .isEqualTo(Amqp10Config.Amqp10ConfigValue.GLOBAL_CONNECT_TIMEOUT.getDefaultValue());
        softly.assertThat(underTest.getGlobalSendTimeout())
                .as(Amqp10Config.Amqp10ConfigValue.GLOBAL_SEND_TIMEOUT.getConfigPath())
                .isEqualTo(Amqp10Config.Amqp10ConfigValue.GLOBAL_SEND_TIMEOUT.getDefaultValue());
        softly.assertThat(underTest.getGlobalRequestTimeout())
                .as(Amqp10Config.Amqp10ConfigValue.GLOBAL_REQUEST_TIMEOUT.getConfigPath())
                .isEqualTo(Amqp10Config.Amqp10ConfigValue.GLOBAL_REQUEST_TIMEOUT.getDefaultValue());
        softly.assertThat(underTest.getGlobalPrefetchPolicyAllCount())
                .as(Amqp10Config.Amqp10ConfigValue.GLOBAL_PREFETCH_POLICY_ALL_COUNT.getConfigPath())
                .isEqualTo(Amqp10Config.Amqp10ConfigValue.GLOBAL_PREFETCH_POLICY_ALL_COUNT.getDefaultValue());
        softly.assertThat(underTest.getHmacAlgorithms())
                .as(Amqp10Config.Amqp10ConfigValue.HMAC_ALGORITHMS.getConfigPath())
                .isEqualTo(Amqp10Config.Amqp10ConfigValue.HMAC_ALGORITHMS.getDefaultValue());
        softly.assertThat(underTest.getConsumerConfig().getThrottlingConfig().getInterval())
                .as(ThrottlingConfig.ConfigValue.INTERVAL.getConfigPath())
                .isEqualTo(ThrottlingConfig.ConfigValue.INTERVAL.getDefaultValue());
        softly.assertThat(underTest.getConsumerConfig().getThrottlingConfig().getLimit())
                .as(ThrottlingConfig.ConfigValue.LIMIT.getConfigPath())
                .isEqualTo(ThrottlingConfig.ConfigValue.LIMIT.getDefaultValue());
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final DefaultAmqp10Config underTest = DefaultAmqp10Config.of(amqp10TestConf);

        softly.assertThat(underTest.getConsumerConfig().getThrottlingConfig().getMaxInFlightFactor())
                .as(ConnectionThrottlingConfig.ConfigValue.MAX_IN_FLIGHT_FACTOR.getConfigPath())
                .isEqualTo(2.0);
        softly.assertThat(underTest.getConsumerConfig().getRedeliveryExpectationTimeout())
                .as(Amqp10ConsumerConfig.ConfigValue.REDELIVERY_EXPECTATION_TIMEOUT.getConfigPath())
                .isEqualTo(Duration.ofSeconds(1337));
        softly.assertThat(underTest.getConsumerConfig().getThrottlingConfig().getLimit())
                .as(ThrottlingConfig.ConfigValue.LIMIT.getConfigPath())
                .isEqualTo(4711);
        softly.assertThat(underTest.getConsumerConfig().getThrottlingConfig().getInterval())
                .as(ThrottlingConfig.ConfigValue.INTERVAL.getConfigPath())
                .isEqualTo(Duration.ofSeconds(42));
        softly.assertThat(underTest.getProducerCacheSize())
                .as(Amqp10Config.Amqp10ConfigValue.PRODUCER_CACHE_SIZE.getConfigPath())
                .isEqualTo(11);
        softly.assertThat(underTest.getPublisherConfig().getMaxQueueSize())
                .as(Amqp10PublisherConfig.ConfigValue.MAX_QUEUE_SIZE.getConfigPath())
                .isEqualTo(39);
        softly.assertThat(underTest.getPublisherConfig().getParallelism())
                .as(Amqp10PublisherConfig.ConfigValue.PARALLELISM.getConfigPath())
                .isEqualTo(3);
        softly.assertThat(underTest.getGlobalConnectTimeout())
                .as(Amqp10Config.Amqp10ConfigValue.GLOBAL_CONNECT_TIMEOUT.getConfigPath())
                .isEqualTo(Duration.ofSeconds(11));
        softly.assertThat(underTest.getGlobalSendTimeout())
                .as(Amqp10Config.Amqp10ConfigValue.GLOBAL_SEND_TIMEOUT.getConfigPath())
                .isEqualTo(Duration.ofSeconds(22));
        softly.assertThat(underTest.getGlobalRequestTimeout())
                .as(Amqp10Config.Amqp10ConfigValue.GLOBAL_REQUEST_TIMEOUT.getConfigPath())
                .isEqualTo(Duration.ofSeconds(33));
        softly.assertThat(underTest.getGlobalPrefetchPolicyAllCount())
                .as(Amqp10Config.Amqp10ConfigValue.GLOBAL_PREFETCH_POLICY_ALL_COUNT.getConfigPath())
                .isEqualTo(44);
        softly.assertThat(underTest.getHmacAlgorithms())
                .as(Amqp10Config.Amqp10ConfigValue.HMAC_ALGORITHMS.getConfigPath())
                .isEqualTo(Map.of("algorithm1", "factory1", "algorithm2", "factory2"));
    }

}
