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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import javax.annotation.concurrent.Immutable;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValue;

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

    private static final String SECRETS_DIR_PATH_DEFAULT = "/run/secrets";

    private final Path secretsDirPath;
    private final Config secretsConfig;

    private SecretsAsConfigSupplier(final Path theSecretsDirPath, final Config theSecretsConfig) {
        secretsDirPath = theSecretsDirPath;
        secretsConfig = theSecretsConfig.resolve();
    }

    /**
     * Returns an instance of {@code SecretsAsConfigSupplier}.
     *
     * @return the instance.
     */
    static SecretsAsConfigSupplier getInstance(final Config initialConfig) {
        return new SecretsAsConfigSupplier(determineSecretsDirPath(), getSecretsConfig(initialConfig));
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
        return tryToGetSecretsFromFileSystemAsConfigObject().atKey(SECRETS_CONFIG_PATH);
    }

    private Config tryToGetSecretsFromFileSystemAsConfigObject() {
        final Map<String, ConfigValue> secrets = new HashMap<>();
        secretsConfig.root().keySet().forEach(secretEntryKey -> {
            final Config secretEntry = secretsConfig.getConfig(secretEntryKey);
            final String secretName = secretEntry.getString(Secret.SECRET_NAME);
            getSecretValueFromFileSystem(secretName).ifPresent(secret ->
                    secrets.put(secretEntryKey, secret.toConfig().root()));
        });
        return ConfigFactory.parseMap(secrets);
    }

    private Optional<Secret> getSecretValueFromFileSystem(final String secretName) {
        final Path secretPath = secretsDirPath.resolve(secretName);
        final SecretFromPathReader secretFromPathReader = SecretFromPathReader.of(secretName, secretPath);
        return secretFromPathReader.get();
    }

    /**
     * Returns the path of the secrets directory that is used by this object.
     *
     * @return the secrets directory path.
     */
    Path getSecretsDirPath() {
        return secretsDirPath;
    }

    private static Config getSecretsConfig(final Config initialConfig) {
        return initialConfig.hasPath(SECRETS_CONFIG_PATH)
                ? initialConfig.getConfig(SECRETS_CONFIG_PATH)
                : ConfigFactory.empty();
    }

}
