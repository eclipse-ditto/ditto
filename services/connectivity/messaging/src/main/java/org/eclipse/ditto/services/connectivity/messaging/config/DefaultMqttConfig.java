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

import java.io.Serializable;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.ConfigWithFallback;
import org.eclipse.ditto.services.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of the MQTT protocol.
 */
@Immutable
public final class DefaultMqttConfig implements ConnectionConfig.MqttConfig, Serializable {

    private static final String CONFIG_PATH = "mqtt";

    private static final long serialVersionUID = 4895715266164857570L;

    private final int sourceBufferSize;

    private DefaultMqttConfig(final ScopedConfig config) {
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
    public int getSourceBufferSize() {
        return sourceBufferSize;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultMqttConfig that = (DefaultMqttConfig) o;
        return Objects.equals(sourceBufferSize, that.sourceBufferSize);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceBufferSize);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "sourceBufferSize=" + sourceBufferSize +
                "]";
    }

}
