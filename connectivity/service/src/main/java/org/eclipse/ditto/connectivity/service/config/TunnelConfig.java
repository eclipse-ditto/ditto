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

import org.eclipse.ditto.internal.utils.config.KnownConfigValue;
import org.eclipse.ditto.utils.jsr305.annotations.AllParametersAndReturnValuesAreNonnullByDefault;

public interface TunnelConfig {

    /**
     * The number of worker threads for the ssh client.
     *
     * @return the number of worker threads.
     */
    int getWorkers();

    /**
     * The heartbeat.
     *
     * @return the heartbeat interval.
     */
    Duration getHeartbeatInterval();

    /**
     * The idle timeout after which the connection is closed.
     *
     * @return the idle timeout.
     */
    Duration getIdleTimeout();

    /**
     * Whether to enable {@link java.net.StandardSocketOptions#SO_KEEPALIVE} option.
     *
     * @return whether {@link java.net.StandardSocketOptions#SO_KEEPALIVE} is enabled.
     */
    boolean getSocketKeepAlive();

    /**
     * An enumeration of the known config path expressions and their associated default values for {@code
     * TunnelConfig}.
     */
    @AllParametersAndReturnValuesAreNonnullByDefault
    enum TunnelConfigValue implements KnownConfigValue {

        /**
         * See documentation on {@link TunnelConfig#getWorkers()}.
         */
        WORKERS("workers", 16),

        /**
         * See documentation on {@link TunnelConfig#getHeartbeatInterval()}.
         */
        HEARTBEAT_INTERVAL("heartbeat-interval", Duration.ofSeconds(30)),

        /**
         * See documentation on {@link TunnelConfig#getIdleTimeout()}.
         * Default is ZERO because we want the ssh tunnel to stay alive indefinitely.
         */
        IDLE_TIMEOUT("idle-timeout", Duration.ZERO),

        /**
         * See documentation on {@link TunnelConfig#getSocketKeepAlive()} .
         */
        SOCKET_KEEPALIVE("socket-keepalive", true);

        private final String path;
        private final Object defaultValue;

        TunnelConfigValue(final String thePath, final Object theDefaultValue) {
            path = thePath;
            defaultValue = theDefaultValue;
        }

        @Override
        public Object getDefaultValue() {
            return defaultValue;
        }

        @Override
        public String getConfigPath() {
            return path;
        }

    }
}
