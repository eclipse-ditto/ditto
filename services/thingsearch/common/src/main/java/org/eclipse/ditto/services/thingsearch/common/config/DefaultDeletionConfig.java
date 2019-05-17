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
package org.eclipse.ditto.services.thingsearch.common.config;

import java.io.Serializable;
import java.text.MessageFormat;
import java.time.Duration;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.ConfigWithFallback;
import org.eclipse.ditto.services.utils.config.DittoConfigError;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of {@link DeletionConfig}.
 */
@Immutable
public final class DefaultDeletionConfig implements DeletionConfig, Serializable {

    /**
     * Path where the deletion config values are expected.
     */
    static final String CONFIG_PATH = "deletion";

    private static final long serialVersionUID = -3713651539614852547L;

    private final boolean enabled;
    private final Duration deletionAge;
    private final Duration runInterval;
    private final int firstIntervalHour;

    private DefaultDeletionConfig(final ConfigWithFallback deletionScopedConfig) {
        enabled = deletionScopedConfig.getBoolean(DeletionConfigValue.ENABLED.getConfigPath());
        deletionAge = deletionScopedConfig.getDuration(DeletionConfigValue.DELETION_AGE.getConfigPath());
        runInterval = deletionScopedConfig.getDuration(DeletionConfigValue.RUN_INTERVAL.getConfigPath());
        firstIntervalHour = getFirstIntervalHourOrThrow(deletionScopedConfig);
    }

    private static int getFirstIntervalHourOrThrow(final ConfigWithFallback deletionScopedConfig) {
        final String firstIntervalHourConfigPath = DeletionConfigValue.FIRST_INTERVAL_HOUR.getConfigPath();
        final int result = deletionScopedConfig.getInt(firstIntervalHourConfigPath);
        final int maxIntervalHour = 23;
        if (0 > result || maxIntervalHour < result) {
            final String msgPattern = "The value for <{0}> must be between 0 and 23 but it was <{1}>!";
            throw new DittoConfigError(MessageFormat.format(msgPattern, firstIntervalHourConfigPath, result));
        }
        return result;
    }

    /**
     * Returns an instance of DefaultDeletionConfig based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the deletion config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultDeletionConfig of(final Config config) {
        return new DefaultDeletionConfig(
                ConfigWithFallback.newInstance(config, CONFIG_PATH, DeletionConfigValue.values()));
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public Duration getDeletionAge() {
        return deletionAge;
    }

    @Override
    public Duration getRunInterval() {
        return runInterval;
    }

    @Override
    public int getFirstIntervalHour() {
        return firstIntervalHour;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultDeletionConfig that = (DefaultDeletionConfig) o;
        return enabled == that.enabled &&
                firstIntervalHour == that.firstIntervalHour &&
                Objects.equals(deletionAge, that.deletionAge) &&
                Objects.equals(runInterval, that.runInterval);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, deletionAge, runInterval, firstIntervalHour);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "enabled=" + enabled +
                ", deletionAge=" + deletionAge +
                ", runInterval=" + runInterval +
                ", firstIntervalHour=" + firstIntervalHour +
                "]";
    }

}
