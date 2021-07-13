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
package org.eclipse.ditto.internal.utils.persistence.mongo.config;

import java.time.Duration;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * Provides the configuration settings of a policy entity's activity check.
 */
@Immutable
public final class DefaultActivityCheckConfig implements ActivityCheckConfig {

    private static final String CONFIG_PATH = "activity-check";

    private final Duration inactiveInterval;
    private final Duration deletedInterval;

    private DefaultActivityCheckConfig(final ScopedConfig scopedConfig) {
        inactiveInterval =
                scopedConfig.getNonNegativeDurationOrThrow(ActivityCheckConfigValue.INACTIVE_INTERVAL);
        deletedInterval =
                scopedConfig.getNonNegativeDurationOrThrow(ActivityCheckConfigValue.DELETED_INTERVAL);
    }

    /**
     * Returns an instance of the default activity check config based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the activity check config at {@value #CONFIG_PATH}.
     * @return instance
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultActivityCheckConfig of(final Config config) {
        return new DefaultActivityCheckConfig(
                ConfigWithFallback.newInstance(config, CONFIG_PATH, ActivityCheckConfigValue.values()));
    }

    @Override
    public Duration getInactiveInterval() {
        return inactiveInterval;
    }

    @Override
    public Duration getDeletedInterval() {
        return deletedInterval;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultActivityCheckConfig that = (DefaultActivityCheckConfig) o;
        return Objects.equals(inactiveInterval, that.inactiveInterval) &&
                Objects.equals(deletedInterval, that.deletedInterval);
    }

    @Override
    public int hashCode() {
        return Objects.hash(inactiveInterval, deletedInterval);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "inactiveInterval=" + inactiveInterval +
                ", deletedInterval=" + deletedInterval +
                "]";
    }

}
