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
package org.eclipse.ditto.gateway.service.util.config.health;

import java.time.Duration;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;
import org.eclipse.ditto.internal.utils.health.config.BasicHealthCheckConfig;
import org.eclipse.ditto.internal.utils.health.config.DefaultBasicHealthCheckConfig;

import com.typesafe.config.Config;

/**
 * This class is the default implementation of the Gateway health check config.
 */
@Immutable
public final class DefaultHealthCheckConfig implements HealthCheckConfig {

    private static final String CONFIG_PATH = "health-check";

    private final Duration serviceTimeout;
    private final BasicHealthCheckConfig basicHealthCheckConfig;
    private final ClusterRolesConfig clusterRolesConfig;

    private DefaultHealthCheckConfig(final ScopedConfig scopedConfig,
            final BasicHealthCheckConfig basicHealthCheckConfig, final ClusterRolesConfig clusterRolesConfig) {

        serviceTimeout = scopedConfig.getNonNegativeAndNonZeroDurationOrThrow(HealthCheckConfigValue.SERVICE_TIMEOUT);
        this.basicHealthCheckConfig = basicHealthCheckConfig;
        this.clusterRolesConfig = clusterRolesConfig;
    }

    /**
     * Returns an instance of {@code DefaultHealthCheckConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the health check config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultHealthCheckConfig of(final Config config) {
        final var configWithFallback =
                ConfigWithFallback.newInstance(config, CONFIG_PATH, HealthCheckConfigValue.values());

        return new DefaultHealthCheckConfig(configWithFallback, DefaultBasicHealthCheckConfig.of(config),
                DefaultClusterRolesConfig.of(configWithFallback));
    }

    @Override
    public Duration getServiceTimeout() {
        return serviceTimeout;
    }

    @Override
    public boolean isEnabled() {
        return basicHealthCheckConfig.isEnabled();
    }

    @Override
    public Duration getInterval() {
        return basicHealthCheckConfig.getInterval();
    }

    @Override
    public ClusterRolesConfig getClusterRolesConfig() {
        return clusterRolesConfig;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DefaultHealthCheckConfig that = (DefaultHealthCheckConfig) o;
        return Objects.equals(serviceTimeout, that.serviceTimeout) &&
                Objects.equals(basicHealthCheckConfig, that.basicHealthCheckConfig) &&
                Objects.equals(clusterRolesConfig, that.clusterRolesConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serviceTimeout, basicHealthCheckConfig, clusterRolesConfig);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "serviceTimeout=" + serviceTimeout +
                ", basicHealthCheckConfig=" + basicHealthCheckConfig +
                ", clusterRolesConfig=" + clusterRolesConfig +
                "]";
    }

}
