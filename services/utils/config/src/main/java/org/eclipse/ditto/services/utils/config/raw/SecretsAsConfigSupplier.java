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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.concurrent.Immutable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueFactory;
import com.typesafe.config.ConfigValueType;

/**
 * Reads the secrets from files and provides them as single {@link Config} object.
 * The path to read the secrets from is provided by system environment {@link #SECRETS_DIR_PATH_ENV_VARIABLE_NAME}.
 * If the path is not set by system environment variable the default path {@value #SECRETS_DIR_PATH_DEFAULT} is used.
 */
@Immutable
final class SecretsAsConfigSupplier implements Supplier<Config> {

    /**
     * Name of the system environment variable for setting a deviant secrets directory path.
     */
    static final String SECRETS_DIR_PATH_ENV_VARIABLE_NAME = "SECRETS_DIR";

    /**
     * Path of the nested secrets config object.
     */
    static final String SECRETS_CONFIG_PATH = "secrets";

    private static final String SECRETS_DIR_PATH_DEFAULT = "/run" + "/secrets";
    private static final Logger LOGGER = LoggerFactory.getLogger(SecretsAsConfigSupplier.class);

    private final Path secretsDirPath;

    private SecretsAsConfigSupplier(final Path theSecretsDirPath) {
        secretsDirPath = theSecretsDirPath;
    }

    /**
     * Returns an instance of {@code SecretsAsConfigSupplier}.
     *
     * @return the instance.
     */
    static SecretsAsConfigSupplier getInstance() {
        return new SecretsAsConfigSupplier(determineSecretsDirPath());
    }

    private static Path determineSecretsDirPath() {
        String path = System.getenv(SECRETS_DIR_PATH_ENV_VARIABLE_NAME);
        if (null == path) {
            path = SECRETS_DIR_PATH_DEFAULT;
        }
        return Paths.get(path);
    }

    @Override
    public Config get() {
        final ConfigValue secrets = tryToGetSecretsFromFileSystemAsConfigObject();
        if (ConfigValueType.NULL != secrets.valueType()) {
            return ConfigFactory.parseMap(Collections.singletonMap(SECRETS_CONFIG_PATH, secrets));
        }
        return ConfigFactory.empty();
    }

    private ConfigValue tryToGetSecretsFromFileSystemAsConfigObject() {
        try (final Stream<Path> filesStream = Files.list(secretsDirPath)) {
            return getSecretsFromFileSystemAsConfigObject(filesStream);
        } catch (final IOException e) {
            LOGGER.warn("No secrets present at path <{}>!", secretsDirPath, e);
            return ConfigValueFactory.fromAnyRef(null);
        }
    }

    private static ConfigValue getSecretsFromFileSystemAsConfigObject(final Stream<Path> filesStream) {
        final Map<String, String> secrets = filesStream.map(SecretFromPathReader::of)
                .map(SecretFromPathReader::get)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toMap(Secret::getName, Secret::getValue));

        return ConfigValueFactory.fromMap(secrets);
    }

    /**
     * Returns the path of the secrets directory that is used by this object.
     *
     * @return the secrets directory path.
     */
    Path getSecretsDirPath() {
        return secretsDirPath;
    }

}
