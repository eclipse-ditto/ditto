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
package org.eclipse.ditto.internal.utils.tracing.config;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of {@link TracingConfig}.
 */
@Immutable
public final class DefaultTracingConfig implements TracingConfig {

    private static final String CONFIG_PATH = "tracing";

    private final boolean tracingEnabled;
    private final String propagationChannel;

    private DefaultTracingConfig(final ConfigWithFallback tracingScopedConfig) {
        tracingEnabled = tracingScopedConfig.getBoolean(TracingConfigValue.TRACING_ENABLED.getConfigPath());
        propagationChannel =
                tracingScopedConfig.getString(TracingConfigValue.TRACING_PROPAGATION_CHANNEL.getConfigPath());
    }

    /**
     * Returns an instance of {@code DefaultTracingConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the tracing config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultTracingConfig of(final Config config) {
        return new DefaultTracingConfig(
                ConfigWithFallback.newInstance(config, CONFIG_PATH, TracingConfigValue.values()));
    }

    @Override
    public boolean isTracingEnabled() {
        return tracingEnabled;
    }

    @Override
    public String getPropagationChannel() {
        return propagationChannel;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultTracingConfig that = (DefaultTracingConfig) o;
        return tracingEnabled == that.tracingEnabled &&
                Objects.equals(propagationChannel, that.propagationChannel);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tracingEnabled, propagationChannel);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "tracingEnabled=" + tracingEnabled +
                ", propagationChannel=" + propagationChannel +
                "]";
    }
}
