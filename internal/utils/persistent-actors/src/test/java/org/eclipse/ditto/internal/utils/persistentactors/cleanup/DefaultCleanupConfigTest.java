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
 * Tests {@link DefaultCleanupConfig}.
 */
public final class DefaultCleanupConfigTest {

    private static final Config CONFIG = ConfigFactory.load("cleanup-test");


    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultCleanupConfig.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultCleanupConfig.class).verify();
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final var underTest = CleanupConfig.of(CONFIG);

        assertThat(underTest.isEnabled())
                .describedAs(CleanupConfig.ConfigValue.ENABLED.getConfigPath())
                .isFalse();

        assertThat(underTest.getQuietPeriod())
                .describedAs(CleanupConfig.ConfigValue.QUIET_PERIOD.getConfigPath())
                .isEqualTo(Duration.ofMinutes(1));

        assertThat(underTest.getInterval())
                .describedAs(CleanupConfig.ConfigValue.INTERVAL.getConfigPath())
                .isEqualTo(Duration.ofMinutes(2));

        assertThat(underTest.getTimerThreshold())
                .describedAs(CleanupConfig.ConfigValue.TIMER_THRESHOLD.getConfigPath())
                .isEqualTo(Duration.ofMinutes(3));

        assertThat(underTest.getCreditsPerBatch())
                .describedAs(CleanupConfig.ConfigValue.CREDITS_PER_BATCH.getConfigPath())
                .isEqualTo(4);

        assertThat(underTest.getReadsPerQuery())
                .describedAs(CleanupConfig.ConfigValue.READS_PER_QUERY.getConfigPath())
                .isEqualTo(5);

        assertThat(underTest.getWritesPerCredit())
                .describedAs(CleanupConfig.ConfigValue.WRITES_PER_CREDIT.getConfigPath())
                .isEqualTo(6);

        assertThat(underTest.shouldDeleteFinalDeletedSnapshot())
                .describedAs(CleanupConfig.ConfigValue.DELETE_FINAL_DELETED_SNAPSHOT.getConfigPath())
                .isEqualTo(true);
    }
}
