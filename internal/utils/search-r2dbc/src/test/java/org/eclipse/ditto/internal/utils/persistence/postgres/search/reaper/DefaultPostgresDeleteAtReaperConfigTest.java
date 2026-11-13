/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.persistence.postgres.search.reaper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.eclipse.ditto.internal.utils.config.DittoConfigError;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Unit tests for {@link DefaultPostgresDeleteAtReaperConfig}: defaults when the block is entirely absent, overrides,
 * and the batch/max-batches validation guard.
 */
public final class DefaultPostgresDeleteAtReaperConfigTest {

    @Test
    public void defaultsApplyWhenTheWholeBlockIsAbsent() {
        final PostgresDeleteAtReaperConfig config = DefaultPostgresDeleteAtReaperConfig.of(ConfigFactory.empty());

        assertThat(config.getInterval()).isEqualTo(Duration.ofSeconds(30L));
        assertThat(config.getBatchSize()).isEqualTo(1_000);
        assertThat(config.getMaxBatchesPerTick()).isEqualTo(50);
    }

    @Test
    public void explicitValuesOverrideTheDefaults() {
        final Config raw = ConfigFactory.parseString(
                "postgresql.search.reaper {\n"
                        + "  interval = 5s\n"
                        + "  batch-size = 250\n"
                        + "  max-batches-per-tick = 4\n"
                        + "}\n");

        final PostgresDeleteAtReaperConfig config = DefaultPostgresDeleteAtReaperConfig.of(raw);

        assertThat(config.getInterval()).isEqualTo(Duration.ofSeconds(5L));
        assertThat(config.getBatchSize()).isEqualTo(250);
        assertThat(config.getMaxBatchesPerTick()).isEqualTo(4);
    }

    @Test
    public void partialOverrideKeepsTheOtherDefaults() {
        final Config raw = ConfigFactory.parseString("postgresql.search.reaper.batch-size = 10\n");

        final PostgresDeleteAtReaperConfig config = DefaultPostgresDeleteAtReaperConfig.of(raw);

        assertThat(config.getBatchSize()).isEqualTo(10);
        assertThat(config.getInterval()).isEqualTo(Duration.ofSeconds(30L));
        assertThat(config.getMaxBatchesPerTick()).isEqualTo(50);
    }

    @Test
    public void zeroBatchSizeIsRejected() {
        final Config raw = ConfigFactory.parseString("postgresql.search.reaper.batch-size = 0\n");

        assertThatThrownBy(() -> DefaultPostgresDeleteAtReaperConfig.of(raw))
                .isInstanceOf(DittoConfigError.class)
                .hasMessageContaining("batch-size");
    }

    @Test
    public void negativeBatchSizeIsRejected() {
        final Config raw = ConfigFactory.parseString("postgresql.search.reaper.batch-size = -1\n");

        assertThatThrownBy(() -> DefaultPostgresDeleteAtReaperConfig.of(raw))
                .isInstanceOf(DittoConfigError.class)
                .hasMessageContaining("batch-size");
    }

    @Test
    public void zeroMaxBatchesPerTickIsRejected() {
        final Config raw = ConfigFactory.parseString("postgresql.search.reaper.max-batches-per-tick = 0\n");

        assertThatThrownBy(() -> DefaultPostgresDeleteAtReaperConfig.of(raw))
                .isInstanceOf(DittoConfigError.class)
                .hasMessageContaining("max-batches-per-tick");
    }

}
