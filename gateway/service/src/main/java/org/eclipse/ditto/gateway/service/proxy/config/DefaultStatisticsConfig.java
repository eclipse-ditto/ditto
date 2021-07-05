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

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;
import org.eclipse.ditto.internal.utils.config.WithConfigPath;

import com.typesafe.config.Config;

/**
 * The implementation of statistics-config.
 */
@Immutable
final class DefaultStatisticsConfig implements StatisticsConfig, WithConfigPath {

    private static final String CONFIG_PATH = "statistics";

    private final Duration askTimeout;
    private final Duration updateInterval;
    private final Duration detailsExpireAfter;
    private final List<StatisticsShardConfig> shards;

    private DefaultStatisticsConfig(final ScopedConfig scopedConfig) {
        askTimeout = scopedConfig.getNonNegativeAndNonZeroDurationOrThrow(ConfigValues.ASK_TIMEOUT);
        updateInterval = scopedConfig.getNonNegativeDurationOrThrow(ConfigValues.UPDATE_INTERVAL);
        detailsExpireAfter = scopedConfig.getNonNegativeDurationOrThrow(ConfigValues.DETAILS_EXPIRE_AFTER);
        shards = scopedConfig.getConfigList(ConfigValues.SHARDS.getConfigPath())
                .stream()
                .map(DefaultStatisticsShardConfig::of)
                .collect(Collectors.toList());
    }

    /**
     * Returns an instance of {@code StatisticsConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the authentication config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    static StatisticsConfig of(final Config config) {
        return new DefaultStatisticsConfig(ConfigWithFallback.newInstance(config, CONFIG_PATH, ConfigValues.values()));
    }

    @Override
    public Duration getAskTimeout() {
        return askTimeout;
    }

    @Override
    public Duration getUpdateInterval() {
        return updateInterval;
    }

    @Override
    public Duration getDetailsExpireAfter() {
        return detailsExpireAfter;
    }

    @Override
    public List<StatisticsShardConfig> getShards() {
        return shards;
    }

    /**
     * @return always {@value CONFIG_PATH}.
     */
    @Override
    public String getConfigPath() {
        return CONFIG_PATH;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultStatisticsConfig that = (DefaultStatisticsConfig) o;
        return askTimeout.equals(that.askTimeout) &&
                updateInterval.equals(that.updateInterval) &&
                detailsExpireAfter.equals(that.detailsExpireAfter) &&
                shards.equals(that.shards);
    }

    @Override
    public int hashCode() {
        return Objects.hash(askTimeout, updateInterval, detailsExpireAfter, shards);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "askTimeout=" + askTimeout +
                ", updateInterval=" + updateInterval +
                ", detailsExpireAfter=" + updateInterval +
                ", shards=" + shards +
                "]";
    }

}
