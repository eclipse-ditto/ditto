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
package org.eclipse.ditto.internal.utils.config.raw;

import java.io.File;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.function.Supplier;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.internal.utils.config.DittoConfigError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;

/**
 * Supplier for Typesafe {@link Config} based on the environment the service runs in.
 * Distinguishes between:
 * <ul>
 * <li>{@link HostingEnvironment#PRODUCTION}</li>
 * <li>{@link HostingEnvironment#FILE_BASED}</li>
 * <li>{@link HostingEnvironment#DEVELOPMENT}</li>
 * </ul>
 */
@Immutable
final class ServiceSpecificEnvironmentConfigSupplier implements Supplier<Config> {

    /**
     * Name of the system environment variable for setting the location of config file.
     */
    private static final String HOSTING_ENV_FILE_LOCATION_ENV_VARIABLE_NAME = "HOSTING_ENVIRONMENT_FILE_LOCATION";

    /**
     * Name of the system environment variable for setting the hosting environment.
     */
    private static final String HOSTING_ENVIRONMENT_ENV_VARIABLE_NAME = "HOSTING_ENVIRONMENT";
    private static final String CONFIG_PATH = "hosting.environment";

    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceSpecificEnvironmentConfigSupplier.class);

    private final String serviceName;
    @Nullable private final String configuredEnvironment;
    private final HostingEnvironment environment;

    private ServiceSpecificEnvironmentConfigSupplier(final String serviceName) {
        this.serviceName = serviceName;
        configuredEnvironment = System.getenv(HOSTING_ENVIRONMENT_ENV_VARIABLE_NAME);
        environment = HostingEnvironment.fromHostingEnvironmentName(configuredEnvironment);
    }

    /**
     * Returns an instance of {@code ServiceSpecificEnvironmentConfigSupplier}.
     *
     * @param serviceName the name of the service to get the config for.
     * @return the instance.
     */
    static ServiceSpecificEnvironmentConfigSupplier of(final String serviceName) {
        return new ServiceSpecificEnvironmentConfigSupplier(serviceName);
    }

    @Override
    public Config get() {
        LOGGER.info("Running in <{}> environment.", environment);
        return withHostingEnvironmentValue(fromHostingEnvironment());
    }

    private Config fromHostingEnvironment() {
        switch (environment) {
            case FILE_BASED:
                return getFileBasedConfig();
            case DEVELOPMENT:
                return getDevelopmentConfig();
            default:
                return ConfigFactory.empty();
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
        final String configFileLocation = System.getenv(HOSTING_ENV_FILE_LOCATION_ENV_VARIABLE_NAME);
        if (null == configFileLocation) {
            final String msgPattern = "System environment variable <{0}> is not set!";
            throw new DittoConfigError(MessageFormat.format(msgPattern, HOSTING_ENV_FILE_LOCATION_ENV_VARIABLE_NAME));
        }
        final File configFile = Paths.get(configFileLocation).toFile();
        return DittoConfigFactory.fromFile(configFile);
    }

    private Config getDevelopmentConfig() {
        return DittoConfigFactory.fromResource(serviceName + "-dev");
    }

    private Config withHostingEnvironmentValue(final Config config) {
        return config.withValue(CONFIG_PATH, ConfigValueFactory.fromAnyRef(configuredEnvironment));
    }

}
