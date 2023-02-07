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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.DittoConfigError;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of the DevOps config.
 */
@Immutable
public final class DefaultDevOpsConfig implements DevOpsConfig {

    private static final String CONFIG_PATH = "devops";

    private final boolean secured;
    private final DevopsAuthenticationMethod devopsAuthenticationMethod;
    private final String password;
    private final Collection<String> devopsOAuth2Subjects;
    private final boolean statusSecured;
    private final DevopsAuthenticationMethod statusAuthenticationMethod;
    private final String statusPassword;
    private final Collection<String> statusOAuth2Subjects;
    private final OAuthConfig oAuthConfig;

    private DefaultDevOpsConfig(final ConfigWithFallback configWithFallback) {
        secured = configWithFallback.getBoolean(DevOpsConfigValue.SECURED.getConfigPath());
        devopsAuthenticationMethod =
                getDevopsAuthenticationMethod(configWithFallback, DevOpsConfigValue.DEVOPS_AUTHENTICATION_METHOD);
        password = configWithFallback.getString(DevOpsConfigValue.PASSWORD.getConfigPath());
        devopsOAuth2Subjects =
                Collections.unmodifiableList(new ArrayList<>(
                        configWithFallback.getStringList(DevOpsConfigValue.DEVOPS_OAUTH2_SUBJECTS.getConfigPath())));
        statusSecured = configWithFallback.getBoolean(DevOpsConfigValue.STATUS_SECURED.getConfigPath());
        statusAuthenticationMethod =
                getDevopsAuthenticationMethod(configWithFallback, DevOpsConfigValue.STATUS_AUTHENTICATION_METHOD);
        statusPassword = configWithFallback.getString(DevOpsConfigValue.STATUS_PASSWORD.getConfigPath());
        statusOAuth2Subjects =
                Collections.unmodifiableList(new ArrayList<>(
                        configWithFallback.getStringList(DevOpsConfigValue.STATUS_OAUTH2_SUBJECTS.getConfigPath())));
        oAuthConfig = DefaultOAuthConfig.of(configWithFallback);
    }

    private static DevopsAuthenticationMethod getDevopsAuthenticationMethod(final ConfigWithFallback configWithFallback,
            final DevOpsConfigValue devOpsConfigValue) {

        final var methodName = configWithFallback.getString(devOpsConfigValue.getConfigPath());
        return DevopsAuthenticationMethod.fromMethodName(methodName)
                .orElseThrow(() -> {
                    final var message =
                            String.format("Could not find devops authentication method with name <%s>", methodName);
                    return new DittoConfigError(message);
                });
    }

    /**
     * Returns an instance of {@code DefaultDevOpsConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the DevOps config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultDevOpsConfig of(final Config config) {
        return new DefaultDevOpsConfig(ConfigWithFallback.newInstance(config, CONFIG_PATH, DevOpsConfigValue.values()));
    }

    @Override
    public boolean isSecured() {
        return secured;
    }

    @Override
    public DevopsAuthenticationMethod getDevopsAuthenticationMethod() {
        return devopsAuthenticationMethod;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public Collection<String> getDevopsOAuth2Subjects() {
        return devopsOAuth2Subjects;
    }

    @Override
    public boolean isStatusSecured() {
        return statusSecured;
    }

    @Override
    public DevopsAuthenticationMethod getStatusAuthenticationMethod() {
        return statusAuthenticationMethod;
    }

    @Override
    public String getStatusPassword() {
        return statusPassword;
    }

    @Override
    public Collection<String> getStatusOAuth2Subjects() {
        return statusOAuth2Subjects;
    }

    @Override
    public OAuthConfig getOAuthConfig() {
        return oAuthConfig;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultDevOpsConfig that = (DefaultDevOpsConfig) o;
        return Objects.equals(secured, that.secured) &&
                Objects.equals(devopsAuthenticationMethod, that.devopsAuthenticationMethod) &&
                Objects.equals(password, that.password) &&
                Objects.equals(devopsOAuth2Subjects, that.devopsOAuth2Subjects) &&
                Objects.equals(statusSecured, that.statusSecured) &&
                Objects.equals(statusAuthenticationMethod, that.statusAuthenticationMethod) &&
                Objects.equals(statusOAuth2Subjects, that.statusOAuth2Subjects) &&
                Objects.equals(statusPassword, that.statusPassword) &&
                Objects.equals(oAuthConfig, that.oAuthConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(secured, devopsAuthenticationMethod, password, devopsOAuth2Subjects,
                statusSecured, statusAuthenticationMethod, statusPassword, statusOAuth2Subjects, oAuthConfig);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "secured=" + secured +
                ", devopsAuthenticationMethod=" + devopsAuthenticationMethod +
                ", password=*****" +
                ", devopsOAuth2Subject=" + devopsOAuth2Subjects +
                ", statusSecured=" + statusSecured +
                ", statusAuthenticationMethod=" + statusAuthenticationMethod +
                ", statusPassword=*****" +
                ", statusOAuth2Subject=" + statusOAuth2Subjects +
                ", oAuthConfig=" + oAuthConfig +
                "]";
    }

}
