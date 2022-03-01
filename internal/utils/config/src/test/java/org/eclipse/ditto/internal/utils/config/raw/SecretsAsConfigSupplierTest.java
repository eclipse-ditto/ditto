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

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.internal.utils.config.raw.SecretsAsConfigSupplier.SECRETS_CONFIG_PATH;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.*;
import org.junit.contrib.java.lang.system.EnvironmentVariables;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Unit test for {@link SecretsAsConfigSupplier}.
 */
public final class SecretsAsConfigSupplierTest {

    private static final String KNOWN_SECRETS_DIR_PATH = "/secrets/";
    private static final String KNOWN_EMPTY_DIR_PATH = "/empty/";

    @Rule
    public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

    private static Path secretsDirPath;
    private static Config initialConfig;

    @BeforeClass
    public static void initTestFixture() throws URISyntaxException {
        final URL secretsDirUrl = SecretsAsConfigSupplierTest.class.getResource(KNOWN_SECRETS_DIR_PATH);
        secretsDirPath = Paths.get(secretsDirUrl.toURI());
        initialConfig = ConfigFactory.load("test");
    }

    @Before
    public void setSecretsDirPathEnvironmentVariable() {
        environmentVariables.set(SecretsAsConfigSupplier.SECRETS_DIR_PATH_ENV_VARIABLE_NAME, secretsDirPath.toString());
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(SecretsAsConfigSupplier.class,
                areImmutable(),
                provided(Path.class, Config.class).areAlsoImmutable());
    }

    @Test
    public void useDefaultSecretsDirPathIfNotSetByEnvVariable() {
        final Path expectedPath = Paths.get("/run/secrets");

        environmentVariables.clear(SecretsAsConfigSupplier.SECRETS_DIR_PATH_ENV_VARIABLE_NAME);
        final SecretsAsConfigSupplier underTest = SecretsAsConfigSupplier.getInstance(initialConfig);

        assertThat(underTest.getSecretsDirPath()).isEqualTo(expectedPath);
    }

    @Test
    public void useSecretsDirPathOfSystemEnvironment() {
        final Path expectedPath = secretsDirPath;

        final SecretsAsConfigSupplier underTest = SecretsAsConfigSupplier.getInstance(initialConfig);

        assertThat(underTest.getSecretsDirPath()).isEqualTo(expectedPath);
    }

    @Test
    public void getEmptyConfigForNotExistingDir() {
        environmentVariables.set(SecretsAsConfigSupplier.SECRETS_DIR_PATH_ENV_VARIABLE_NAME, "plumbus");
        final SecretsAsConfigSupplier underTest = SecretsAsConfigSupplier.getInstance(initialConfig);

        assertThat(underTest.get()).isEqualTo(ConfigFactory.empty().atKey(SECRETS_CONFIG_PATH));
    }

    @Test
    public void getEmptyConfigForEmptyDir() throws URISyntaxException {
        final Config expectedConfig = ConfigFactory.empty().atKey(SECRETS_CONFIG_PATH);

        final URL emptyDirParentUrl = SecretsAsConfigSupplierTest.class.getResource(".");
        final Path parentPath = Paths.get(emptyDirParentUrl.toURI());
        final Path emptyDirPath = Paths.get(parentPath.toString(), KNOWN_EMPTY_DIR_PATH);
        final File emptyDir = emptyDirPath.toFile();
        emptyDir.mkdir();

        environmentVariables.set(SecretsAsConfigSupplier.SECRETS_DIR_PATH_ENV_VARIABLE_NAME, emptyDirPath.toString());

        final SecretsAsConfigSupplier underTest = SecretsAsConfigSupplier.getInstance(initialConfig);

        assertThat(underTest.get()).isEqualTo(expectedConfig);
    }

    @Test
    public void getReturnsExpectedSecretsConfig() {
        final Config expectedConfig =
                ConfigFactory.parseString("secrets.test{name=plumbus_password,value=612brapples}");

        final SecretsAsConfigSupplier underTest = SecretsAsConfigSupplier.getInstance(initialConfig);

        assertThat(underTest.get()).isEqualTo(expectedConfig);
    }

}
