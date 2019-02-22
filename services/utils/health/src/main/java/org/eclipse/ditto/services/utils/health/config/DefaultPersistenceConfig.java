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

import com.typesafe.config.Config;

/**
 * This class is the default implementation of the health check persistence config.
 */
@Immutable
public final class DefaultPersistenceConfig implements PersistenceConfig, Serializable {

    private static final String CONFIG_PATH = "persistence";

    private static final long serialVersionUID = -2709507433137475329L;

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
