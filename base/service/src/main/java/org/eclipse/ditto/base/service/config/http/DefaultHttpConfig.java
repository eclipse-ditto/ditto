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
package org.eclipse.ditto.base.service.config.http;

import java.time.Duration;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.DittoConfigError;
import org.eclipse.ditto.internal.utils.config.WithConfigPath;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of {@link HttpConfig}.
 */
@Immutable
public final class DefaultHttpConfig implements HttpConfig, WithConfigPath {

    private static final String CONFIG_PATH = "http";

    private final String hostname;
    private final int port;
    private final Duration coordinatedShutdownTimeout;

    private DefaultHttpConfig(final ConfigWithFallback config) {
        hostname = config.getString(HttpConfigValue.HOSTNAME.getConfigPath());
        port = config.getNonNegativeIntOrThrow(HttpConfigValue.PORT);
        coordinatedShutdownTimeout =
                config.getNonNegativeAndNonZeroDurationOrThrow(HttpConfigValue.COORDINATED_SHUTDOWN_TIMEOUT);
    }

    /**
     * Returns an instance of {@code DefaultHttpConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the HTTP config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws DittoConfigError if {@code config} is invalid.
     */
    public static DefaultHttpConfig of(final Config config) {
        return new DefaultHttpConfig(ConfigWithFallback.newInstance(config, CONFIG_PATH, HttpConfigValue.values()));
    }

    @Override
    public String getHostname() {
        return hostname;
    }

    @Override
    public int getPort() {
        return port;
    }

    @Override
    public Duration getCoordinatedShutdownTimeout() {
        return coordinatedShutdownTimeout;
    }

    /**
     * @return always {@value #CONFIG_PATH}.
     */
    @Override
    public String getConfigPath() {
        return CONFIG_PATH;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultHttpConfig that = (DefaultHttpConfig) o;
        return port == that.port
                && Objects.equals(hostname, that.hostname)
                && Objects.equals(coordinatedShutdownTimeout, that.coordinatedShutdownTimeout);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hostname, port, coordinatedShutdownTimeout);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "hostname=" + hostname +
                ", port=" + port +
                ", coordinatedShutdownTimeout=" + coordinatedShutdownTimeout +
                "]";
    }

}
