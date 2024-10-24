/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.config.http;

import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of the HTTP proxy config.
 */
@Immutable
public final class DefaultHttpProxyBaseConfig implements HttpProxyBaseConfig {

    private static final String HTTP_PROXY_PATH = "http.proxy";
    private static final String PROXY_PATH = "proxy";

    private final boolean enabled;
    private final String hostName;
    private final int port;
    private final String userName;
    private final String password;

    private DefaultHttpProxyBaseConfig(final ConfigWithFallback configWithFallback) {
        enabled = configWithFallback.getBoolean(HttpProxyConfigValue.ENABLED.getConfigPath());
        hostName = configWithFallback.getString(HttpProxyConfigValue.HOST_NAME.getConfigPath());
        port = configWithFallback.getInt(HttpProxyConfigValue.PORT.getConfigPath());
        userName = configWithFallback.getString(HttpProxyConfigValue.USER_NAME.getConfigPath());
        password = configWithFallback.getString(HttpProxyConfigValue.PASSWORD.getConfigPath());
    }

    /**
     * Returns an instance of {@code DefaultHttpProxyConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the HTTP proxy config at {@value #HTTP_PROXY_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultHttpProxyBaseConfig ofHttpProxy(final Config config) {
        return ofConfigPath(config, HTTP_PROXY_PATH);
    }

    public static DefaultHttpProxyBaseConfig ofProxy(final Config config) {
        return ofConfigPath(config, PROXY_PATH);
    }

    private static DefaultHttpProxyBaseConfig ofConfigPath(final Config config, final String relativePath) {
        return new DefaultHttpProxyBaseConfig(
                ConfigWithFallback.newInstance(config, relativePath, HttpProxyConfigValue.values()));
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public String getHostname() {
        return hostName;
    }

    @Override
    public int getPort() {
        return port;
    }

    @Override
    public String getUsername() {
        return userName;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultHttpProxyBaseConfig that = (DefaultHttpProxyBaseConfig) o;
        return enabled == that.enabled &&
                port == that.port &&
                Objects.equals(hostName, that.hostName) &&
                Objects.equals(userName, that.userName) &&
                Objects.equals(password, that.password);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, hostName, port, userName, password);
    }

    @SuppressWarnings("squid:S2068")
    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "enabled=" + enabled +
                ", hostName=" + hostName +
                ", port=" + port +
                ", userName=" + userName +
                ", password=*****" +
                "]";
    }

}
