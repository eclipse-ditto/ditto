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
package org.eclipse.ditto.internal.utils.persistentactors.cleanup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Duration;

import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Tests {@link DefaultCleanUpConfig}.
 */
public final class DefaultCleanUpConfigTest {

    private static final Config CONFIG = ConfigFactory.load("cleanup-test");


    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultCleanUpConfig.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultCleanUpConfig.class).verify();
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final var underTest = CleanUpConfig.of(CONFIG);

        assertThat(underTest.isEnabled())
                .describedAs(CleanUpConfig.ConfigValue.ENABLED.getConfigPath())
                .isFalse();

        assertThat(underTest.getQuietPeriod())
                .describedAs(CleanUpConfig.ConfigValue.QUIET_PERIOD.getConfigPath())
                .isEqualTo(Duration.ofMinutes(1));

        assertThat(underTest.getInterval())
                .describedAs(CleanUpConfig.ConfigValue.INTERVAL.getConfigPath())
                .isEqualTo(Duration.ofMinutes(2));

        assertThat(underTest.getTimerThreshold())
                .describedAs(CleanUpConfig.ConfigValue.TIMER_THRESHOLD.getConfigPath())
                .isEqualTo(Duration.ofMinutes(3));

        assertThat(underTest.getCreditsPerBatch())
                .describedAs(CleanUpConfig.ConfigValue.CREDITS_PER_BATCH.getConfigPath())
                .isEqualTo(4);

        assertThat(underTest.getReadsPerQuery())
                .describedAs(CleanUpConfig.ConfigValue.READS_PER_QUERY.getConfigPath())
                .isEqualTo(5);

        assertThat(underTest.getWritesPerCredit())
                .describedAs(CleanUpConfig.ConfigValue.WRITES_PER_CREDIT.getConfigPath())
                .isEqualTo(6);

        assertThat(underTest.shouldDeleteFinalDeletedSnapshot())
                .describedAs(CleanUpConfig.ConfigValue.DELETE_FINAL_DELETED_SNAPSHOT.getConfigPath())
                .isEqualTo(true);
    }
}
