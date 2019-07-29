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
package org.eclipse.ditto.services.connectivity.messaging.config;

import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.ConfigWithFallback;
import org.eclipse.ditto.services.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of {@link MqttConfig}.
 */
@Immutable
public final class DefaultMqttConfig implements MqttConfig {

    private static final String CONFIG_PATH = "mqtt";

    private final boolean experimental;
    private final int sourceBufferSize;

    private DefaultMqttConfig(final ScopedConfig config) {
        experimental = config.getBoolean(MqttConfigValue.EXPERIMENTAL.getConfigPath());
        sourceBufferSize = config.getInt(MqttConfigValue.SOURCE_BUFFER_SIZE.getConfigPath());
    }

    /**
     * Returns an instance of {@code DefaultMqttConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the JavaScript mapping config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultMqttConfig of(final Config config) {
        return new DefaultMqttConfig(ConfigWithFallback.newInstance(config, CONFIG_PATH, MqttConfigValue.values()));
    }

    @Override
    public boolean isExperimental() {
        return experimental;
    }

    @Override
    public int getSourceBufferSize() {
        return sourceBufferSize;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultMqttConfig that = (DefaultMqttConfig) o;
        return experimental == that.experimental &&
                sourceBufferSize == that.sourceBufferSize;
    }

    @Override
    public int hashCode() {
        return Objects.hash(experimental, sourceBufferSize);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "experimental=" + experimental +
                ", sourceBufferSize=" + sourceBufferSize +
                "]";
    }

}
