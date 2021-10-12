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

import static org.eclipse.ditto.thingsearch.service.common.config.StreamConfig.StreamConfigValue;
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
 * Unit tests for {@link DefaultStreamConfig}.
 */
public final class DefaultStreamConfigTest {


    private static Config config;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void initTestFixture() {
        config = ConfigFactory.load("stream-test");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultStreamConfig.class,
                areImmutable(),
                provided(PersistenceStreamConfig.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultStreamConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void underTestReturnsDefaultValuesIfBaseConfigWasEmpty() {
        final StreamConfig underTest = DefaultStreamConfig.of(ConfigFactory.empty());

        softly.assertThat(underTest.getMaxArraySize())
                .as(StreamConfigValue.MAX_ARRAY_SIZE.getConfigPath())
                .isEqualTo(StreamConfigValue.MAX_ARRAY_SIZE.getDefaultValue());

        softly.assertThat(underTest.getWriteInterval())
                .as(StreamConfigValue.WRITE_INTERVAL.getConfigPath())
                .isEqualTo(StreamConfigValue.WRITE_INTERVAL.getDefaultValue());
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final StreamConfig underTest = DefaultStreamConfig.of(config);

        softly.assertThat(underTest.getMaxArraySize())
                .as(StreamConfigValue.MAX_ARRAY_SIZE.getConfigPath())
                .isEqualTo(1);

        softly.assertThat(underTest.getWriteInterval())
                .as(StreamConfigValue.WRITE_INTERVAL.getConfigPath())
                .isEqualTo(Duration.ofSeconds(2));
    }

}
