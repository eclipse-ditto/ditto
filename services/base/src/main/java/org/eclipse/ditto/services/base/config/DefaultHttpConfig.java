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
package org.eclipse.ditto.services.base.config;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.base.config.ServiceSpecificConfig.HttpConfig;
import org.eclipse.ditto.services.utils.config.ConfigWithFallback;
import org.eclipse.ditto.services.utils.config.ScopedConfig;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of {@link HttpConfig}.
 */
@Immutable
public final class DefaultHttpConfig implements HttpConfig {

    private static final String CONFIG_PATH = "http";

    private final String hostname;
    private final int port;

    private DefaultHttpConfig(final ScopedConfig config) {
        hostname = config.getString(HttpConfigValue.HOSTNAME.getConfigPath());
        port = config.getInt(HttpConfigValue.PORT.getConfigPath());
    }

    /**
     * Returns an instance of {@code DefaultHttpConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the HTTP config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if {@code config} is invalid.
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
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultHttpConfig that = (DefaultHttpConfig) o;
        return port == that.port && hostname.equals(that.hostname);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hostname, port);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "hostname=" + hostname +
                ", port=" + port +
                "]";
    }

}
