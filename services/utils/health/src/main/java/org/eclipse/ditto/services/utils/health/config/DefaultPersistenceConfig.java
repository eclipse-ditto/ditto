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
package org.eclipse.ditto.services.utils.health.config;

import java.time.Duration;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.ConfigWithFallback;
import org.eclipse.ditto.services.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of the health check persistence config.
 */
@Immutable
public final class DefaultPersistenceConfig implements PersistenceConfig {

    private static final String CONFIG_PATH = "persistence";

    private final boolean enabled;
    private final Duration timeout;

    private DefaultPersistenceConfig(final ScopedConfig scopedConfig) {
        enabled = scopedConfig.getBoolean(PersistenceConfigValue.ENABLED.getConfigPath());
        timeout = scopedConfig.getDuration(PersistenceConfigValue.TIMEOUT.getConfigPath());
    }

    /**
     * Returns an instance of {@code DefaultPersistenceConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the persistence config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultPersistenceConfig of(final Config config) {
        return new DefaultPersistenceConfig(
                ConfigWithFallback.newInstance(config, CONFIG_PATH, PersistenceConfigValue.values()));
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public Duration getTimeout() {
        return timeout;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultPersistenceConfig that = (DefaultPersistenceConfig) o;
        return enabled == that.enabled && Objects.equals(timeout, that.timeout);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, timeout);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "enabled=" + enabled +
                ", timeout=" + timeout +
                "]";
    }

}
