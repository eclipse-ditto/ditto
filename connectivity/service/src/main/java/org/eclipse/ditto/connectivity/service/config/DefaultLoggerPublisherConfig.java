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
package org.eclipse.ditto.connectivity.service.config;

import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;

import com.typesafe.config.Config;

/**
 * Default implementation of {@link LoggerPublisherConfig}.
 */
@Immutable
public final class DefaultLoggerPublisherConfig implements LoggerPublisherConfig {

    private static final String CONFIG_PATH = "publisher";

    private final boolean enabled;
    private final FluencyLoggerPublisherConfig fluencyLoggerPublisherConfig;

    private DefaultLoggerPublisherConfig(final ConfigWithFallback config) {
        enabled = config.getBoolean(ConfigValue.ENABLED.getConfigPath());
        fluencyLoggerPublisherConfig = DefaultFluencyLoggerPublisherConfig.of(config);
    }

    /**
     * Returns {@link LoggerPublisherConfig}.
     *
     * @param config is supposed to provide the settings of the connection config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static LoggerPublisherConfig of(final Config config) {
        return new DefaultLoggerPublisherConfig(
                ConfigWithFallback.newInstance(config, CONFIG_PATH, ConfigValue.values()));
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public FluencyLoggerPublisherConfig getFluencyLoggerPublisherConfig() {
        return fluencyLoggerPublisherConfig;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultLoggerPublisherConfig that = (DefaultLoggerPublisherConfig) o;
        return enabled == that.enabled &&
                Objects.equals(fluencyLoggerPublisherConfig, that.fluencyLoggerPublisherConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, fluencyLoggerPublisherConfig);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                ", enabled=" + enabled +
                ", fluencyLoggerPublisherConfig=" + fluencyLoggerPublisherConfig +
                "]";
    }

}
