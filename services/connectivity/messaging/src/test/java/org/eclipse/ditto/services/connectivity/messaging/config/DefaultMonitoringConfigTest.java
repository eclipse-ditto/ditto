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
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link org.eclipse.ditto.services.connectivity.messaging.config.DefaultMonitoringConfig}.
 */
public final class DefaultMonitoringConfigTest {

    private static Config connectionTestConf;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void initTestFixture() {
        connectionTestConf = ConfigFactory.load("monitoring-test");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultMonitoringConfig.class,
                areImmutable(),
                provided(MonitoringLoggerConfig.class).isAlsoImmutable(),
                provided(MonitoringCounterConfig.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultMonitoringConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final MonitoringConfig underTest = DefaultMonitoringConfig.of(connectionTestConf);

        softly.assertThat(underTest.logger())
                .as("loggerConfig")
                .satisfies(loggerConfig -> {
                    softly.assertThat(loggerConfig.successCapacity())
                            .as(MonitoringLoggerConfig.MonitoringLoggerConfigValue.SUCCESS_CAPACITY.getConfigPath())
                            .isEqualTo(10);
                    softly.assertThat(loggerConfig.failureCapacity())
                            .as(MonitoringLoggerConfig.MonitoringLoggerConfigValue.FAILURE_CAPACITY.getConfigPath())
                            .isEqualTo(11);
                    softly.assertThat(loggerConfig.logDuration())
                            .as(MonitoringLoggerConfig.MonitoringLoggerConfigValue.LOG_DURATION.getConfigPath())
                            .isEqualTo(Duration.ofMinutes(12));
                    softly.assertThat(loggerConfig.loggingActiveCheckInterval())
                            .as(MonitoringLoggerConfig.MonitoringLoggerConfigValue.LOGGING_ACTIVE_CHECK_INTERVAL.getConfigPath())
                            .isEqualTo(Duration.ofMinutes(13));
                });

        softly.assertThat(underTest.counter())
                .as("counterConfig")
                .satisfies(counterConfig -> softly.assertThat(counterConfig).isNotNull());
    }

}
