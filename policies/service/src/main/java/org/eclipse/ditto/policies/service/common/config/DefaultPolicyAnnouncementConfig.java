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
package org.eclipse.ditto.policies.service.common.config;

import java.time.Duration;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.service.config.supervision.DefaultExponentialBackOffConfig;
import org.eclipse.ditto.base.service.config.supervision.ExponentialBackOffConfig;
import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of the policy announcement config.
 */
@Immutable
final class DefaultPolicyAnnouncementConfig implements PolicyAnnouncementConfig {

    static final String CONFIG_PATH = "announcement";

    private final Duration gracePeriod;
    private final Duration maxTimeout;
    private final boolean enableAnnouncementsWhenDeleted;

    private final Duration defaultRandomizationInterval;
    private final ExponentialBackOffConfig exponentialBackOffConfig;

    private DefaultPolicyAnnouncementConfig(final ScopedConfig scopedConfig) {
        gracePeriod = scopedConfig.getDuration(ConfigValue.GRACE_PERIOD.getConfigPath());
        maxTimeout = scopedConfig.getDuration(ConfigValue.MAX_TIMEOUT.getConfigPath());
        enableAnnouncementsWhenDeleted =
                scopedConfig.getBoolean(ConfigValue.ENABLE_ANNOUNCEMENTS_WHEN_DELETED.getConfigPath());
        defaultRandomizationInterval = scopedConfig.getDuration(ConfigValue.DEFAULT_RANDOMIZATION_INTERVAL.getConfigPath());
        exponentialBackOffConfig = DefaultExponentialBackOffConfig.of(scopedConfig);
    }

    static DefaultPolicyAnnouncementConfig of(final Config config) {
        final ConfigWithFallback mappingScopedConfig =
                ConfigWithFallback.newInstance(config, CONFIG_PATH, ConfigValue.values());
        return new DefaultPolicyAnnouncementConfig(mappingScopedConfig);
    }

    @Override
    public Duration getGracePeriod() {
        return gracePeriod;
    }

    @Override
    public Duration getMaxTimeout() {
        return maxTimeout;
    }

    @Override
    public boolean isEnableAnnouncementsWhenDeleted() {
        return enableAnnouncementsWhenDeleted;
    }

    @Override
    public Duration getDefaultRandomizationInterval() {
        return defaultRandomizationInterval;
    }

    @Override
    public ExponentialBackOffConfig getExponentialBackOffConfig() {
        return exponentialBackOffConfig;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultPolicyAnnouncementConfig that = (DefaultPolicyAnnouncementConfig) o;
        return Objects.equals(gracePeriod, that.gracePeriod) &&
                Objects.equals(maxTimeout, that.maxTimeout) &&
                enableAnnouncementsWhenDeleted == that.enableAnnouncementsWhenDeleted &&
                Objects.equals(defaultRandomizationInterval, that.defaultRandomizationInterval) &&
                Objects.equals(exponentialBackOffConfig, that.exponentialBackOffConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(gracePeriod, maxTimeout, enableAnnouncementsWhenDeleted, defaultRandomizationInterval,
                exponentialBackOffConfig);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "gracePeriod=" + gracePeriod +
                ", maxTimeout=" + maxTimeout +
                ", enableAnnouncementsWhenDeleted=" + enableAnnouncementsWhenDeleted +
                ", defaultRandomizationInterval=" + defaultRandomizationInterval +
                ", exponentialBackOffConfig" + exponentialBackOffConfig +
                "]";
    }

}
