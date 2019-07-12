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
import java.util.Optional;
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
     * Name of the system environment variable for setting the location of config file.
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
        final Config initialConfig = ConfigFactory.parseFileAnySyntax(getConfigFile(getConfigFileLocation()));
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

    private static File getConfigFile(final String configFileLocation) {
        return Paths.get(configFileLocation).toFile();
    }

    /**
     * Returns an instance of {@code FileBasedConfigSupplier}.
     *
     * @param resourceBaseName the name of the resource.
     * @return the instance.
     */
    static FileBasedConfigSupplier fromResource(final String resourceBaseName) {
        final Config initialConfig = ConfigFactory.parseResourcesAnySyntax(resourceBaseName);
        return new FileBasedConfigSupplier(initialConfig);
    }

    @Override
    public Config get() {
        return getFileBasedConfig();
    }

    private Config getFileBasedConfig() {
        final VcapServicesStringSupplier vcapServicesStringSupplier = VcapServicesStringSupplier.getInstance();
        final Optional<String> vcapJsonContentOptional = vcapServicesStringSupplier.get();

        if (vcapJsonContentOptional.isPresent()) {
            final Config vcapConfig = VcapServicesStringToConfig.getInstance().apply(vcapJsonContentOptional.get());
            return initialConfig.withFallback(vcapConfig).withFallback(getSecretsAsConfig());
        } else {
            return initialConfig.withFallback(getSecretsAsConfig());
        }
    }

    private Config getSecretsAsConfig() {
        return SecretsAsConfigSupplier.getInstance(initialConfig).get();
    }

}
