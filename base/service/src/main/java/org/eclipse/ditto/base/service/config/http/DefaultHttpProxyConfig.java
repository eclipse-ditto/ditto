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

import java.net.InetSocketAddress;
import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.DittoConfigError;

import com.typesafe.config.Config;

import akka.http.javadsl.ClientTransport;
import akka.http.javadsl.model.headers.HttpCredentials;

/**
 * This class is the default implementation of the HTTP proxy config.
 */
@Immutable
public final class DefaultHttpProxyConfig implements HttpProxyConfig {

    private static final String HTTP_PROXY_PATH = "http.proxy";
    private static final String PROXY_PATH = "proxy";

    private final boolean enabled;
    private final String hostName;
    private final int port;
    private final String userName;
    private final String password;

    private DefaultHttpProxyConfig(final ConfigWithFallback configWithFallback) {
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
    public static DefaultHttpProxyConfig ofHttpProxy(final Config config) {
        return ofConfigPath(config, HTTP_PROXY_PATH);
    }

    public static DefaultHttpProxyConfig ofProxy(final Config config) {
        return ofConfigPath(config, PROXY_PATH);
    }

    private static DefaultHttpProxyConfig ofConfigPath(final Config config, final String relativePath) {
        return new DefaultHttpProxyConfig(
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
    public ClientTransport toClientTransport() {
        if (hostName.isEmpty() || 0 == port) {
            throw new DittoConfigError("When HTTP proxy is enabled via config, at least proxy hostname and port must " +
                    "be configured as well!");
        }
        final InetSocketAddress inetSocketAddress = InetSocketAddress.createUnresolved(hostName, port);

        if (!userName.isEmpty() && !password.isEmpty()) {
            return ClientTransport.httpsProxy(inetSocketAddress, HttpCredentials.create(userName, password));
        }
        return ClientTransport.httpsProxy(inetSocketAddress);
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultHttpProxyConfig that = (DefaultHttpProxyConfig) o;
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
