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
package org.eclipse.ditto.internal.utils.persistence.mongo.config;

import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Duration;

import org.assertj.core.api.JUnitSoftAssertions;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link DefaultConnectionPoolConfig}.
 */
public final class DefaultConnectionPoolConfigTest {

    private static Config connectionPoolTestConfig;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

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
    public void underTestReturnsDefaultValuesWhenBaseConfigWasEmpty() {
        final DefaultConnectionPoolConfig underTest = DefaultConnectionPoolConfig.of(ConfigFactory.empty());

        softly.assertThat(underTest.getMinSize())
                .as(MongoDbConfig.ConnectionPoolConfig.ConnectionPoolConfigValue.MIN_SIZE.getConfigPath())
                .isEqualTo(MongoDbConfig.ConnectionPoolConfig.ConnectionPoolConfigValue.MIN_SIZE.getDefaultValue());
        softly.assertThat(underTest.getMaxSize())
                .as(MongoDbConfig.ConnectionPoolConfig.ConnectionPoolConfigValue.MAX_SIZE.getConfigPath())
                .isEqualTo(MongoDbConfig.ConnectionPoolConfig.ConnectionPoolConfigValue.MAX_SIZE.getDefaultValue());
        softly.assertThat(underTest.getMaxIdleTime())
                .as(MongoDbConfig.ConnectionPoolConfig.ConnectionPoolConfigValue.MAX_IDLE_TIME.getConfigPath())
                .isEqualTo(MongoDbConfig.ConnectionPoolConfig.ConnectionPoolConfigValue.MAX_IDLE_TIME.getDefaultValue());
        softly.assertThat(underTest.getMaxWaitTime())
                .as(MongoDbConfig.ConnectionPoolConfig.ConnectionPoolConfigValue.MAX_WAIT_TIME.getConfigPath())
                .isEqualTo(MongoDbConfig.ConnectionPoolConfig.ConnectionPoolConfigValue.MAX_WAIT_TIME.getDefaultValue());
        softly.assertThat(underTest.isJmxListenerEnabled())
                .as(MongoDbConfig.ConnectionPoolConfig.ConnectionPoolConfigValue.JMX_LISTENER_ENABLED.getConfigPath())
                .isEqualTo(MongoDbConfig.ConnectionPoolConfig.ConnectionPoolConfigValue.JMX_LISTENER_ENABLED.getDefaultValue());
    }

    @Test
    public void underTestReturnsValuesOfBaseConfig() {
        final DefaultConnectionPoolConfig underTest = DefaultConnectionPoolConfig.of(connectionPoolTestConfig);

        softly.assertThat(underTest.getMinSize())
                .as(MongoDbConfig.ConnectionPoolConfig.ConnectionPoolConfigValue.MIN_SIZE.getConfigPath())
                .isEqualTo(10);
        softly.assertThat(underTest.getMaxSize())
                .as(MongoDbConfig.ConnectionPoolConfig.ConnectionPoolConfigValue.MAX_SIZE.getConfigPath())
                .isEqualTo(1_000);
        softly.assertThat(underTest.getMaxIdleTime())
                .as(MongoDbConfig.ConnectionPoolConfig.ConnectionPoolConfigValue.MAX_IDLE_TIME.getConfigPath())
                .isEqualTo(Duration.ofMinutes(5L));
        softly.assertThat(underTest.getMaxWaitTime())
                .as(MongoDbConfig.ConnectionPoolConfig.ConnectionPoolConfigValue.MAX_WAIT_TIME.getConfigPath())
                .isEqualTo(Duration.ofSeconds(42L));
        softly.assertThat(underTest.isJmxListenerEnabled())
                .as(MongoDbConfig.ConnectionPoolConfig.ConnectionPoolConfigValue.JMX_LISTENER_ENABLED.getConfigPath())
                .isTrue();
    }

}
