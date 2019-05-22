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
package org.eclipse.ditto.services.thingsearch.common.config;

import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.base.config.DittoServiceConfig;
import org.eclipse.ditto.services.base.config.http.HttpConfig;
import org.eclipse.ditto.services.base.config.limits.LimitsConfig;
import org.eclipse.ditto.services.utils.cluster.config.ClusterConfig;
import org.eclipse.ditto.services.utils.config.ConfigWithFallback;
import org.eclipse.ditto.services.utils.config.ScopedConfig;
import org.eclipse.ditto.services.utils.health.config.DefaultHealthCheckConfig;
import org.eclipse.ditto.services.utils.health.config.HealthCheckConfig;
import org.eclipse.ditto.services.utils.metrics.config.MetricsConfig;
import org.eclipse.ditto.services.utils.persistence.mongo.config.DefaultIndexInitializationConfig;
import org.eclipse.ditto.services.utils.persistence.mongo.config.DefaultMongoDbConfig;
import org.eclipse.ditto.services.utils.persistence.mongo.config.IndexInitializationConfig;
import org.eclipse.ditto.services.utils.persistence.mongo.config.MongoDbConfig;

/**
 * This class is the default implementation of {@link SearchConfig}.
 */
@Immutable
public final class DittoSearchConfig implements SearchConfig, Serializable {

    private static final String CONFIG_PATH = "things-search";

    private static final long serialVersionUID = -2047392690545433509L;

    @Nullable private final String mongoHintsByNamespace;
    private final DefaultDeleteConfig deleteConfig;
    private final DefaultDeletionConfig deletionConfig;
    private final DefaultUpdaterConfig updaterConfig;
    private final DittoServiceConfig dittoServiceConfig;
    private final DefaultHealthCheckConfig healthCheckConfig;
    private final DefaultIndexInitializationConfig indexInitializationConfig;
    private final DefaultMongoDbConfig mongoDbConfig;
    private final DefaultStreamConfig streamConfig;

    private DittoSearchConfig(final ConfigWithFallback configWithFallback) {
        mongoHintsByNamespace = configWithFallback.getStringOrNull(SearchConfigValue.MONGO_HINTS_BY_NAMESPACE);
        deleteConfig = DefaultDeleteConfig.of(configWithFallback);
        deletionConfig = DefaultDeletionConfig.of(configWithFallback);
        updaterConfig = DefaultUpdaterConfig.of(configWithFallback);
        dittoServiceConfig = DittoServiceConfig.of(configWithFallback);
        healthCheckConfig = DefaultHealthCheckConfig.of(configWithFallback);
        indexInitializationConfig = DefaultIndexInitializationConfig.of(configWithFallback);
        mongoDbConfig = DefaultMongoDbConfig.of(configWithFallback);
        streamConfig = DefaultStreamConfig.of(configWithFallback);
    }

    /**
     * Returns an instance of DittoSearchConfig based on the settings of the specified Config.
     *
     * @param dittoScopedConfig is supposed to provide the settings of the Search service config at
     * {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DittoSearchConfig of(final ScopedConfig dittoScopedConfig) {
        return new DittoSearchConfig(
                ConfigWithFallback.newInstance(dittoScopedConfig, CONFIG_PATH, SearchConfigValue.values()));
    }

    @Override
    public Optional<String> getMongoHintsByNamespace() {
        return Optional.ofNullable(mongoHintsByNamespace);
    }

    @Override
    public DeleteConfig getDeleteConfig() {
        return deleteConfig;
    }

    @Override
    public DeletionConfig getDeletionConfig() {
        return deletionConfig;
    }

    @Override
    public UpdaterConfig getUpdaterConfig() {
        return updaterConfig;
    }

    @Override
    public StreamConfig getStreamConfig() {
        return streamConfig;
    }

    @Override
    public ClusterConfig getClusterConfig() {
        return dittoServiceConfig.getClusterConfig();
    }

    @Override
    public LimitsConfig getLimitsConfig() {
        return dittoServiceConfig.getLimitsConfig();
    }

    @Override
    public HttpConfig getHttpConfig() {
        return dittoServiceConfig.getHttpConfig();
    }

    @Override
    public MetricsConfig getMetricsConfig() {
        return dittoServiceConfig.getMetricsConfig();
    }

    @Override
    public HealthCheckConfig getHealthCheckConfig() {
        return healthCheckConfig;
    }

    @Override
    public IndexInitializationConfig getIndexInitializationConfig() {
        return indexInitializationConfig;
    }

    @Override
    public MongoDbConfig getMongoDbConfig() {
        return mongoDbConfig;
    }

    @SuppressWarnings("OverlyComplexMethod")
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DittoSearchConfig that = (DittoSearchConfig) o;
        return Objects.equals(mongoHintsByNamespace, that.mongoHintsByNamespace) &&
                Objects.equals(deleteConfig, that.deleteConfig) &&
                Objects.equals(deletionConfig, that.deletionConfig) &&
                Objects.equals(updaterConfig, that.updaterConfig) &&
                Objects.equals(dittoServiceConfig, that.dittoServiceConfig) &&
                Objects.equals(healthCheckConfig, that.healthCheckConfig) &&
                Objects.equals(indexInitializationConfig, that.indexInitializationConfig) &&
                Objects.equals(mongoDbConfig, that.mongoDbConfig) &&
                Objects.equals(streamConfig, that.streamConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mongoHintsByNamespace, deleteConfig, deletionConfig, updaterConfig, dittoServiceConfig,
                healthCheckConfig, indexInitializationConfig, mongoDbConfig, streamConfig);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "mongoHintsByNamespace=" + mongoHintsByNamespace +
                ", deleteConfig=" + deleteConfig +
                ", deletionConfig=" + deletionConfig +
                ", updaterConfig=" + updaterConfig +
                ", dittoServiceConfig=" + dittoServiceConfig +
                ", healthCheckConfig=" + healthCheckConfig +
                ", indexInitializationConfig=" + indexInitializationConfig +
                ", mongoDbConfig=" + mongoDbConfig +
                ", streamConfig=" + streamConfig +
                "]";
    }

}
