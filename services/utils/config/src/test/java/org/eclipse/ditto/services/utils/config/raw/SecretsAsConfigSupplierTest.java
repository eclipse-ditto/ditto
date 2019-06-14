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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueFactory;

/**
 * Unit test for {@link org.eclipse.ditto.services.utils.config.raw.SecretsAsConfigSupplier}.
 */
public final class SecretsAsConfigSupplierTest {

    private static final String KNOWN_SECRETS_DIR_PATH = "/secrets/";
    private static final String KNOWN_EMPTY_DIR_PATH = "/empty/";

    @Rule
    public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

    private static Path secretsDirPath;

    @BeforeClass
    public static void initTestFixture() throws URISyntaxException {
        final URL secretsDirUrl = SecretsAsConfigSupplierTest.class.getResource(KNOWN_SECRETS_DIR_PATH);
        secretsDirPath = Paths.get(secretsDirUrl.toURI());
    }

    @Before
    public void setSecretsDirPathEnvironmentVariable() {
        environmentVariables.set(SecretsAsConfigSupplier.SECRETS_DIR_PATH_ENV_VARIABLE_NAME, secretsDirPath.toString());
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(SecretsAsConfigSupplier.class,
                areImmutable(),
                provided(Path.class).isAlsoImmutable());
    }

    @Test
    public void useDefaultSecretsDirPathIfNotSetByEnvVariable() {
        final Path expectedPath = Paths.get("/run/secrets");

        environmentVariables.clear(SecretsAsConfigSupplier.SECRETS_DIR_PATH_ENV_VARIABLE_NAME);
        final SecretsAsConfigSupplier underTest = SecretsAsConfigSupplier.getInstance();

        assertThat(underTest.getSecretsDirPath()).isEqualTo(expectedPath);
    }

    @Test
    public void useSecretsDirPathOfSystemEnvironment() {
        final Path expectedPath = secretsDirPath;

        final SecretsAsConfigSupplier underTest = SecretsAsConfigSupplier.getInstance();

        assertThat(underTest.getSecretsDirPath()).isEqualTo(expectedPath);
    }

    @Test
    public void getEmptyConfigForNotExistingDir() {
        environmentVariables.set(SecretsAsConfigSupplier.SECRETS_DIR_PATH_ENV_VARIABLE_NAME, "plumbus");
        final SecretsAsConfigSupplier underTest = SecretsAsConfigSupplier.getInstance();

        assertThat(underTest.get()).isEqualTo(ConfigFactory.empty());
    }

    @Test
    public void getEmptyConfigForEmptyDir() throws URISyntaxException {
        final Config expectedConfig = ConfigFactory.empty()
                .withValue(SecretsAsConfigSupplier.SECRETS_CONFIG_PATH,
                        ConfigValueFactory.fromMap(Collections.emptyMap()));

        final URL emptyDirParentUrl = SecretsAsConfigSupplierTest.class.getResource(".");
        final Path parentPath = Paths.get(emptyDirParentUrl.toURI());
        final Path emptyDirPath = Paths.get(parentPath.toString(), KNOWN_EMPTY_DIR_PATH);
        final File emptyDir = emptyDirPath.toFile();
        emptyDir.mkdir();

        environmentVariables.set(SecretsAsConfigSupplier.SECRETS_DIR_PATH_ENV_VARIABLE_NAME, emptyDirPath.toString());

        final SecretsAsConfigSupplier underTest = SecretsAsConfigSupplier.getInstance();

        assertThat(underTest.get()).isEqualTo(expectedConfig);
    }

    @Test
    public void getReturnsExpectedSecretsConfig() {
        final ConfigValue expectedSecretsConfig =
                ConfigValueFactory.fromMap(Collections.singletonMap("plumbus_password", "612brapples"));
        final Config expectedConfig = ConfigFactory.parseMap(
                Collections.singletonMap(SecretsAsConfigSupplier.SECRETS_CONFIG_PATH, expectedSecretsConfig));

        final SecretsAsConfigSupplier underTest = SecretsAsConfigSupplier.getInstance();

        assertThat(underTest.get()).isEqualTo(expectedConfig);
    }

}