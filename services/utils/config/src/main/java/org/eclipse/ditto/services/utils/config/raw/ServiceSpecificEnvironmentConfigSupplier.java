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
package org.eclipse.ditto.services.utils.config.raw;

import java.util.function.Supplier;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueFactory;

/**
 * TODO Javadoc
 */
@Immutable
final class ServiceSpecificEnvironmentConfigSupplier implements Supplier<Config> {

    /**
     * Name of the system environment variable for setting the hosting environment.
     */
    static final String HOSTING_ENVIRONMENT_ENV_VARIABLE_NAME = "HOSTING_ENVIRONMENT";

    /**
     * Name of the system environment variable for setting the VCAP services configuration.
     * The value of the variable is supposed to be a JSON object string.
     */
    static final String CF_VCAP_SERVICES_ENV_VARIABLE_NAME = "VCAP_SERVICES";

    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceSpecificEnvironmentConfigSupplier.class);

    private final String serviceName;
    @Nullable private final String systemHostingEnvironment;
    @Nullable private final String systemVcapServices;

    private ServiceSpecificEnvironmentConfigSupplier(final String theServiceName,
            @Nullable final String theSystemHostingEnvironment, @Nullable final String theSystemVcapServices) {

        serviceName = theServiceName;
        systemHostingEnvironment = theSystemHostingEnvironment;
        systemVcapServices = theSystemVcapServices;
    }

    /**
     * Returns an instance of {@code ServiceSpecificEnvironmentConfigSupplier}.
     *
     * @param serviceName the name of the service to get the config for.
     * @return the instance.
     */
    static ServiceSpecificEnvironmentConfigSupplier of(final String serviceName) {
        return new ServiceSpecificEnvironmentConfigSupplier(serviceName, getSystemHostingEnvironmentOrNull(),
                getSystemVcapServicesOrNull());
    }

    @Nullable
    private static String getSystemHostingEnvironmentOrNull() {
        return System.getenv(HOSTING_ENVIRONMENT_ENV_VARIABLE_NAME);
    }

    @Nullable
    private static String getSystemVcapServicesOrNull() {
        return System.getenv(CF_VCAP_SERVICES_ENV_VARIABLE_NAME);
    }

    @Override
    public Config get() {
        final Config result;

        switch (determineHostingEnvironment()) {
            case CLOUD_NATIVE:
                result = getCloudNativeConfig();
                break;
            case DOCKER:
                result = getDockerConfig();
                break;
            case FILE_BASED_CONFIGURED:
                result = getConfiguredFileBasedConfig();
                break;
            case FILE_BASED_SERVICE_NAME:
                result = getServiceNameBasedFileBasedConfig();
                break;
            case DEVELOPMENT:
                result = getDevelopmentConfig();
                break;
            default:
                result = ConfigFactory.empty();
                break;
        }

        return result;
    }

    private HostingEnvironment determineHostingEnvironment() {
        if (null != systemVcapServices) {
            LOGGER.info("Running with 'CloudFoundry' config.");
            return HostingEnvironment.CLOUD_NATIVE;
        }

        final HostingEnvironment result;
        if ("docker".equalsIgnoreCase(systemHostingEnvironment)) {
            LOGGER.info("Running with 'Docker' config.");
            result = HostingEnvironment.DOCKER;
        } else if ("filebased".equalsIgnoreCase(systemHostingEnvironment)) {
            result = HostingEnvironment.FILE_BASED_CONFIGURED;
        } else if (null != systemHostingEnvironment && !systemHostingEnvironment.isEmpty()) {
            result = HostingEnvironment.FILE_BASED_SERVICE_NAME;
        } else {
            LOGGER.info("Docker environment was not detected. Assuming running in 'Development' environment.");
            result = HostingEnvironment.DEVELOPMENT;
        }

        return result;
    }

    private Config getCloudNativeConfig() {
        final VcapServicesStringToConfig vcapServicesStringToConfig = VcapServicesStringToConfig.getInstance();
        return withHostingEnvironmentValue(getConfigFromResource(HostingEnvironment.CLOUD_NATIVE))
                .withFallback(vcapServicesStringToConfig.apply(systemVcapServices));
    }

    private Config getDockerConfig() {
        return withHostingEnvironmentValue(getConfigFromResource(HostingEnvironment.DOCKER));
    }

    private Config getConfiguredFileBasedConfig() {
        final Supplier<Config> configSupplier = FileBasedConfigSupplier.forConfiguredConfigFile();
        return withHostingEnvironmentValue(configSupplier.get());
    }

    private Config getServiceNameBasedFileBasedConfig() {
        final Supplier<Config> configSupplier = FileBasedConfigSupplier.forServiceNameBasedConfigFile(serviceName);
        return withHostingEnvironmentValue(configSupplier.get());
    }

    private Config getDevelopmentConfig() {
        return withHostingEnvironmentValue(getConfigFromResource(HostingEnvironment.DEVELOPMENT));
    }

    private Config getConfigFromResource(final HostingEnvironment hostingEnvironment) {
        return ConfigFactory.parseResourcesAnySyntax(getResourceBasename(hostingEnvironment));
    }

    private String getResourceBasename(final HostingEnvironment hostingEnvironment) {
        return serviceName + hostingEnvironment.getConfigFileSuffix();
    }

    private Config withHostingEnvironmentValue(final Config config) {
        return config.withValue(HostingEnvironment.CONFIG_PATH, getHostingEnvironmentConfig());
    }

    private ConfigValue getHostingEnvironmentConfig() {
        return ConfigValueFactory.fromAnyRef(systemHostingEnvironment);
    }

}
