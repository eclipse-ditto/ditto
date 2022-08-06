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
package org.eclipse.ditto.gateway.service.util.config.security;

import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.service.config.http.DefaultHttpProxyConfig;
import org.eclipse.ditto.base.service.config.http.HttpProxyConfig;
import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;
import org.eclipse.ditto.internal.utils.config.WithConfigPath;

import com.typesafe.config.Config;

/**
 * This interface is the default implementation of the Gateway authentication config.
 */
@Immutable
public final class DefaultAuthenticationConfig implements AuthenticationConfig, WithConfigPath {

    private static final String CONFIG_PATH = "authentication";

    private final boolean preAuthenticationEnabled;
    private final HttpProxyConfig httpProxyConfig;
    private final DevOpsConfig devOpsConfig;
    private final OAuthConfig oAuthConfig;

    private DefaultAuthenticationConfig(final ScopedConfig scopedConfig) {
        preAuthenticationEnabled =
                scopedConfig.getBoolean(AuthenticationConfigValue.PRE_AUTHENTICATION_ENABLED.getConfigPath());
        httpProxyConfig = DefaultHttpProxyConfig.ofHttpProxy(scopedConfig);
        devOpsConfig = DefaultDevOpsConfig.of(scopedConfig);
        oAuthConfig = DefaultOAuthConfig.of(scopedConfig);
    }

    /**
     * Returns an instance of {@code DefaultAuthenticationConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the authentication config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultAuthenticationConfig of(final Config config) {
        return new DefaultAuthenticationConfig(
                ConfigWithFallback.newInstance(config, CONFIG_PATH, AuthenticationConfigValue.values()));
    }

    @Override
    public boolean isPreAuthenticationEnabled() {
        return preAuthenticationEnabled;
    }

    @Override
    public DevOpsConfig getDevOpsConfig() {
        return devOpsConfig;
    }

    @Override
    public HttpProxyConfig getHttpProxyConfig() {
        return httpProxyConfig;
    }

    @Override
    public OAuthConfig getOAuthConfig() {
        return oAuthConfig;
    }

    /**
     * @return always {@value CONFIG_PATH}.
     */
    @Override
    public String getConfigPath() {
        return CONFIG_PATH;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final DefaultAuthenticationConfig that = (DefaultAuthenticationConfig) o;
        return preAuthenticationEnabled == that.preAuthenticationEnabled &&
                Objects.equals(httpProxyConfig, that.httpProxyConfig) &&
                Objects.equals(devOpsConfig, that.devOpsConfig) &&
                Objects.equals(oAuthConfig, that.oAuthConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(preAuthenticationEnabled, httpProxyConfig,
                devOpsConfig, oAuthConfig);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "preAuthenticationEnabled=" + preAuthenticationEnabled +
                ", httpProxyConfig=" + httpProxyConfig +
                ", devOpsConfig=" + devOpsConfig +
                ", oAuthConfig=" + oAuthConfig +
                "]";
    }

}
