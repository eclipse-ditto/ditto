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

import java.time.Duration;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of {@link TunnelConfig}.
 */
@Immutable
final class DefaultTunnelConfig implements TunnelConfig {

    private static final String CONFIG_PATH = "tunnel";

    private final int workers;
    private final Duration heartbeatInterval;
    private final Duration idleTimeout;
    private final boolean socketKeepalive;

    private DefaultTunnelConfig(final ScopedConfig config) {
        workers = config.getNonNegativeIntOrThrow(TunnelConfigValue.WORKERS);
        heartbeatInterval = config.getNonNegativeAndNonZeroDurationOrThrow(TunnelConfigValue.HEARTBEAT_INTERVAL);
        idleTimeout = config.getNonNegativeDurationOrThrow(TunnelConfigValue.IDLE_TIMEOUT);
        socketKeepalive = config.getBoolean(TunnelConfigValue.SOCKET_KEEPALIVE.getConfigPath());
    }

    /**
     * Returns an instance of {@code DefaultTunnelConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the JavaScript mapping config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultTunnelConfig of(final Config config) {
        return new DefaultTunnelConfig(
                ConfigWithFallback.newInstance(config, CONFIG_PATH, TunnelConfigValue.values()));
    }

    @Override
    public int getWorkers() {
        return workers;
    }

    @Override
    public Duration getHeartbeatInterval() {
        return heartbeatInterval;
    }

    @Override
    public Duration getIdleTimeout() {
        return idleTimeout;
    }

    @Override
    public boolean getSocketKeepAlive() {
        return socketKeepalive;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final DefaultTunnelConfig that = (DefaultTunnelConfig) o;
        return workers == that.workers && socketKeepalive == that.socketKeepalive &&
                Objects.equals(heartbeatInterval, that.heartbeatInterval) &&
                Objects.equals(idleTimeout, that.idleTimeout);
    }

    @Override
    public int hashCode() {
        return Objects.hash(workers, heartbeatInterval, idleTimeout, socketKeepalive);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "workers=" + workers +
                ", heartbeatInterval=" + heartbeatInterval +
                ", idleTimeout=" + idleTimeout +
                ", socketKeepalive=" + socketKeepalive +
                "]";
    }
}
