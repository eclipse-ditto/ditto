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
package org.eclipse.ditto.services.utils.persistence.mongo.config;

import java.time.Duration;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.ConfigWithFallback;
import org.eclipse.ditto.services.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of {@link org.eclipse.ditto.services.utils.persistence.mongo.config.MongoDbConfig.ConnectionPoolConfig}.
 */
@Immutable
public final class DefaultConnectionPoolConfig implements MongoDbConfig.ConnectionPoolConfig {

    private static final String CONFIG_PATH = "pool";

    private final int maxSize;
    private final int maxWaitQueueSize;
    private final Duration maxWaitTime;
    private final boolean jmxListenerEnabled;

    private DefaultConnectionPoolConfig(final ScopedConfig config) {
        maxSize = config.getInt(ConnectionPoolConfigValue.MAX_SIZE.getConfigPath());
        maxWaitQueueSize = config.getInt(ConnectionPoolConfigValue.MAX_WAIT_QUEUE_SIZE.getConfigPath());
        maxWaitTime = config.getDuration(ConnectionPoolConfigValue.MAX_WAIT_TIME.getConfigPath());
        jmxListenerEnabled = config.getBoolean(ConnectionPoolConfigValue.JMX_LISTENER_ENABLED.getConfigPath());
    }

    /**
     * Returns an instance of {@code DefaultConnectionPoolConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the connection pool config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultConnectionPoolConfig of(final Config config) {
        return new DefaultConnectionPoolConfig(
                ConfigWithFallback.newInstance(config, CONFIG_PATH, ConnectionPoolConfigValue.values()));
    }

    @Override
    public int getMaxSize() {
        return maxSize;
    }

    @Override
    public int getMaxWaitQueueSize() {
        return maxWaitQueueSize;
    }

    @Override
    public Duration getMaxWaitTime() {
        return maxWaitTime;
    }

    @Override
    public boolean isJmxListenerEnabled() {
        return jmxListenerEnabled;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultConnectionPoolConfig that = (DefaultConnectionPoolConfig) o;
        return maxSize == that.maxSize &&
                maxWaitQueueSize == that.maxWaitQueueSize &&
                jmxListenerEnabled == that.jmxListenerEnabled &&
                Objects.equals(maxWaitTime, that.maxWaitTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(maxSize, maxWaitQueueSize, maxWaitTime, jmxListenerEnabled);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "maxSize=" + maxSize +
                ", maxWaitQueueSize=" + maxWaitQueueSize +
                ", maxWaitTime=" + maxWaitTime +
                ", jmxListenerEnabled=" + jmxListenerEnabled +
                "]";
    }

}
