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
package org.eclipse.ditto.services.thingsearch.starter.config;

import java.io.Serializable;
import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.base.config.DittoServiceConfig;
import org.eclipse.ditto.services.base.config.HttpConfig;
import org.eclipse.ditto.services.base.config.LimitsConfig;
import org.eclipse.ditto.services.thingsearch.updater.config.DefaultDeletionConfig;
import org.eclipse.ditto.services.thingsearch.updater.config.DefaultUpdaterConfig;
import org.eclipse.ditto.services.thingsearch.updater.config.DeletionConfig;
import org.eclipse.ditto.services.thingsearch.updater.config.UpdaterConfig;
import org.eclipse.ditto.services.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.services.utils.config.ScopedConfig;
import org.eclipse.ditto.services.utils.health.config.DefaultHealthCheckConfig;
import org.eclipse.ditto.services.utils.health.config.HealthCheckConfig;
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

    private final DeletionConfig deletionConfig;
    private final UpdaterConfig updaterConfig;
    private final DittoServiceConfig dittoServiceConfig;
    private final HealthCheckConfig healthCheckConfig;
    private final IndexInitializationConfig indexInitializationConfig;
    private final MongoDbConfig mongoDbConfig;

    private DittoSearchConfig(final ScopedConfig searchScopedConfig) {
        deletionConfig = DefaultDeletionConfig.of(searchScopedConfig);
        updaterConfig = DefaultUpdaterConfig.of(searchScopedConfig);
        dittoServiceConfig = DittoServiceConfig.of(searchScopedConfig);
        healthCheckConfig = DefaultHealthCheckConfig.of(searchScopedConfig);
        indexInitializationConfig = DefaultIndexInitializationConfig.of(searchScopedConfig);
        mongoDbConfig = DefaultMongoDbConfig.of(searchScopedConfig);
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
        return new DittoSearchConfig(DefaultScopedConfig.newInstance(dittoScopedConfig, CONFIG_PATH));
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

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DittoSearchConfig that = (DittoSearchConfig) o;
        return deletionConfig.equals(that.deletionConfig) &&
                updaterConfig.equals(that.updaterConfig) &&
                dittoServiceConfig.equals(that.dittoServiceConfig) &&
                healthCheckConfig.equals(that.healthCheckConfig) &&
                indexInitializationConfig.equals(that.indexInitializationConfig) &&
                mongoDbConfig.equals(that.mongoDbConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(deletionConfig, updaterConfig, dittoServiceConfig, healthCheckConfig,
                indexInitializationConfig,
                mongoDbConfig);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "deletionConfig=" + deletionConfig +
                ", updaterConfig=" + updaterConfig +
                ", dittoServiceConfig=" + dittoServiceConfig +
                ", healthCheckConfig=" + healthCheckConfig +
                ", indexInitializationConfig=" + indexInitializationConfig +
                ", mongoDbConfig=" + mongoDbConfig +
                "]";
    }

}
