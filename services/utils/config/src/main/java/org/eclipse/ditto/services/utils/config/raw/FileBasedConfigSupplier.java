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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.function.Supplier;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.services.utils.config.DittoConfigError;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Provides a {@link com.typesafe.config.Config} based on a particular file.
 */
@Immutable
final class FileBasedConfigSupplier implements Supplier<Config> {

    /**
     * Name of the system environment variable for setting the location of the VCAP services config file.
     */
    static final String HOSTING_ENV_FILE_LOCATION_ENV_VARIABLE_NAME = "HOSTING_ENVIRONMENT_FILE_LOCATION";

    private final Config initialConfig;

    private FileBasedConfigSupplier(final Config theInitialConfig) {
        initialConfig = theInitialConfig;
    }

    /**
     * Returns an instance of {@code FileBasedConfigSupplier} that provides a {@link com.typesafe.config.Config} based
     * on the file denoted by system environment variable {@value #HOSTING_ENV_FILE_LOCATION_ENV_VARIABLE_NAME}.
     *
     * @return the instance.
     * @throws DittoConfigError if the value of the system environment variable
     * {@value #HOSTING_ENV_FILE_LOCATION_ENV_VARIABLE_NAME} was either not set at all or did not denote an existing
     * file.
     */
    static FileBasedConfigSupplier forConfiguredConfigFile() {
        final Config initialConfig = ConfigFactory.parseFileAnySyntax(tryToGetConfigFile(getConfigFileLocation()));
        return new FileBasedConfigSupplier(initialConfig);
    }

    private static String getConfigFileLocation() {
        final String result = System.getenv(HOSTING_ENV_FILE_LOCATION_ENV_VARIABLE_NAME);
        if (null == result) {
            final String msgPattern = "System environment variable <{0}> is not set!";
            throw new DittoConfigError(MessageFormat.format(msgPattern, HOSTING_ENV_FILE_LOCATION_ENV_VARIABLE_NAME));
        }
        return result;
    }

    private static File tryToGetConfigFile(final String configFileLocation) {
        try {
            return getConfigFile(configFileLocation);
        } catch (final IllegalArgumentException e) {
            final String msgPattern = "Failed to get config file at <{0}>!";
            throw new DittoConfigError(MessageFormat.format(msgPattern, configFileLocation), e);
        }
    }

    private static File getConfigFile(final String configFileLocation) {
        final Path configFilePath = Paths.get(configFileLocation);
        final File result = configFilePath.toFile();
        if (!result.exists()) {
            throw new IllegalArgumentException(MessageFormat.format("<{0}> does not exist!", configFilePath));
        }
        if (!result.isFile()) {
            throw new IllegalArgumentException(MessageFormat.format("<{0}> is not a file!", configFilePath));
        }
        if (!result.canRead()) {
            throw new IllegalArgumentException(MessageFormat.format("<{0}> is not readable!", configFilePath));
        }
        return result;
    }

    /**
     * Returns an instance of {@code FileBasedConfigSupplier}.
     * The name of the config file resource is derived from the given service name.
     *
     * @param serviceName the name of the service.
     * @return the instance.
     */
    static FileBasedConfigSupplier forServiceNameBasedConfigFile(final String serviceName) {
        final Config initialConfig = ConfigFactory.parseResourcesAnySyntax(serviceName);
        return new FileBasedConfigSupplier(initialConfig);
    }

    @Override
    public Config get() {
        return getFileBasedConfig();
    }

    private Config getFileBasedConfig() {
        final VcapServicesStringSupplier vcapServicesStringSupplier = VcapServicesStringSupplier.getInstance();
        return vcapServicesStringSupplier.get()
                .map(VcapServicesStringToConfig.getInstance())
                .map(initialConfig::withFallback)
                .map(config -> config.withFallback(getSecretsAsConfig()))
                .orElseGet(() -> initialConfig.withFallback(getSecretsAsConfig()));
    }

    private static Config getSecretsAsConfig() {
        return SecretsAsConfigSupplier.getInstance().get();
    }

}
