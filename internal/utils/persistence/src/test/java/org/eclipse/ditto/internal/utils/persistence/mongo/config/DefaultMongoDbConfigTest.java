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
 * Unit test for {@link DefaultMongoDbConfig}.
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
                .contains("mongoDbUri", "maxQueryTime", "optionsConfig", "connectionPoolConfig",
                        "circuitBreakerConfig", "monitoringConfig");
    }

    @Test
    public void defaultMongodbConfigContainsExactlyValuesOfResourceConfigFile() {
        final DefaultMongoDbConfig underTest = DefaultMongoDbConfig.of(rawMongoDbConfig);

        softly.assertThat(underTest.getMaxQueryTime()).isEqualTo(Duration.ofSeconds(10));
        // query options from the configured Mongo URI in "mongodb_test.conf" must be preserved
        // exception is the "ssl" option where the configured value in the config has priority
        softly.assertThat(underTest.getMongoDbUri())
                .isEqualTo("mongodb://foo:bar@mongodb:27017/test?w=1&ssl=false&sslInvalidHostNameAllowed=true");
        softly.assertThat(underTest.getOptionsConfig()).satisfies(optionsConfig -> {
            softly.assertThat(optionsConfig.isSslEnabled()).isFalse();
        });
        softly.assertThat(underTest.getConnectionPoolConfig()).satisfies(connectionPoolConfig -> {
            softly.assertThat(connectionPoolConfig.getMinSize())
                    .as(MongoDbConfig.ConnectionPoolConfig.ConnectionPoolConfigValue.MIN_SIZE.getConfigPath())
                    .isEqualTo(10);
            softly.assertThat(connectionPoolConfig.getMaxSize())
                    .as(MongoDbConfig.ConnectionPoolConfig.ConnectionPoolConfigValue.MAX_SIZE.getConfigPath())
                    .isEqualTo(1_000);
            softly.assertThat(connectionPoolConfig.getMaxIdleTime())
                    .as(MongoDbConfig.ConnectionPoolConfig.ConnectionPoolConfigValue.MAX_IDLE_TIME.getConfigPath())
                    .isEqualTo(Duration.ofMinutes(5L));
            softly.assertThat(connectionPoolConfig.getMaxWaitTime())
                    .as(MongoDbConfig.ConnectionPoolConfig.ConnectionPoolConfigValue.MAX_WAIT_TIME.getConfigPath())
                    .isEqualTo(Duration.ofSeconds(42L));
            softly.assertThat(connectionPoolConfig.isJmxListenerEnabled())
                    .as(MongoDbConfig.ConnectionPoolConfig.ConnectionPoolConfigValue.JMX_LISTENER_ENABLED.getConfigPath())
                    .isTrue();
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
                DefaultMongoDbConfig.CONFIG_PATH + "." + MongoDbConfig.MongoDbConfigValue.URI.getConfigPath();
        final String sourceMongoDbUri = "mongodb://foo:bar@mongodb:27017/test";
        final Config originalMongoDbConfig =
                ConfigFactory.parseMap(Collections.singletonMap(absoluteMongoDbUriPath, sourceMongoDbUri));
        final DefaultMongoDbConfig underTest = DefaultMongoDbConfig.of(originalMongoDbConfig);

        softly.assertThat(underTest.getMaxQueryTime()).as("maxQueryTime").isEqualTo(Duration.ofMinutes(1));
        softly.assertThat(underTest.getMongoDbUri()).as("mongoDbUri").isEqualTo("mongodb://foo:bar@mongodb:27017/test?ssl=false");
        softly.assertThat(underTest.getOptionsConfig()).satisfies(optionsConfig -> {
            softly.assertThat(optionsConfig.isSslEnabled()).as("ssl").isFalse();
            softly.assertThat(optionsConfig.readPreference())
                    .as("readPreference")
                    .isEqualTo(ReadPreference.PRIMARY_PREFERRED);
        });
        softly.assertThat(underTest.getConnectionPoolConfig()).satisfies(connectionPoolConfig -> {
            softly.assertThat(connectionPoolConfig.getMaxSize()).as("maxSize").isEqualTo(100);
            softly.assertThat(connectionPoolConfig.getMaxWaitTime())
                    .as("maxWaitTime")
                    .isEqualTo(Duration.ofSeconds(30L));
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
