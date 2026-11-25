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

import java.time.Duration;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.DittoConfigError;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * Default implementation of {@link PostgresDeleteAtReaperConfig}, reading the {@code postgresql.search.reaper} block
 * (i.e. {@code ditto.postgresql.search.reaper} when handed the {@code ditto} config) — the same relative-path
 * convention {@code DefaultPostgresConfig} uses for {@code postgresql}.
 */
@Immutable
public final class DefaultPostgresDeleteAtReaperConfig implements PostgresDeleteAtReaperConfig {

    /**
     * The HOCON path of the reaper config block, relative to the {@code ditto} namespace.
     */
    public static final String CONFIG_PATH = "postgresql.search.reaper";

    private final Duration interval;
    private final int batchSize;
    private final int maxBatchesPerTick;

    private DefaultPostgresDeleteAtReaperConfig(final ScopedConfig config) {
        interval = config.getDuration(PostgresDeleteAtReaperConfigValue.INTERVAL.getConfigPath());
        batchSize = config.getInt(PostgresDeleteAtReaperConfigValue.BATCH_SIZE.getConfigPath());
        maxBatchesPerTick = config.getInt(PostgresDeleteAtReaperConfigValue.MAX_BATCHES_PER_TICK.getConfigPath());
        if (batchSize < 1) {
            throw new DittoConfigError(String.format(
                    "Invalid ditto.postgresql.search.reaper.batch-size <%d>; expected a value >= 1.", batchSize));
        }
        if (maxBatchesPerTick < 1) {
            throw new DittoConfigError(String.format(
                    "Invalid ditto.postgresql.search.reaper.max-batches-per-tick <%d>; expected a value >= 1.",
                    maxBatchesPerTick));
        }
    }

    /**
     * Returns an instance of {@code DefaultPostgresDeleteAtReaperConfig} based on the settings of the given Config.
     *
     * @param dittoConfig is supposed to provide the settings of the {@code ditto} namespace (the reaper block is read
     * at {@value #CONFIG_PATH} relative to it), exactly like {@code DefaultPostgresConfig.of(Config)}.
     * @return the instance.
     * @throws DittoConfigError if {@code dittoConfig} is invalid.
     */
    public static DefaultPostgresDeleteAtReaperConfig of(final Config dittoConfig) {
        return new DefaultPostgresDeleteAtReaperConfig(
                ConfigWithFallback.newInstance(dittoConfig, CONFIG_PATH, PostgresDeleteAtReaperConfigValue.values()));
    }

    @Override
    public Duration getInterval() {
        return interval;
    }

    @Override
    public int getBatchSize() {
        return batchSize;
    }

    @Override
    public int getMaxBatchesPerTick() {
        return maxBatchesPerTick;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultPostgresDeleteAtReaperConfig that = (DefaultPostgresDeleteAtReaperConfig) o;
        return batchSize == that.batchSize &&
                maxBatchesPerTick == that.maxBatchesPerTick &&
                Objects.equals(interval, that.interval);
    }

    @Override
    public int hashCode() {
        return Objects.hash(interval, batchSize, maxBatchesPerTick);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "interval=" + interval +
                ", batchSize=" + batchSize +
                ", maxBatchesPerTick=" + maxBatchesPerTick +
                "]";
    }

}
