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

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.base.config.DittoServiceWithMongoDbConfig;
import org.eclipse.ditto.services.base.config.HttpConfig;
import org.eclipse.ditto.services.base.config.LimitsConfig;
import org.eclipse.ditto.services.things.persistence.config.DefaultThingConfig;
import org.eclipse.ditto.services.things.persistence.config.ThingConfig;
import org.eclipse.ditto.services.utils.health.config.DefaultHealthCheckConfig;
import org.eclipse.ditto.services.utils.health.config.HealthCheckConfig;
import org.eclipse.ditto.services.utils.persistence.mongo.config.DefaultTagsConfig;
import org.eclipse.ditto.services.utils.persistence.mongo.config.MongoDbConfig;
import org.eclipse.ditto.services.utils.persistence.mongo.config.TagsConfig;

import com.typesafe.config.Config;

/**
 * This class implements the config of the Ditto Things service.
 */
@Immutable
public final class DittoThingsConfig implements ThingsConfig, Serializable {

    private static final String CONFIG_PATH = "things";

    private static final long serialVersionUID = -7526956068560224469L;

    private final DittoServiceWithMongoDbConfig basicConfig;
    private final boolean logIncomingMessages;
    private final HealthCheckConfig healthCheckConfig;
    private final TagsConfig tagsConfig;
    private final ThingConfig thingConfig;

    private DittoThingsConfig(final DittoServiceWithMongoDbConfig dittoServiceConfig) {
        basicConfig = dittoServiceConfig;
        logIncomingMessages = dittoServiceConfig.getBoolean(ThingsConfigValue.LOG_INCOMING_MESSAGES.getConfigPath());
        healthCheckConfig = DefaultHealthCheckConfig.of(dittoServiceConfig);
        tagsConfig = DefaultTagsConfig.of(dittoServiceConfig);
        thingConfig = DefaultThingConfig.of(dittoServiceConfig);
    }

    /**
     * Returns an instance of the things config based on the settings of the specified Config.
     *
     * @param dittoScopedConfig is supposed to provide the settings of the Things service config at
     * {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DittoThingsConfig of(final Config dittoScopedConfig) {
        return new DittoThingsConfig(DittoServiceWithMongoDbConfig.of(dittoScopedConfig, CONFIG_PATH));
    }

    @Override
    public ClusterConfig getClusterConfig() {
        return basicConfig.getClusterConfig();
    }

    @Override
    public LimitsConfig getLimitsConfig() {
        return basicConfig.getLimitsConfig();
    }

    @Override
    public HttpConfig getHttpConfig() {
        return basicConfig.getHttpConfig();
    }

    @Override
    public MetricsConfig getMetricsConfig() {
        return basicConfig.getMetricsConfig();
    }

    @Override
    public MongoDbConfig getMongoDbConfig() {
        return basicConfig.getMongoDbConfig();
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
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DittoThingsConfig that = (DittoThingsConfig) o;
        return logIncomingMessages == that.logIncomingMessages &&
                Objects.equals(basicConfig, that.basicConfig) &&
                Objects.equals(healthCheckConfig, that.healthCheckConfig) &&
                Objects.equals(tagsConfig, that.tagsConfig) &&
                Objects.equals(thingConfig, that.thingConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(basicConfig, logIncomingMessages, healthCheckConfig, tagsConfig, thingConfig);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "basicConfig=" + basicConfig +
                ", logIncomingMessages=" + logIncomingMessages +
                ", healthCheckConfig=" + healthCheckConfig +
                ", tagsConfig=" + tagsConfig +
                ", thingConfig=" + thingConfig +
                "]";
    }

}
