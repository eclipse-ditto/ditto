/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

import java.time.Duration;
import java.util.Objects;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;

import com.typesafe.config.Config;

/**
 * Package-private implementation of the configuration of the background sync actor.
 */
public final class DefaultBackgroundSyncConfig implements BackgroundSyncConfig {

    private static final String CONFIG_PATH = "background-sync";

    private final Config config;
    private final boolean enabled;
    private final Duration quietPeriod;
    private final int keptEvents;
    private final Duration toleranceWindow;
    private final int throttleThroughput;
    private final Duration throttlePeriod;
    private final Duration idleTimeout;
    private final Duration policyAskTimeout;
    private final Duration minBackoff;
    private final Duration maxBackoff;
    private final int maxRestarts;
    private final Duration recovery;

    private DefaultBackgroundSyncConfig(final Config config) {
        this.config = config;
        enabled = config.getBoolean(ConfigValue.ENABLED.getConfigPath());
        quietPeriod = config.getDuration(ConfigValue.QUIET_PERIOD.getConfigPath());
        keptEvents = config.getInt(ConfigValue.KEEP_EVENTS.getConfigPath());
        toleranceWindow = config.getDuration(ConfigValue.TOLERANCE_WINDOW.getConfigPath());
        throttleThroughput = config.getInt(ConfigValue.THROTTLE_THROUGHPUT.getConfigPath());
        throttlePeriod = config.getDuration(ConfigValue.THROTTLE_PERIOD.getConfigPath());
        idleTimeout = config.getDuration(ConfigValue.IDLE_TIMEOUT.getConfigPath());
        policyAskTimeout = config.getDuration(ConfigValue.POLICY_ASK_TIMEOUT.getConfigPath());
        this.minBackoff = config.getDuration(ConfigValue.MIN_BACKOFF.getConfigPath());
        this.maxBackoff = config.getDuration(ConfigValue.MAX_BACKOFF.getConfigPath());
        this.maxRestarts = config.getInt(ConfigValue.MAX_RESTARTS.getConfigPath());
        this.recovery = config.getDuration(ConfigValue.RECOVERY.getConfigPath());
    }

    /**
     * Parse HOCON into configuration for background sync actor.
     *
     * @param config the HOCON.
     * @return config for background sync actor.
     */
    public static BackgroundSyncConfig parse(final Config config) {
        return new DefaultBackgroundSyncConfig(config);
    }

    public static BackgroundSyncConfig fromUpdaterConfig(final Config updaterConfig) {
        return new DefaultBackgroundSyncConfig(
                ConfigWithFallback.newInstance(updaterConfig, CONFIG_PATH, ConfigValue.values()));
    }

    @Override
    public Config getConfig() {
        return config;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public Duration getQuietPeriod() {
        return quietPeriod;
    }

    @Override
    public int getKeptEvents() {
        return keptEvents;
    }

    /**
     * Get how recent an out-of-date search index entry may be without triggering reindexing.
     * Should be much larger than the normal delay between thing change and full replication in the search index.
     *
     * @return the tolerance window.
     */
    public Duration getToleranceWindow() {
        return toleranceWindow;
    }

    /**
     * Get how many things to update per throttle period.
     *
     * @return the number of things to update per  throttle period.
     */
    public int getThrottleThroughput() {
        return throttleThroughput;
    }

    /**
     * Get the throttle period.
     *
     * @return the throttle period.
     */
    public Duration getThrottlePeriod() {
        return throttlePeriod;
    }

    /**
     * How long to wait before failing the background sync stream when no element passed through for a while.
     * The stream stalls when other services are slow.
     */
    public Duration getIdleTimeout() {
        return idleTimeout;
    }

    /**
     * How long to wait for the policy shard region for the most up-to-date policy revision.
     *
     * @return ask timeout for the policy shard region.
     */
    public Duration getPolicyAskTimeout() {
        return policyAskTimeout;
    }

    @Override
    public Duration getMinBackoff() {
        return minBackoff;
    }

    @Override
    public Duration getMaxBackoff() {
        return maxBackoff;
    }

    @Override
    public int getMaxRestarts() {
        return maxRestarts;
    }

    @Override
    public Duration getRecovery() {
        return recovery;
    }

    @Override
    public boolean equals(final Object o) {
        if (o instanceof DefaultBackgroundSyncConfig) {
            final DefaultBackgroundSyncConfig that = (DefaultBackgroundSyncConfig) o;
            return enabled == that.enabled &&
                    Objects.equals(quietPeriod, that.quietPeriod) &&
                    Objects.equals(idleTimeout, that.idleTimeout) &&
                    keptEvents == that.keptEvents &&
                    Objects.equals(toleranceWindow, that.toleranceWindow) &&
                    Objects.equals(policyAskTimeout, that.policyAskTimeout) &&
                    throttleThroughput == that.throttleThroughput &&
                    Objects.equals(throttlePeriod, that.throttlePeriod) &&
                    Objects.equals(minBackoff, that.minBackoff) &&
                    Objects.equals(maxBackoff, that.maxBackoff) &&
                    maxRestarts == that.maxRestarts &&
                    Objects.equals(recovery, that.recovery) &&
                    Objects.equals(config, that.config);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, quietPeriod, idleTimeout, keptEvents, toleranceWindow, policyAskTimeout,
                throttleThroughput, throttlePeriod, minBackoff, maxBackoff, maxRestarts, recovery, config);
    }

    @Override
    public String toString() {
        // config contains all information in other fields.
        return getClass().getSimpleName() + "[config=" + config + "]";
    }
}
