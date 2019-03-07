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
package org.eclipse.ditto.services.policies.starter.config;

import java.io.Serializable;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.base.config.DittoServiceWithMongoDbConfig;
import org.eclipse.ditto.services.base.config.HttpConfig;
import org.eclipse.ditto.services.base.config.LimitsConfig;
import org.eclipse.ditto.services.policies.persistence.config.DefaultPolicyConfig;
import org.eclipse.ditto.services.policies.persistence.config.PolicyConfig;
import org.eclipse.ditto.services.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.services.utils.config.ScopedConfig;
import org.eclipse.ditto.services.utils.health.config.DefaultHealthCheckConfig;
import org.eclipse.ditto.services.utils.health.config.HealthCheckConfig;
import org.eclipse.ditto.services.utils.persistence.mongo.config.MongoDbConfig;

/**
 * TODO Javadoc
 */
@Immutable
public final class DittoPoliciesConfig implements PoliciesConfig, Serializable {

    private static final String CONFIG_PATH = "policies";

    private final DittoServiceWithMongoDbConfig dittoServiceConfig;
    private final HealthCheckConfig healthCheckConfig;
    private final PolicyConfig policyConfig;
    private final TagsConfig tagsConfig;

    private DittoPoliciesConfig(final DittoServiceWithMongoDbConfig dittoServiceConfig,
            final ScopedConfig policiesScopedConfig) {

        this.dittoServiceConfig = dittoServiceConfig;
        healthCheckConfig = DefaultHealthCheckConfig.of(policiesScopedConfig);
        policyConfig = DefaultPolicyConfig.of(policiesScopedConfig);
        tagsConfig = DefaultTagsConfig.of(policiesScopedConfig);
    }

    /**
     * Returns an instance of {@code DittoPoliciesConfig} based on the settings of the specified Config.
     *
     * @param dittoScopedConfig is supposed to provide the settings of the Policies service config at
     * {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.services.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DittoPoliciesConfig of(final ScopedConfig dittoScopedConfig) {
        return new DittoPoliciesConfig(DittoServiceWithMongoDbConfig.of(dittoScopedConfig, CONFIG_PATH),
                DefaultScopedConfig.newInstance(dittoScopedConfig, CONFIG_PATH));
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
    public MongoDbConfig getMongoDbConfig() {
        return dittoServiceConfig.getMongoDbConfig();
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

}
