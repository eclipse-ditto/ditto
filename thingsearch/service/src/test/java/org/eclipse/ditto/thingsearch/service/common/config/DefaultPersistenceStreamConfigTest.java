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
package org.eclipse.ditto.thingsearch.service.common.config;

import static org.eclipse.ditto.thingsearch.service.common.config.PersistenceStreamConfig.PersistenceStreamConfigValue;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Duration;

import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.base.service.config.supervision.DefaultExponentialBackOffConfig;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import com.mongodb.WriteConcern;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit tests for {@link DefaultPersistenceStreamConfig}.
 */
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
                areImmutable(),
                provided(WriteConcern.class).isAlsoImmutable());
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

        softly.assertThat(underTest.getAckDelay())
                .as(PersistenceStreamConfigValue.ACK_DELAY.getConfigPath())
                .isEqualTo(PersistenceStreamConfigValue.ACK_DELAY.getDefaultValue());

        softly.assertThat(underTest.getParallelism())
                .as(StreamStageConfig.StreamStageConfigValue.PARALLELISM.getConfigPath())
                .isEqualTo(StreamStageConfig.StreamStageConfigValue.PARALLELISM.getDefaultValue());

        softly.assertThat(underTest.getExponentialBackOffConfig())
                .as("exponential-backoff")
                .isEqualTo(DefaultExponentialBackOffConfig.of(ConfigFactory.empty()));

        softly.assertThat(underTest.getWithAcknowledgementsWriteConcern())
                .as(PersistenceStreamConfigValue.WITH_ACKS_WRITE_CONCERN.getConfigPath())
                .isEqualTo(WriteConcern.valueOf(
                        (String) PersistenceStreamConfigValue.WITH_ACKS_WRITE_CONCERN.getDefaultValue()));
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final PersistenceStreamConfig underTest = DefaultPersistenceStreamConfig.of(config);

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

        softly.assertThat(underTest.getWithAcknowledgementsWriteConcern())
                .as(PersistenceStreamConfigValue.WITH_ACKS_WRITE_CONCERN.getConfigPath())
                .isEqualTo(WriteConcern.MAJORITY);
    }

}
