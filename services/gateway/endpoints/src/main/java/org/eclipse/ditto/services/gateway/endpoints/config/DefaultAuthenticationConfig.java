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
package org.eclipse.ditto.services.gateway.endpoints.config;

import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.ConfigWithFallback;
import org.eclipse.ditto.services.utils.config.ScopedConfig;
import org.eclipse.ditto.services.utils.config.WithConfigPath;

import com.typesafe.config.Config;

/**
 * This interface is the default implementation of the Gateway authentication config.
 */
@Immutable
public final class DefaultAuthenticationConfig implements AuthenticationConfig, WithConfigPath {

    private static final String CONFIG_PATH = "authentication";

    private final boolean dummyAuthEnabled;
    private final HttpProxyConfig httpProxyConfig;
    private final DevOpsConfig devOpsConfig;
    private final OAuthConfig oAuthConfig;

    private DefaultAuthenticationConfig(final ScopedConfig scopedConfig) {
        dummyAuthEnabled = scopedConfig.getBoolean(AuthenticationConfigValue.DUMMY_AUTH_ENABLED.getConfigPath());
        httpProxyConfig = DefaultHttpProxyConfig.of(scopedConfig);
        devOpsConfig = DefaultDevOpsConfig.of(scopedConfig);
        oAuthConfig = DefaultOAuthConfig.of(scopedConfig);
    }

    /**
     * Returns an instance of {@code DefaultAuthenticationConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the authentication config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultAuthenticationConfig of(final Config config) {
        return new DefaultAuthenticationConfig(
                ConfigWithFallback.newInstance(config, CONFIG_PATH, AuthenticationConfigValue.values()));
    }

    @Override
    public boolean isDummyAuthenticationEnabled() {
        return dummyAuthEnabled;
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
        return dummyAuthEnabled == that.dummyAuthEnabled &&
                Objects.equals(httpProxyConfig, that.httpProxyConfig) &&
                Objects.equals(devOpsConfig, that.devOpsConfig) &&
                Objects.equals(oAuthConfig, that.oAuthConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dummyAuthEnabled, httpProxyConfig, devOpsConfig, oAuthConfig);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                ", dummyAuthEnabled=" + dummyAuthEnabled +
                ", httpProxyConfig=" + httpProxyConfig +
                ", devOpsConfig=" + devOpsConfig +
                ", oAuthConfig=" + oAuthConfig +
                "]";
    }

}
