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
package org.eclipse.ditto.services.concierge.starter.config;

import java.io.Serializable;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.base.config.DittoServiceWithMongoDbConfig;
import org.eclipse.ditto.services.concierge.cache.config.CachesConfig;
import org.eclipse.ditto.services.concierge.cache.config.DefaultCachesConfig;
import org.eclipse.ditto.services.concierge.enforcement.config.DefaultEnforcementConfig;
import org.eclipse.ditto.services.concierge.enforcement.config.EnforcementConfig;
import org.eclipse.ditto.services.utils.config.ScopedConfig;
import org.eclipse.ditto.services.utils.config.WithConfigPath;
import org.eclipse.ditto.services.utils.health.config.DefaultHealthCheckConfig;
import org.eclipse.ditto.services.utils.health.config.HealthCheckConfig;
import org.eclipse.ditto.services.utils.persistence.mongo.config.MongoDbConfig;

/**
 * This class is the implementation of {@link ConciergeConfig} for Ditto's Concierge service.
 */
@Immutable
public final class DittoConciergeConfig implements ConciergeConfig, Serializable, WithConfigPath {

    private static final long serialVersionUID = -2837337263022150664L;

    private static final String CONFIG_PATH = "concierge";

    private final DittoServiceWithMongoDbConfig serviceSpecificConfig;
    private final HealthCheckConfig healthCheckConfig;
    private final EnforcementConfig enforcementConfig;
    private final CachesConfig cachesConfig;
    private final ThingsAggregatorConfig thingsAggregatorConfig;

    private DittoConciergeConfig(final DittoServiceWithMongoDbConfig serviceSpecificConfig,
            final HealthCheckConfig healthCheckConfig,
            final EnforcementConfig enforcementConfig,
            final CachesConfig cachesConfig,
            final ThingsAggregatorConfig thingsAggregatorConfig) {

        this.serviceSpecificConfig = serviceSpecificConfig;
        this.healthCheckConfig = healthCheckConfig;
        this.enforcementConfig = enforcementConfig;
        this.cachesConfig = cachesConfig;
        this.thingsAggregatorConfig = thingsAggregatorConfig;
    }

    /**
     * Returns an instance of {@code DittoConciergeConfig} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the Concierge service config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DittoConciergeConfig of(final ScopedConfig config) {
        final DittoServiceWithMongoDbConfig dittoServiceConfig = DittoServiceWithMongoDbConfig.of(config, CONFIG_PATH);

        return new DittoConciergeConfig(dittoServiceConfig,
                DefaultHealthCheckConfig.of(dittoServiceConfig),
                DefaultEnforcementConfig.of(dittoServiceConfig),
                DefaultCachesConfig.of(dittoServiceConfig),
                DefaultThingsAggregatorConfig.of(dittoServiceConfig));
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
    public MongoDbConfig getMongoDbConfig() {
        return serviceSpecificConfig.getMongoDbConfig();
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
                healthCheckConfig.equals(that.healthCheckConfig) &&
                enforcementConfig.equals(that.enforcementConfig) &&
                cachesConfig.equals(that.cachesConfig) &&
                thingsAggregatorConfig.equals(that.thingsAggregatorConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serviceSpecificConfig, healthCheckConfig, enforcementConfig, cachesConfig,
                thingsAggregatorConfig);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "serviceSpecificConfig=" + serviceSpecificConfig +
                ", healthCheckConfig=" + healthCheckConfig +
                ", enforcementConfig=" + enforcementConfig +
                ", cachesConfig=" + cachesConfig +
                ", thingsAggregatorConfig=" + thingsAggregatorConfig +
                "]";
    }

}
