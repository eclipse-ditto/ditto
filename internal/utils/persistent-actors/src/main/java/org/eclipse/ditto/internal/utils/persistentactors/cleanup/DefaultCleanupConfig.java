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
import java.util.Map;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ScopedConfig;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

@Immutable
final class DefaultCleanupConfig implements CleanupConfig {

    static final String CONFIG_PATH = "cleanup";

    private final boolean enabled;
    private final Duration historyRetentionDuration;
    private final Duration quietPeriod;
    private final Duration interval;
    private final Duration timerThreshold;
    private final int creditsPerBatch;
    private final int readsPerQuery;
    private final int writesPerCredit;
    private final boolean deleteFinalDeletedSnapshot;

    DefaultCleanupConfig(final boolean enabled,
            final Duration historyRetentionDuration,
            final Duration quietPeriod,
            final Duration interval,
            final Duration timerThreshold,
            final int creditsPerBatch,
            final int readsPerQuery,
            final int writesPerCredit,
            final boolean deleteFinalDeletedSnapshot) {
        this.enabled = enabled;
        this.historyRetentionDuration = historyRetentionDuration;
        this.quietPeriod = quietPeriod;
        this.interval = interval;
        this.timerThreshold = timerThreshold;
        this.creditsPerBatch = creditsPerBatch;
        this.readsPerQuery = readsPerQuery;
        this.writesPerCredit = writesPerCredit;
        this.deleteFinalDeletedSnapshot = deleteFinalDeletedSnapshot;
    }

    DefaultCleanupConfig(final ScopedConfig conf) {
        this.enabled = conf.getBoolean(ConfigValue.ENABLED.getConfigPath());
        this.historyRetentionDuration = conf.getNonNegativeDurationOrThrow(ConfigValue.HISTORY_RETENTION_DURATION);
        this.quietPeriod = conf.getNonNegativeAndNonZeroDurationOrThrow(ConfigValue.QUIET_PERIOD);
        this.interval = conf.getNonNegativeAndNonZeroDurationOrThrow(ConfigValue.INTERVAL);
        this.timerThreshold = conf.getNonNegativeAndNonZeroDurationOrThrow(ConfigValue.TIMER_THRESHOLD);
        this.creditsPerBatch = conf.getNonNegativeIntOrThrow(ConfigValue.CREDITS_PER_BATCH);
        this.readsPerQuery = conf.getPositiveIntOrThrow(ConfigValue.READS_PER_QUERY);
        this.writesPerCredit = conf.getPositiveIntOrThrow(ConfigValue.WRITES_PER_CREDIT);
        this.deleteFinalDeletedSnapshot = conf.getBoolean(ConfigValue.DELETE_FINAL_DELETED_SNAPSHOT.getConfigPath());
    }

    @Override
    public Config render() {
        final Map<String, Object> configMap = Map.of(
                ConfigValue.ENABLED.getConfigPath(), enabled,
                ConfigValue.HISTORY_RETENTION_DURATION.getConfigPath(), historyRetentionDuration,
                ConfigValue.QUIET_PERIOD.getConfigPath(), quietPeriod,
                ConfigValue.INTERVAL.getConfigPath(), interval,
                ConfigValue.TIMER_THRESHOLD.getConfigPath(), timerThreshold,
                ConfigValue.CREDITS_PER_BATCH.getConfigPath(), creditsPerBatch,
                ConfigValue.READS_PER_QUERY.getConfigPath(), readsPerQuery,
                ConfigValue.WRITES_PER_CREDIT.getConfigPath(), writesPerCredit,
                ConfigValue.DELETE_FINAL_DELETED_SNAPSHOT.getConfigPath(), deleteFinalDeletedSnapshot
        );
        return ConfigFactory.parseMap(configMap);
    }

    @Override
    public CleanupConfig setAll(final Config config) {
        return CleanupConfig.of(config.withFallback(render()).atKey(CONFIG_PATH));
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public Duration getHistoryRetentionDuration() {
        return historyRetentionDuration;
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
        return creditsPerBatch;
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
        if (o instanceof DefaultCleanupConfig that) {
            return enabled == that.enabled &&
                    Objects.equals(historyRetentionDuration, that.historyRetentionDuration) &&
                    Objects.equals(quietPeriod, that.quietPeriod) &&
                    Objects.equals(interval, that.interval) &&
                    Objects.equals(timerThreshold, that.timerThreshold) &&
                    creditsPerBatch == that.creditsPerBatch &&
                    readsPerQuery == that.readsPerQuery &&
                    writesPerCredit == that.writesPerCredit &&
                    deleteFinalDeletedSnapshot == that.deleteFinalDeletedSnapshot;
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, historyRetentionDuration, quietPeriod, interval, timerThreshold, creditsPerBatch,
                readsPerQuery, writesPerCredit, deleteFinalDeletedSnapshot);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" +
                "enabled=" + enabled +
                ", minAgeFromNow=" + historyRetentionDuration +
                ", quietPeriod=" + quietPeriod +
                ", interval=" + interval +
                ", timerThreshold=" + timerThreshold +
                ", creditPerBatch=" + creditsPerBatch +
                ", readsPerQuery=" + readsPerQuery +
                ", writesPerCredit=" + writesPerCredit +
                ", deleteFinalDeletedSnapshot=" + deleteFinalDeletedSnapshot +
                "]";
    }

}
