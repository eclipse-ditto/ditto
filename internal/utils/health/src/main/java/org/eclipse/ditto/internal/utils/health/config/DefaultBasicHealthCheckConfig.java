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
package org.eclipse.ditto.internal.utils.health.config;

import java.time.Duration;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;
import org.eclipse.ditto.internal.utils.config.WithConfigPath;

import com.typesafe.config.Config;

/**
 * This class is the default implementation which provides the basic settings of health checking.
 */
@Immutable
public final class DefaultBasicHealthCheckConfig implements BasicHealthCheckConfig, WithConfigPath {

    private static final String CONFIG_PATH = "health-check";

    private final boolean enabled;
    private final Duration interval;

    private DefaultBasicHealthCheckConfig(final ScopedConfig scopedConfig) {
        enabled = scopedConfig.getBoolean(HealthCheckConfigValue.ENABLED.getConfigPath());
        interval = scopedConfig.getNonNegativeAndNonZeroDurationOrThrow(HealthCheckConfigValue.INTERVAL);
    }

    /**
     * Returns an instance of {@code DefaultBasicHealthCheckConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the basic health check config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultBasicHealthCheckConfig of(final Config config) {
        return new DefaultBasicHealthCheckConfig(
                ConfigWithFallback.newInstance(config, CONFIG_PATH, HealthCheckConfigValue.values()));
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public Duration getInterval() {
        return interval;
    }

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
        final DefaultBasicHealthCheckConfig that = (DefaultBasicHealthCheckConfig) o;
        return enabled == that.enabled &&
                Objects.equals(interval, that.interval);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, interval);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "enabled=" + enabled +
                ", interval=" + interval +
                "]";
    }

}
