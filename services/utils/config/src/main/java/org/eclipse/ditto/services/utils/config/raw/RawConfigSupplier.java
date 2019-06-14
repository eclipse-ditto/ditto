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
package org.eclipse.ditto.services.utils.config.raw;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.function.Supplier;

import javax.annotation.concurrent.Immutable;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Determines the {@link Config} to use based on the environment we are running in.
 * Applies a simple logic:
 * <ul>
 * <li>
 *     If environment variable {@value ServiceSpecificEnvironmentConfigSupplier#CF_VCAP_SERVICES_ENV_VARIABLE_NAME}
 *     is present - CloudFoundry profile - loading "resourceBasename{@code -cloud}" config.
 * </li>
 * <li>
 *     If environment variable {@value ServiceSpecificEnvironmentConfigSupplier#HOSTING_ENVIRONMENT_ENV_VARIABLE_NAME}
 *     is set to "{@code docker}" - Docker profile - loading "resourceBasename{@code -docker}" config.
 * </li>
 * <li>
 *     If nothing else is detected - Development profile - loading "resourceBasename{@code -dev}" config.
 * </li>
 * </ul>
 */
@Immutable
public final class RawConfigSupplier implements Supplier<Config> {

    private static final String DITTO_BASE_CONFIG_NAME = "ditto-service-base";

    private final String serviceName;

    private RawConfigSupplier(final String theServiceName) {
        serviceName = theServiceName;
    }

    /**
     * Returns an instance of {@code RawConfigSupplier} for the given service name.
     *
     * @param serviceName the name of the service to get the Config for.
     * @return the instance.
     * @throws NullPointerException if {@code serviceName} is {@code null}.
     */
    public static RawConfigSupplier of(final String serviceName) {
        return new RawConfigSupplier(checkNotNull(serviceName, "service name"));
    }

    @Override
    public Config get() {
        /*
         * 1. Service specific environment config (e. g. concierge-docker.conf)
         * 2. Service specific base config (e. g. concierge.conf)
         * 3. Common Ditto services config (ditto-service-base.conf)
         */
        final Config serviceSpecificEnvironmentConfig = getServiceSpecificEnvironmentConfig();

        final Config configWithFallbacks = serviceSpecificEnvironmentConfig
                .withFallback(getServiceSpecificBaseConfig())
                .withFallback(getCommonDittoServicesConfig())
                .resolve();

        return ConfigFactory.load(configWithFallbacks);
    }

    private Config getServiceSpecificEnvironmentConfig() {
        final Supplier<Config> configSupplier = ServiceSpecificEnvironmentConfigSupplier.of(serviceName);
        return configSupplier.get();
    }

    private Config getServiceSpecificBaseConfig() {
        return ConfigFactory.parseResourcesAnySyntax(serviceName);
    }

    private static Config getCommonDittoServicesConfig() {
        return ConfigFactory.parseResourcesAnySyntax(DITTO_BASE_CONFIG_NAME);
    }

}
