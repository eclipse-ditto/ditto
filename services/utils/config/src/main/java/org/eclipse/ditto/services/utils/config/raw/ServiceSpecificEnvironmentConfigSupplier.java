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

import java.io.File;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.function.Supplier;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.DittoConfigError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueFactory;

/**
 * Supplier for Typesafe {@link Config} based on the environment the service runs in.
 * Distinguishes between:
 * <ul>
 * <li>{@link HostingEnvironment#DOCKER}</li>
 * <li>{@link HostingEnvironment#FILE_BASED}</li>
 * <li>{@link HostingEnvironment#DEVELOPMENT}</li>
 * </ul>
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

    /**
     * Name of the system environment variable for setting the location of config file.
     */
    static final String HOSTING_ENV_FILE_LOCATION_ENV_VARIABLE_NAME = "HOSTING_ENVIRONMENT_FILE_LOCATION";

    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceSpecificEnvironmentConfigSupplier.class);

    private final String serviceName;
    private final String systemHostingEnvironment;

    private ServiceSpecificEnvironmentConfigSupplier(final String serviceName, final String systemHostingEnvironment) {
        this.serviceName = serviceName;
        this.systemHostingEnvironment = systemHostingEnvironment;
    }

    /**
     * Returns an instance of {@code ServiceSpecificEnvironmentConfigSupplier}.
     *
     * @param serviceName the name of the service to get the config for.
     * @return the instance.
     */
    static ServiceSpecificEnvironmentConfigSupplier of(final String serviceName) {
        return new ServiceSpecificEnvironmentConfigSupplier(serviceName, getSystemHostingEnvironmentOrEmpty());
    }

    private static String getSystemHostingEnvironmentOrEmpty() {
        final String hostingEnvironment = System.getenv(HOSTING_ENVIRONMENT_ENV_VARIABLE_NAME);
        return hostingEnvironment == null ? "" : hostingEnvironment;
    }

    @Override
    public Config get() {
        final HostingEnvironment hostingEnvironment = determineHostingEnvironment();
        LOGGER.info("Running in <{}> environment.", hostingEnvironment);

        switch (hostingEnvironment) {
            case FILE_BASED:
                return getFileBasedConfig();
            case DEVELOPMENT:
                return getDevelopmentConfig();
            default:
                return withHostingEnvironmentValue(ConfigFactory.empty());
        }
    }

    private HostingEnvironment determineHostingEnvironment() {
        switch (systemHostingEnvironment.toLowerCase()) {
            case "docker":
                return HostingEnvironment.DOCKER;
            case "filebased":
                return HostingEnvironment.FILE_BASED;
            default:
                return HostingEnvironment.DEVELOPMENT;
        }
    }

    /**
     * Returns a {@link com.typesafe.config.Config} based on the file denoted by system environment variable
     * {@value #HOSTING_ENV_FILE_LOCATION_ENV_VARIABLE_NAME}.
     *
     * @return the config.
     * @throws DittoConfigError if the value of the system environment variable
     * {@value #HOSTING_ENV_FILE_LOCATION_ENV_VARIABLE_NAME} was either not set at all or did not denote an existing
     * file.
     */
    private Config getFileBasedConfig() {
        final Config fileBasedConfig = DittoConfigFactory.fromFile(getConfigFile(getConfigFileLocation()));
        return withHostingEnvironmentValue(fileBasedConfig);
    }

    private Config getDevelopmentConfig() {
        return withHostingEnvironmentValue(getConfigFromResource(HostingEnvironment.DEVELOPMENT));
    }

    private Config getConfigFromResource(final HostingEnvironment hostingEnvironment) {
        return DittoConfigFactory.fromResource(getResourceBasename(hostingEnvironment));
    }

    private String getResourceBasename(final HostingEnvironment hostingEnvironment) {
        return serviceName + hostingEnvironment.getConfigFileSuffix();
    }

    private Config withHostingEnvironmentValue(final Config config) {
        return config.withValue(HostingEnvironment.CONFIG_PATH, getHostingEnvironmentConfig());
    }

    private ConfigValue getHostingEnvironmentConfig() {
        return ConfigValueFactory.fromAnyRef(systemHostingEnvironment.isEmpty() ? null : systemHostingEnvironment);
    }

    private static String getConfigFileLocation() {
        final String result = System.getenv(HOSTING_ENV_FILE_LOCATION_ENV_VARIABLE_NAME);
        if (null == result) {
            final String msgPattern = "System environment variable <{0}> is not set!";
            throw new DittoConfigError(MessageFormat.format(msgPattern, HOSTING_ENV_FILE_LOCATION_ENV_VARIABLE_NAME));
        }
        return result;
    }

    private static File getConfigFile(final String configFileLocation) {
        return Paths.get(configFileLocation).toFile();
    }

}
