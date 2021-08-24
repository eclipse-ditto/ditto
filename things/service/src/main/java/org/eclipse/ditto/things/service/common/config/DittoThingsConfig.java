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
package org.eclipse.ditto.things.service.common.config;

import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.service.config.DittoServiceConfig;
import org.eclipse.ditto.base.service.config.http.HttpConfig;
import org.eclipse.ditto.base.service.config.limits.LimitsConfig;
import org.eclipse.ditto.internal.utils.cluster.config.ClusterConfig;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;
import org.eclipse.ditto.internal.utils.health.config.DefaultHealthCheckConfig;
import org.eclipse.ditto.internal.utils.health.config.HealthCheckConfig;
import org.eclipse.ditto.internal.utils.metrics.config.MetricsConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.DefaultMongoDbConfig;
import org.eclipse.ditto.internal.utils.persistence.mongo.config.MongoDbConfig;
import org.eclipse.ditto.internal.utils.persistence.operations.DefaultPersistenceOperationsConfig;
import org.eclipse.ditto.internal.utils.persistence.operations.PersistenceOperationsConfig;
import org.eclipse.ditto.internal.utils.tracing.config.TracingConfig;

/**
 * This class implements the config of the Ditto Things service.
 */
@Immutable
public final class DittoThingsConfig implements ThingsConfig {

    private static final String CONFIG_PATH = "things";

    private final DittoServiceConfig serviceSpecificConfig;
    private final boolean logIncomingMessages;
    private final PersistenceOperationsConfig persistenceOperationsConfig;
    private final MongoDbConfig mongoDbConfig;
    private final HealthCheckConfig healthCheckConfig;
    private final ThingConfig thingConfig;

    private DittoThingsConfig(final ScopedConfig dittoScopedConfig) {
        serviceSpecificConfig = DittoServiceConfig.of(dittoScopedConfig, CONFIG_PATH);
        logIncomingMessages = serviceSpecificConfig.getBoolean(ThingsConfigValue.LOG_INCOMING_MESSAGES.getConfigPath());
        persistenceOperationsConfig = DefaultPersistenceOperationsConfig.of(dittoScopedConfig);
        mongoDbConfig = DefaultMongoDbConfig.of(dittoScopedConfig);
        healthCheckConfig = DefaultHealthCheckConfig.of(dittoScopedConfig);
        thingConfig = DefaultThingConfig.of(serviceSpecificConfig);
    }

    /**
     * Returns an instance of the things config based on the settings of the specified Config.
     *
     * @param dittoScopedConfig is supposed to provide the settings of the service config at the {@code "ditto"} config
     * path.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DittoThingsConfig of(final ScopedConfig dittoScopedConfig) {
        return new DittoThingsConfig(dittoScopedConfig);
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
    public TracingConfig getTracingConfig() {
        return serviceSpecificConfig.getTracingConfig();
    }

    @Override
    public PersistenceOperationsConfig getPersistenceOperationsConfig() {
        return persistenceOperationsConfig;
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
                Objects.equals(persistenceOperationsConfig, that.persistenceOperationsConfig) &&
                Objects.equals(mongoDbConfig, that.mongoDbConfig) &&
                Objects.equals(healthCheckConfig, that.healthCheckConfig) &&
                Objects.equals(thingConfig, that.thingConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serviceSpecificConfig, logIncomingMessages, persistenceOperationsConfig, mongoDbConfig,
                healthCheckConfig, thingConfig);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "serviceSpecificConfig=" + serviceSpecificConfig +
                ", logIncomingMessages=" + logIncomingMessages +
                ", persistenceOperationsConfig=" + persistenceOperationsConfig +
                ", mongoDbConfig=" + mongoDbConfig +
                ", healthCheckConfig=" + healthCheckConfig +
                ", thingConfig=" + thingConfig +
                "]";
    }

}
