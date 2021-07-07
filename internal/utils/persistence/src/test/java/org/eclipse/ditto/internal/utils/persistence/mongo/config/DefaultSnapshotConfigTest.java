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
 * Unit test for {@link DefaultSnapshotConfig}.
 */
public final class DefaultSnapshotConfigTest {

    private static Config snapshotTestConf;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void initTestFixture() {
        snapshotTestConf = ConfigFactory.load("snapshot-test");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultSnapshotConfig.class,
                areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultSnapshotConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void underTestReturnsDefaultValuesIfBaseConfigWasEmpty() {
        final DefaultSnapshotConfig underTest = DefaultSnapshotConfig.of(ConfigFactory.empty());

        softly.assertThat(underTest.getInterval())
                .as(SnapshotConfig.SnapshotConfigValue.INTERVAL.getConfigPath())
                .isEqualTo(SnapshotConfig.SnapshotConfigValue.INTERVAL.getDefaultValue());
        softly.assertThat(underTest.getThreshold())
                .as(SnapshotConfig.SnapshotConfigValue.THRESHOLD.getConfigPath())
                .isEqualTo(SnapshotConfig.SnapshotConfigValue.THRESHOLD.getDefaultValue());
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final DefaultSnapshotConfig underTest = DefaultSnapshotConfig.of(snapshotTestConf);

        softly.assertThat(underTest.getInterval())
                .as(SnapshotConfig.SnapshotConfigValue.INTERVAL.getConfigPath())
                .isEqualTo(Duration.ofDays(100L));
        softly.assertThat(underTest.getThreshold())
                .as(SnapshotConfig.SnapshotConfigValue.THRESHOLD.getConfigPath())
                .isEqualTo(2);
    }
}
