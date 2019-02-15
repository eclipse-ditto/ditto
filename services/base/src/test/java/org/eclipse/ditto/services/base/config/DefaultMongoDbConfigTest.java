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
package org.eclipse.ditto.services.base.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Duration;
import java.util.Collections;

import org.junit.Before;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link org.eclipse.ditto.services.base.config.DefaultMongoDbConfig}.
 */
public final class DefaultMongoDbConfigTest {

    private static final String MONGODB_CONFIG_FILE_NAME = "mongodb_test.conf";

    private Config mongoDbConfig;

    @Before
    public void initMongoDbConfig() {
        mongoDbConfig = ConfigFactory.parseResources(MONGODB_CONFIG_FILE_NAME);
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultMongoDbConfig.class,
                areImmutable(),
                provided(Config.class,
                        DefaultMongoDbConfig.DefaultOptionsConfig.class,
                        DefaultMongoDbConfig.DefaultConnectionPoolConfig.class,
                        DefaultMongoDbConfig.DefaultCircuitBreakerConfig.class,
                        DefaultMongoDbConfig.DefaultMonitoringConfig.class).areAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultMongoDbConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void toStringContainsExpected() {
        final DefaultMongoDbConfig underTest = DefaultMongoDbConfig.of(mongoDbConfig);

        assertThat(underTest.toString()).contains("config", "mongoDbUri", "optionsConfig", "connectionPoolConfig",
                "circuitBreakerConfig", "monitoringConfig");
    }

    @Test
    public void defaultMongodbConfigContainsExactlyValuesOfResourceConfigFile() {
        final DefaultMongoDbConfig underTest = DefaultMongoDbConfig.of(mongoDbConfig);

        assertThat(underTest.getMaxQueryTime()).isEqualTo(Duration.ofSeconds(10));
        assertThat(underTest.getMongoDbUri()).isEqualTo("mongodb://foo:bar@mongodb:27017/test?ssl=true&w=1");
        assertThat(underTest.getOptionsConfig()).satisfies(optionsConfig -> {
            assertThat(optionsConfig.isSslEnabled()).isTrue();
        });
        assertThat(underTest.getConnectionPoolConfig()).satisfies(connectionPoolConfig -> {
            assertThat(connectionPoolConfig.getMaxSize()).isEqualTo(1_000);
            assertThat(connectionPoolConfig.getMaxWaitQueueSize()).isEqualTo(1_000);
            assertThat(connectionPoolConfig.getMaxWaitTime()).isEqualTo(Duration.ofSeconds(42L));
            assertThat(connectionPoolConfig.isJmxListenerEnabled()).isTrue();
        });
        assertThat(underTest.getCircuitBreakerConfig()).satisfies(circuitBreakerConfig -> {
            assertThat(circuitBreakerConfig.getMaxFailures()).isEqualTo(23);
            assertThat(circuitBreakerConfig.getTimeoutConfig()).satisfies(timeoutConfig -> {
                assertThat(timeoutConfig.getCall()).isEqualTo(Duration.ofSeconds(13L));
                assertThat(timeoutConfig.getReset()).isEqualTo(Duration.ofSeconds(7L));
            });
        });
        assertThat(underTest.getMonitoringConfig()).satisfies(monitoringConfig -> {
            assertThat(monitoringConfig.isCommandsEnabled()).isTrue();
            assertThat(monitoringConfig.isConnectionPoolEnabled()).isTrue();
        });
    }

    @Test
    public void defaultMongodbConfigContainsExactlyFallBackValuesIfEmptyResourceConfigFile() {
        final String absoluteMongoDbUriPath =
                DefaultMongoDbConfig.CONFIG_PATH + "." + MongoDbUriSupplier.URI_CONFIG_PATH;
        final String sourceMongoDbUri = "mongodb://foo:bar@mongodb:27017/test";
        final Config originalMongoDbConfig =
                ConfigFactory.parseMap(Collections.singletonMap(absoluteMongoDbUriPath, sourceMongoDbUri));
        final DefaultMongoDbConfig underTest = DefaultMongoDbConfig.of(originalMongoDbConfig);

        assertThat(underTest.getMaxQueryTime()).as("maxQueryTime").isEqualTo(Duration.ofMinutes(1));
        assertThat(underTest.getMongoDbUri()).as("mongoDbUri").isEqualTo("mongodb://foo:bar@mongodb:27017/test");
        assertThat(underTest.getOptionsConfig()).satisfies(optionsConfig -> {
            assertThat(optionsConfig.isSslEnabled()).as("ssl").isFalse();
        });
        assertThat(underTest.getConnectionPoolConfig()).satisfies(connectionPoolConfig -> {
            assertThat(connectionPoolConfig.getMaxSize()).as("maxSize").isEqualTo(100);
            assertThat(connectionPoolConfig.getMaxWaitQueueSize()).as("maxWaitQueueSize").isEqualTo(100);
            assertThat(connectionPoolConfig.getMaxWaitTime()).as("maxWaitTime").isEqualTo(Duration.ofSeconds(30L));
            assertThat(connectionPoolConfig.isJmxListenerEnabled()).as("jmxListenerEnabled").isFalse();
        });
        assertThat(underTest.getCircuitBreakerConfig()).satisfies(circuitBreakerConfig -> {
            assertThat(circuitBreakerConfig.getMaxFailures()).as("maxFailures").isEqualTo(5);
            assertThat(circuitBreakerConfig.getTimeoutConfig()).satisfies(timeoutConfig -> {
                assertThat(timeoutConfig.getCall()).as("call").isEqualTo(Duration.ofSeconds(5L));
                assertThat(timeoutConfig.getReset()).as("reset").isEqualTo(Duration.ofSeconds(10L));
            });
        });
        assertThat(underTest.getMonitoringConfig()).satisfies(monitoringConfig -> {
            assertThat(monitoringConfig.isCommandsEnabled()).as("commands").isFalse();
            assertThat(monitoringConfig.isConnectionPoolEnabled()).as("connection-pool").isFalse();
        });
    }

}