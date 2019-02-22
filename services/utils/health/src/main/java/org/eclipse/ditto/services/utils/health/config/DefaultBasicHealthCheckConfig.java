/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.utils.health.config;

import java.io.Serializable;
import java.time.Duration;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.ConfigWithFallback;
import org.eclipse.ditto.services.utils.config.ScopedConfig;
import org.eclipse.ditto.services.utils.config.WithConfigPath;

import com.typesafe.config.Config;

/**
 * This class is the default implementation which provides the basic settings of health checking.
 */
@Immutable
public final class DefaultBasicHealthCheckConfig implements BasicHealthCheckConfig, Serializable, WithConfigPath {

    private static final String CONFIG_PATH = "health-check";

    private static final long serialVersionUID = -1688454147237793760L;

    private final boolean enabled;
    private final Duration interval;

    private DefaultBasicHealthCheckConfig(final ScopedConfig scopedConfig) {
        enabled = scopedConfig.getBoolean(HealthCheckConfigValue.ENABLED.getConfigPath());
        interval = scopedConfig.getDuration(HealthCheckConfigValue.INTERVAL.getConfigPath());
    }

    /**
     * Returns an instance of {@code DefaultBasicHealthCheckConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the basic health check config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if {@code config} is invalid.
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
