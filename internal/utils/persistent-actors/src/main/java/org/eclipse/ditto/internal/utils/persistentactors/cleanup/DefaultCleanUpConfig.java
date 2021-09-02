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

import java.time.Duration;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ScopedConfig;

@Immutable
final class DefaultCleanUpConfig implements CleanUpConfig {

    static final String CONFIG_PATH = "clean-up";

    private final Duration quietPeriod;
    private final Duration interval;
    private final Duration timerThreshold;
    private final int creditPerBatch;
    private final int readsPerQuery;
    private final int writesPerCredit;
    private final boolean deleteFinalDeletedSnapshot;

    DefaultCleanUpConfig(final Duration quietPeriod,
            final Duration interval,
            final Duration timerThreshold,
            final int creditPerBatch,
            final int readsPerQuery,
            final int writesPerCredit,
            final boolean deleteFinalDeletedSnapshot) {

        this.quietPeriod = quietPeriod;
        this.interval = interval;
        this.timerThreshold = timerThreshold;
        this.creditPerBatch = creditPerBatch;
        this.readsPerQuery = readsPerQuery;
        this.writesPerCredit = writesPerCredit;
        this.deleteFinalDeletedSnapshot = deleteFinalDeletedSnapshot;
    }

    DefaultCleanUpConfig(final ScopedConfig conf) {
        this.quietPeriod = conf.getNonNegativeAndNonZeroDurationOrThrow(ConfigValue.QUIET_PERIOD);
        this.interval = conf.getNonNegativeAndNonZeroDurationOrThrow(ConfigValue.INTERVAL);
        this.timerThreshold = conf.getNonNegativeAndNonZeroDurationOrThrow(ConfigValue.TIMER_THRESHOLD);
        this.creditPerBatch = conf.getNonNegativeIntOrThrow(ConfigValue.CREDITS_PER_BATCH);
        this.readsPerQuery = conf.getPositiveIntOrThrow(ConfigValue.READS_PER_QUERY);
        this.writesPerCredit = conf.getPositiveIntOrThrow(ConfigValue.WRITES_PER_CREDIT);
        this.deleteFinalDeletedSnapshot = conf.getBoolean(ConfigValue.DELETE_FINAL_DELETED_SNAPSHOT.getConfigPath());
    }

    @Override
    public Duration getQuietPeriod() {
        return quietPeriod;
    }

    @Override
    public Duration getInterval() {
        return interval;
    }

    @Override
    public Duration getTimerThreshold() {
        return timerThreshold;
    }

    @Override
    public int getCreditsPerBatch() {
        return creditPerBatch;
    }

    @Override
    public int getReadsPerQuery() {
        return readsPerQuery;
    }

    @Override
    public int getWritesPerCredit() {
        return writesPerCredit;
    }

    @Override
    public boolean shouldDeleteFinalDeletedSnapshot() {
        return deleteFinalDeletedSnapshot;
    }

    @Override
    public boolean equals(final Object o) {
        if (o instanceof DefaultCleanUpConfig) {
            final DefaultCleanUpConfig that = (DefaultCleanUpConfig) o;
            return Objects.equals(quietPeriod, that.quietPeriod) &&
                    Objects.equals(interval, that.interval) &&
                    Objects.equals(timerThreshold, that.timerThreshold) &&
                    creditPerBatch == that.creditPerBatch &&
                    readsPerQuery == that.readsPerQuery &&
                    writesPerCredit == that.writesPerCredit &&
                    deleteFinalDeletedSnapshot == that.deleteFinalDeletedSnapshot;
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(quietPeriod, interval, timerThreshold, creditPerBatch, readsPerQuery, writesPerCredit,
                deleteFinalDeletedSnapshot);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() +
                "[quietPeriod=" + quietPeriod +
                ",interval=" + interval +
                ",timerThreshold=" + timerThreshold +
                ",creditPerBatch=" + creditPerBatch +
                ",readsPerQuery=" + readsPerQuery +
                ",writesPerCredit=" + writesPerCredit +
                ",deleteFinalDeletedSnapshot=" + deleteFinalDeletedSnapshot +
                "]";
    }

}
