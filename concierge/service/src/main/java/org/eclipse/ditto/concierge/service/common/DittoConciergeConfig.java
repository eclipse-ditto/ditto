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
package org.eclipse.ditto.concierge.service.common;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.service.config.DittoServiceConfig;
import org.eclipse.ditto.base.service.config.http.HttpConfig;
import org.eclipse.ditto.base.service.config.limits.LimitsConfig;
import org.eclipse.ditto.internal.utils.cluster.config.ClusterConfig;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;
import org.eclipse.ditto.internal.utils.config.WithConfigPath;
import org.eclipse.ditto.internal.utils.health.config.DefaultHealthCheckConfig;
import org.eclipse.ditto.internal.utils.health.config.HealthCheckConfig;
import org.eclipse.ditto.internal.utils.metrics.config.MetricsConfig;
import org.eclipse.ditto.internal.utils.tracing.config.TracingConfig;

/**
 * This class is the implementation of {@link ConciergeConfig} for Ditto's Concierge service.
 */
@Immutable
public final class DittoConciergeConfig implements ConciergeConfig, WithConfigPath {

    private static final String CONFIG_PATH = "concierge";

    private final DittoServiceConfig serviceSpecificConfig;
    private final DefaultHealthCheckConfig healthCheckConfig;
    private final DefaultEnforcementConfig enforcementConfig;
    private final DefaultCachesConfig cachesConfig;
    private final DefaultThingsAggregatorConfig thingsAggregatorConfig;
    private final String defaultNamespace;

    private DittoConciergeConfig(final ScopedConfig dittoScopedConfig) {
        serviceSpecificConfig = DittoServiceConfig.of(dittoScopedConfig, CONFIG_PATH);
        healthCheckConfig = DefaultHealthCheckConfig.of(dittoScopedConfig);
        enforcementConfig = DefaultEnforcementConfig.of(serviceSpecificConfig);
        cachesConfig = DefaultCachesConfig.of(serviceSpecificConfig);
        thingsAggregatorConfig = DefaultThingsAggregatorConfig.of(serviceSpecificConfig);
        defaultNamespace = dittoScopedConfig.getString(ConciergeConfigValue.DEFAULT_NAMESPACE.getConfigPath());
    }

    /**
     * Returns an instance of DittoConciergeConfig based on the settings of the specified Config.
     *
     * @param dittoScopedConfig is supposed to provide the settings of the service config at the {@code "ditto"} config
     * path.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DittoConciergeConfig of(final ScopedConfig dittoScopedConfig) {
        return new DittoConciergeConfig(dittoScopedConfig);
    }

    @Override
    public String getDefaultNamespace() {
        return defaultNamespace;
    }

    @Override
    public EnforcementConfig getEnforcementConfig() {
        return enforcementConfig;
    }

    @Override
    public CachesConfig getCachesConfig() {
        return cachesConfig;
    }

    @Override
    public ThingsAggregatorConfig getThingsAggregatorConfig() {
        return thingsAggregatorConfig;
    }

    @Override
    public ClusterConfig getClusterConfig() {
        return serviceSpecificConfig.getClusterConfig();
    }

    @Override
    public HealthCheckConfig getHealthCheckConfig() {
        return healthCheckConfig;
    }

    @Override
    public LimitsConfig getLimitsConfig() {
        return serviceSpecificConfig.getLimitsConfig();
    }

    @Override
    public HttpConfig getHttpConfig() {
        return serviceSpecificConfig.getHttpConfig();
    }

    @Override
    public MetricsConfig getMetricsConfig() {
        return serviceSpecificConfig.getMetricsConfig();
    }

    @Override
    public TracingConfig getTracingConfig() {
        return serviceSpecificConfig.getTracingConfig();
    }

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
        final DittoConciergeConfig that = (DittoConciergeConfig) o;
        return serviceSpecificConfig.equals(that.serviceSpecificConfig) &&
                healthCheckConfig.equals(that.healthCheckConfig) && enforcementConfig.equals(that.enforcementConfig) &&
                cachesConfig.equals(that.cachesConfig) && thingsAggregatorConfig.equals(that.thingsAggregatorConfig) &&
                defaultNamespace.equals(that.defaultNamespace);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serviceSpecificConfig, healthCheckConfig, enforcementConfig, cachesConfig,
                thingsAggregatorConfig, defaultNamespace);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "serviceSpecificConfig=" + serviceSpecificConfig +
                ", healthCheckConfig=" + healthCheckConfig +
                ", enforcementConfig=" + enforcementConfig +
                ", cachesConfig=" + cachesConfig +
                ", thingsAggregatorConfig=" + thingsAggregatorConfig +
                ", defaultNamespace='" + defaultNamespace +
                "]";
    }

}
