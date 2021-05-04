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
package org.eclipse.ditto.gateway.service.proxy.config;

import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Duration;

import org.assertj.core.api.JUnitSoftAssertions;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Tests {@link DefaultStatisticsConfig}.
 */
public final class DefaultStatisticsConfigTest {

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultStatisticsConfig.class,
                areImmutable(),
                assumingFields("shards").areSafelyCopiedUnmodifiableCollectionsWithImmutableElements());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultStatisticsConfig.class).verify();
    }

    @Test
    public void underTestReturnsDefaultValuesIfBaseConfigWasEmpty() {
        final StatisticsConfig underTest = DefaultStatisticsConfig.of(ConfigFactory.empty());

        softly.assertThat(underTest.getAskTimeout())
                .as(StatisticsConfig.ConfigValues.ASK_TIMEOUT.getConfigPath())
                .isEqualTo(Duration.ofSeconds(5L));

        softly.assertThat(underTest.getUpdateInterval())
                .as(StatisticsConfig.ConfigValues.UPDATE_INTERVAL.getConfigPath())
                .isEqualTo(Duration.ofSeconds(15L));

        softly.assertThat(underTest.getDetailsExpireAfter())
                .as(StatisticsConfig.ConfigValues.DETAILS_EXPIRE_AFTER.getConfigPath())
                .isEqualTo(Duration.ofSeconds(1L));

        softly.assertThat(underTest.getShards())
                .as(StatisticsConfig.ConfigValues.SHARDS.getConfigPath())
                .isEmpty();
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final StatisticsConfig underTest = DefaultStatisticsConfig.of(ConfigFactory.load("test-statistics.conf"));

        softly.assertThat(underTest.getAskTimeout())
                .as(StatisticsConfig.ConfigValues.ASK_TIMEOUT.getConfigPath())
                .isEqualTo(Duration.ofSeconds(1234L));

        softly.assertThat(underTest.getUpdateInterval())
                .as(StatisticsConfig.ConfigValues.UPDATE_INTERVAL.getConfigPath())
                .isEqualTo(Duration.ofMinutes(5678L));

        softly.assertThat(underTest.getDetailsExpireAfter())
                .as(StatisticsConfig.ConfigValues.DETAILS_EXPIRE_AFTER.getConfigPath())
                .isEqualTo(Duration.ofDays(9L));

        softly.assertThat(underTest.getShards())
                .as(StatisticsConfig.ConfigValues.SHARDS.getConfigPath())
                .containsExactly(
                        new DefaultStatisticsShardConfig("glass", "macbeth", "/user/ginger"),
                        new DefaultStatisticsShardConfig("crystal", "hamlet", "/user/asparagus")
                );
    }

}
