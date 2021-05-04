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

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of {@link MongoDbConfig.MonitoringConfig}.
 */
@Immutable
public final class DefaultMonitoringConfig implements MongoDbConfig.MonitoringConfig {

    private static final String CONFIG_PATH = "monitoring";

    private final boolean commandsEnabled;
    private final boolean connectionPoolEnabled;

    private DefaultMonitoringConfig(final ScopedConfig config) {
        commandsEnabled = config.getBoolean(MonitoringConfigValue.COMMANDS_ENABLED.getConfigPath());
        connectionPoolEnabled = config.getBoolean(MonitoringConfigValue.CONNECTION_POOL_ENABLED.getConfigPath());
    }

    /**
     * Returns an instance of {@code DefaultMonitoringConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the monitoring config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultMonitoringConfig of(final Config config) {
        return new DefaultMonitoringConfig(
                ConfigWithFallback.newInstance(config, CONFIG_PATH, MonitoringConfigValue.values()));
    }

    @Override
    public boolean isCommandsEnabled() {
        return commandsEnabled;
    }

    @Override
    public boolean isConnectionPoolEnabled() {
        return connectionPoolEnabled;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultMonitoringConfig that = (DefaultMonitoringConfig) o;
        return commandsEnabled == that.commandsEnabled &&
                connectionPoolEnabled == that.connectionPoolEnabled;
    }

    @Override
    public int hashCode() {
        return Objects.hash(commandsEnabled, connectionPoolEnabled);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "commandsEnabled=" + commandsEnabled +
                ", connectionPoolEnabled=" + connectionPoolEnabled +
                "]";
    }

}
