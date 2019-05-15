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
package org.eclipse.ditto.services.policies.starter.config;

import java.io.Serializable;
import java.util.Objects;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.base.config.DittoServiceConfig;
import org.eclipse.ditto.services.base.config.http.HttpConfig;
import org.eclipse.ditto.services.base.config.limits.LimitsConfig;
import org.eclipse.ditto.services.policies.persistence.config.DefaultPolicyConfig;
import org.eclipse.ditto.services.policies.persistence.config.PolicyConfig;
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
 * This class implements the config of the Ditto Policies service.
 */
@Immutable
public final class DittoPoliciesConfig implements PoliciesConfig, Serializable {

    private static final String CONFIG_PATH = "policies";

    private static final long serialVersionUID = -9097093192571860894L;

    private final DittoServiceConfig serviceSpecificConfig;
    private final DefaultMongoDbConfig mongoDbConfig;
    private final DefaultHealthCheckConfig healthCheckConfig;
    private final DefaultPolicyConfig policyConfig;
    private final DefaultTagsConfig tagsConfig;

    private DittoPoliciesConfig(final DittoServiceConfig policiesScopedConfig) {
        serviceSpecificConfig = policiesScopedConfig;
        mongoDbConfig = DefaultMongoDbConfig.of(policiesScopedConfig);
        healthCheckConfig = DefaultHealthCheckConfig.of(policiesScopedConfig);
        policyConfig = DefaultPolicyConfig.of(policiesScopedConfig);
        tagsConfig = DefaultTagsConfig.of(policiesScopedConfig);
    }

    /**
     * Returns an instance of the policies config based on the settings of the specified Config.
     *
     * @param dittoScopedConfig is supposed to provide the settings of the Policies service config at
     * {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DittoPoliciesConfig of(final ScopedConfig dittoScopedConfig) {
        return new DittoPoliciesConfig(DittoServiceConfig.of(dittoScopedConfig, CONFIG_PATH));
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
    public PolicyConfig getPolicyConfig() {
        return policyConfig;
    }

    @Override
    public TagsConfig getTagsConfig() {
        return tagsConfig;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DittoPoliciesConfig that = (DittoPoliciesConfig) o;
        return Objects.equals(serviceSpecificConfig, that.serviceSpecificConfig) &&
                Objects.equals(mongoDbConfig, that.mongoDbConfig) &&
                Objects.equals(healthCheckConfig, that.healthCheckConfig) &&
                Objects.equals(policyConfig, that.policyConfig) &&
                Objects.equals(tagsConfig, that.tagsConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serviceSpecificConfig, mongoDbConfig, healthCheckConfig, policyConfig, tagsConfig);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "serviceSpecificConfig=" + serviceSpecificConfig +
                ", mongoDbConfig=" + mongoDbConfig +
                ", healthCheckConfig=" + healthCheckConfig +
                ", policyConfig=" + policyConfig +
                ", tagsConfig=" + tagsConfig +
                "]";
    }

}
