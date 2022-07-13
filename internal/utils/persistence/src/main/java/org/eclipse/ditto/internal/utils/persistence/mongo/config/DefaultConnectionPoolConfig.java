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

import java.time.Duration;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of {@link MongoDbConfig.ConnectionPoolConfig}.
 */
@Immutable
public final class DefaultConnectionPoolConfig implements MongoDbConfig.ConnectionPoolConfig {

    private static final String CONFIG_PATH = "pool";

    private final int minSize;
    private final int maxSize;
    private final Duration maxIdleTime;
    private final Duration maxWaitTime;
    private final boolean jmxListenerEnabled;

    private DefaultConnectionPoolConfig(final ScopedConfig config) {
        minSize = config.getNonNegativeIntOrThrow(ConnectionPoolConfigValue.MIN_SIZE);
        maxSize = config.getNonNegativeIntOrThrow(ConnectionPoolConfigValue.MAX_SIZE);
        maxIdleTime = config.getDuration(ConnectionPoolConfigValue.MAX_IDLE_TIME.getConfigPath());
        maxWaitTime = config.getNonNegativeDurationOrThrow(ConnectionPoolConfigValue.MAX_WAIT_TIME);
        jmxListenerEnabled = config.getBoolean(ConnectionPoolConfigValue.JMX_LISTENER_ENABLED.getConfigPath());
    }

    /**
     * Returns an instance of {@code DefaultConnectionPoolConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the connection pool config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultConnectionPoolConfig of(final Config config) {
        return new DefaultConnectionPoolConfig(
                ConfigWithFallback.newInstance(config, CONFIG_PATH, ConnectionPoolConfigValue.values()));
    }

    @Override
    public int getMinSize() {
        return minSize;
    }

    @Override
    public int getMaxSize() {
        return maxSize;
    }

    @Override
    public Duration getMaxIdleTime() {
        return maxIdleTime;
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
        return minSize == that.minSize &&
                maxSize == that.maxSize &&
                jmxListenerEnabled == that.jmxListenerEnabled &&
                Objects.equals(maxIdleTime, that.maxIdleTime) &&
                Objects.equals(maxWaitTime, that.maxWaitTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(minSize, maxSize, maxWaitTime, maxIdleTime, jmxListenerEnabled);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "minSize=" + minSize +
                ", maxSize=" + maxSize +
                ", maxWaitTime=" + maxWaitTime +
                ", maxIdleTime=" + maxIdleTime +
                ", jmxListenerEnabled=" + jmxListenerEnabled +
                "]";
    }

}
