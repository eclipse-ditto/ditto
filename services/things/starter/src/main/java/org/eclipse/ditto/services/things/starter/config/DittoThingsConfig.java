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
package org.eclipse.ditto.services.things.starter.config;

import java.io.Serializable;
import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.base.config.DittoServiceConfig;
import org.eclipse.ditto.services.base.config.HttpConfig;
import org.eclipse.ditto.services.base.config.LimitsConfig;
import org.eclipse.ditto.services.base.config.MetricsConfig;
import org.eclipse.ditto.services.things.persistence.config.DefaultThingConfig;
import org.eclipse.ditto.services.things.persistence.config.ThingConfig;
import org.eclipse.ditto.services.utils.cluster.config.ClusterConfig;
import org.eclipse.ditto.services.utils.config.ScopedConfig;
import org.eclipse.ditto.services.utils.health.config.DefaultHealthCheckConfig;
import org.eclipse.ditto.services.utils.health.config.HealthCheckConfig;
import org.eclipse.ditto.services.utils.metrics.config.MetricsConfig;
import org.eclipse.ditto.services.utils.persistence.mongo.config.DefaultMongoDbConfig;
import org.eclipse.ditto.services.utils.persistence.mongo.config.DefaultTagsConfig;
import org.eclipse.ditto.services.utils.persistence.mongo.config.MongoDbConfig;
import org.eclipse.ditto.services.utils.persistence.mongo.config.TagsConfig;

/**
 * This class implements the config of the Ditto Things service.
 */
@Immutable
public final class DittoThingsConfig implements ThingsConfig, Serializable {

    private static final String CONFIG_PATH = "things";

    private static final long serialVersionUID = -7526956068560224469L;

    private final DittoServiceConfig serviceSpecificConfig;
    private final boolean logIncomingMessages;
    private final DefaultMongoDbConfig mongoDbConfig;
    private final DefaultHealthCheckConfig healthCheckConfig;
    private final DefaultTagsConfig tagsConfig;
    private final DefaultThingConfig thingConfig;

    private DittoThingsConfig(final DittoServiceConfig thingsScopedConfig) {
        serviceSpecificConfig = thingsScopedConfig;
        logIncomingMessages = thingsScopedConfig.getBoolean(ThingsConfigValue.LOG_INCOMING_MESSAGES.getConfigPath());
        mongoDbConfig = DefaultMongoDbConfig.of(thingsScopedConfig);
        healthCheckConfig = DefaultHealthCheckConfig.of(thingsScopedConfig);
        tagsConfig = DefaultTagsConfig.of(thingsScopedConfig);
        thingConfig = DefaultThingConfig.of(thingsScopedConfig);
    }

    /**
     * Returns an instance of the things config based on the settings of the specified Config.
     *
     * @param dittoScopedConfig is supposed to provide the settings of the Things service config at
     * {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DittoThingsConfig of(final ScopedConfig dittoScopedConfig) {
        return new DittoThingsConfig(DittoServiceConfig.of(dittoScopedConfig, CONFIG_PATH));
    }

    @Override
    public ClusterConfig getClusterConfig() {
        return serviceSpecificConfig.getClusterConfig();
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
        return mongoDbConfig;
    }

    @Override
    public HealthCheckConfig getHealthCheckConfig() {
        return healthCheckConfig;
    }

    @Override
    public TagsConfig getTagsConfig() {
        return tagsConfig;
    }

    @Override
    public boolean isLogIncomingMessages() {
        return logIncomingMessages;
    }

    @Override
    public ThingConfig getThingConfig() {
        return thingConfig;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DittoThingsConfig that = (DittoThingsConfig) o;
        return logIncomingMessages == that.logIncomingMessages &&
                Objects.equals(serviceSpecificConfig, that.serviceSpecificConfig) &&
                Objects.equals(mongoDbConfig, that.mongoDbConfig) &&
                Objects.equals(healthCheckConfig, that.healthCheckConfig) &&
                Objects.equals(tagsConfig, that.tagsConfig) &&
                Objects.equals(thingConfig, that.thingConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serviceSpecificConfig, logIncomingMessages, mongoDbConfig, healthCheckConfig, tagsConfig,
                thingConfig);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "serviceSpecificConfig=" + serviceSpecificConfig +
                ", logIncomingMessages=" + logIncomingMessages +
                ", mongoDbConfig=" + mongoDbConfig +
                ", healthCheckConfig=" + healthCheckConfig +
                ", tagsConfig=" + tagsConfig +
                ", thingConfig=" + thingConfig +
                "]";
    }

}
