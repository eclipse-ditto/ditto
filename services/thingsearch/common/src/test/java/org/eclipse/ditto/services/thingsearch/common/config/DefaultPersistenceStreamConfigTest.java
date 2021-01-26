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
package org.eclipse.ditto.services.thingsearch.common.config;

import static org.eclipse.ditto.services.thingsearch.common.config.PersistenceStreamConfig.PersistenceStreamConfigValue;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Duration;

import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.services.base.config.supervision.DefaultExponentialBackOffConfig;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

public final class DefaultPersistenceStreamConfigTest {


    private static Config config;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void initTestFixture() {
        config = ConfigFactory.load("persistence-test");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultPersistenceStreamConfig.class,
                areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultPersistenceStreamConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void underTestReturnsDefaultValuesIfBaseConfigWasEmpty() {
        final PersistenceStreamConfig underTest = DefaultPersistenceStreamConfig.of(ConfigFactory.empty());

        softly.assertThat(underTest.getMaxBulkSize())
                .as(PersistenceStreamConfigValue.MAX_BULK_SIZE.getConfigPath())
                .isEqualTo(PersistenceStreamConfigValue.MAX_BULK_SIZE.getDefaultValue());

        softly.assertThat(underTest.getAckDelay())
                .as(PersistenceStreamConfigValue.ACK_DELAY.getConfigPath())
                .isEqualTo(PersistenceStreamConfigValue.ACK_DELAY.getDefaultValue());

        softly.assertThat(underTest.getParallelism())
                .as(StreamStageConfig.StreamStageConfigValue.PARALLELISM.getConfigPath())
                .isEqualTo(StreamStageConfig.StreamStageConfigValue.PARALLELISM.getDefaultValue());

        softly.assertThat(underTest.getExponentialBackOffConfig())
                .as("exponential-backoff")
                .isEqualTo(DefaultExponentialBackOffConfig.of(ConfigFactory.empty()));
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final PersistenceStreamConfig underTest = DefaultPersistenceStreamConfig.of(config);

        softly.assertThat(underTest.getMaxBulkSize())
                .as(PersistenceStreamConfigValue.MAX_BULK_SIZE.getConfigPath())
                .isEqualTo(65);

        softly.assertThat(underTest.getAckDelay())
                .as(PersistenceStreamConfigValue.ACK_DELAY.getConfigPath())
                .isEqualTo(Duration.ofSeconds(66L));

        softly.assertThat(underTest.getParallelism())
                .as(StreamStageConfig.StreamStageConfigValue.PARALLELISM.getConfigPath())
                .isEqualTo(64);

        softly.assertThat(underTest.getExponentialBackOffConfig())
                .as("exponential-backoff")
                .isEqualTo(DefaultExponentialBackOffConfig.of(
                        config.getConfig(DefaultPersistenceStreamConfig.CONFIG_PATH)));
    }

}
