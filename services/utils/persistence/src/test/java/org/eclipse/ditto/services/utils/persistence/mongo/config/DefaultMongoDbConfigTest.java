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

import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Duration;
import java.util.Collections;

import org.assertj.core.api.JUnitSoftAssertions;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

/**
 * Unit test for {@link org.eclipse.ditto.services.utils.persistence.mongo.config.DefaultMongoDbConfig}.
 */
public final class DefaultMongoDbConfigTest {

    private static final String MONGODB_CONFIG_FILE_NAME = "mongodb_test.conf";

    private Config rawMongoDbConfig;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @Before
    public void initMongoDbConfig() {
        rawMongoDbConfig = ConfigFactory.parseResources(MONGODB_CONFIG_FILE_NAME);
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultMongoDbConfig.class,
                areImmutable(),
                provided(Config.class,
                        DefaultOptionsConfig.class,
                        DefaultConnectionPoolConfig.class,
                        DefaultCircuitBreakerConfig.class,
                        DefaultMonitoringConfig.class).areAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultMongoDbConfig.class)
                .suppress(Warning.NULL_FIELDS)
                .usingGetClass()
                .verify();
    }

    @Test
    public void toStringContainsExpected() {
        final DefaultMongoDbConfig underTest = DefaultMongoDbConfig.of(rawMongoDbConfig);

        softly.assertThat(underTest.toString()).contains(underTest.getClass().getSimpleName())
                .contains("maxQueryTime", "mongoDbUri", "optionsConfig", "connectionPoolConfig",
                "circuitBreakerConfig", "monitoringConfig");
    }

    @Test
    public void defaultMongodbConfigContainsExactlyValuesOfResourceConfigFile() {
        final DefaultMongoDbConfig underTest = DefaultMongoDbConfig.of(rawMongoDbConfig);

        softly.assertThat(underTest.getMaxQueryTime()).isEqualTo(Duration.ofSeconds(10));
        softly.assertThat(underTest.getMongoDbUri()).isEqualTo("mongodb://foo:bar@mongodb:27017/test?w=1&ssl=false");
        softly.assertThat(underTest.getOptionsConfig()).satisfies(optionsConfig -> {
            softly.assertThat(optionsConfig.isSslEnabled()).isFalse();
        });
        softly.assertThat(underTest.getConnectionPoolConfig()).satisfies(connectionPoolConfig -> {
            softly.assertThat(connectionPoolConfig.getMaxSize()).isEqualTo(1_000);
            softly.assertThat(connectionPoolConfig.getMaxWaitQueueSize()).isEqualTo(1_000);
            softly.assertThat(connectionPoolConfig.getMaxWaitTime()).isEqualTo(Duration.ofSeconds(42L));
            softly.assertThat(connectionPoolConfig.isJmxListenerEnabled()).isTrue();
        });
        softly.assertThat(underTest.getCircuitBreakerConfig()).satisfies(circuitBreakerConfig -> {
            softly.assertThat(circuitBreakerConfig.getMaxFailures()).isEqualTo(23);
            softly.assertThat(circuitBreakerConfig.getTimeoutConfig()).satisfies(timeoutConfig -> {
                softly.assertThat(timeoutConfig.getCall()).isEqualTo(Duration.ofSeconds(13L));
                softly.assertThat(timeoutConfig.getReset()).isEqualTo(Duration.ofSeconds(7L));
            });
        });
        softly.assertThat(underTest.getMonitoringConfig()).satisfies(monitoringConfig -> {
            softly.assertThat(monitoringConfig.isCommandsEnabled()).isTrue();
            softly.assertThat(monitoringConfig.isConnectionPoolEnabled()).isTrue();
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

        softly.assertThat(underTest.getMaxQueryTime()).as("maxQueryTime").isEqualTo(Duration.ofMinutes(1));
        softly.assertThat(underTest.getMongoDbUri()).as("mongoDbUri").isEqualTo("mongodb://foo:bar@mongodb:27017/test");
        softly.assertThat(underTest.getOptionsConfig()).satisfies(optionsConfig -> {
            softly.assertThat(optionsConfig.isSslEnabled()).as("ssl").isFalse();
        });
        softly.assertThat(underTest.getConnectionPoolConfig()).satisfies(connectionPoolConfig -> {
            softly.assertThat(connectionPoolConfig.getMaxSize()).as("maxSize").isEqualTo(100);
            softly.assertThat(connectionPoolConfig.getMaxWaitQueueSize()).as("maxWaitQueueSize").isEqualTo(100);
            softly.assertThat(connectionPoolConfig.getMaxWaitTime()).as("maxWaitTime").isEqualTo(Duration.ofSeconds(30L));
            softly.assertThat(connectionPoolConfig.isJmxListenerEnabled()).as("jmxListenerEnabled").isFalse();
        });
        softly.assertThat(underTest.getCircuitBreakerConfig()).satisfies(circuitBreakerConfig -> {
            softly.assertThat(circuitBreakerConfig.getMaxFailures()).as("maxFailures").isEqualTo(5);
            softly.assertThat(circuitBreakerConfig.getTimeoutConfig()).satisfies(timeoutConfig -> {
                softly.assertThat(timeoutConfig.getCall()).as("call").isEqualTo(Duration.ofSeconds(5L));
                softly.assertThat(timeoutConfig.getReset()).as("reset").isEqualTo(Duration.ofSeconds(10L));
            });
        });
        softly.assertThat(underTest.getMonitoringConfig()).satisfies(monitoringConfig -> {
            softly.assertThat(monitoringConfig.isCommandsEnabled()).as("commands").isFalse();
            softly.assertThat(monitoringConfig.isConnectionPoolEnabled()).as("connection-pool").isFalse();
        });
    }

}