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
package org.eclipse.ditto.services.base.config.raw;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.function.Supplier;

import javax.annotation.concurrent.Immutable;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * TODO Javadoc
 * // Ersatz fuer ConfigUtil
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

        return serviceSpecificEnvironmentConfig
                .withFallback(getServiceSpecificBaseConfig())
                .withFallback(getCommonDittoServicesConfig());
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
