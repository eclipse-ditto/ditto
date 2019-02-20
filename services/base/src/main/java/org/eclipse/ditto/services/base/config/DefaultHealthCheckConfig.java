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
package org.eclipse.ditto.services.base.config;

import java.time.Duration;
import java.util.Objects;

import org.eclipse.ditto.services.base.config.ServiceSpecificConfig.HealthCheckConfig;
import org.eclipse.ditto.services.utils.config.ConfigWithFallback;
import org.eclipse.ditto.services.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of {@link HealthCheckConfig}.
 */
public final class DefaultHealthCheckConfig implements HealthCheckConfig {

    private static final String CONFIG_PATH = "health-check";

    private final boolean enabled;
    private final Duration interval;
    private final boolean persistenceEnabled;
    private final Duration persistenceTimeout;

    private DefaultHealthCheckConfig(final ScopedConfig config) {
        enabled = config.getBoolean(HealthCheckConfigValue.ENABLED.getConfigPath());
        interval = config.getDuration(HealthCheckConfigValue.INTERVAL.getConfigPath());
        persistenceEnabled = config.getBoolean(HealthCheckConfigValue.PERSISTENCE_ENABLED.getConfigPath());
        persistenceTimeout = config.getDuration(HealthCheckConfigValue.PERSISTENCE_TIMEOUT.getConfigPath());
    }

    /**
     * Returns an instance of {@code DefaultHealthCheckConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the health check config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultHealthCheckConfig of(final Config config) {
        return new DefaultHealthCheckConfig(
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
    public boolean isPersistenceEnabled() {
        return persistenceEnabled;
    }

    @Override
    public Duration getPersistenceTimeout() {
        return persistenceTimeout;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultHealthCheckConfig that = (DefaultHealthCheckConfig) o;
        return enabled == that.enabled &&
                persistenceEnabled == that.persistenceEnabled &&
                interval.equals(that.interval) &&
                persistenceTimeout.equals(that.persistenceTimeout);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, interval, persistenceEnabled, persistenceTimeout);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "enabled=" + enabled +
                ", interval=" + interval +
                ", persistenceEnabled=" + persistenceEnabled +
                ", persistenceTimeout=" + persistenceTimeout +
                "]";
    }

}
