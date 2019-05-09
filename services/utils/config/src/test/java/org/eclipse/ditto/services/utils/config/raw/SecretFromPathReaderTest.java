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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Unit test for {@link org.eclipse.ditto.services.utils.config.raw.SecretFromPathReader}.
 */
public final class SecretFromPathReaderTest {

    private static final String SECRETS_DIR = "/secrets/";
    private static final String PASSWORD_FILE_SUFFIX = "_password";

    private static final String PLUMBUS_SECRET_FILE_NAME = "plumbus" + PASSWORD_FILE_SUFFIX;
    private static final String PLUMBUS_SECRET_FILE_PATH = SECRETS_DIR + PLUMBUS_SECRET_FILE_NAME;

    private static final String EMPTY_SECRET_FILE_NAME = "empty" + PASSWORD_FILE_SUFFIX;
    private static final String EMPTY_SECRET_FILE_PATH = SECRETS_DIR + EMPTY_SECRET_FILE_NAME;

    private static Path knownPlumbusSecretPath;
    private static Path knownEmptySecretPath;

    @BeforeClass
    public static void initTestFixture() throws URISyntaxException {
        final Class<SecretFromPathReaderTest> testClass = SecretFromPathReaderTest.class;

        final URL plumbusSecretFileUrl = testClass.getResource(PLUMBUS_SECRET_FILE_PATH);
        knownPlumbusSecretPath = Paths.get(plumbusSecretFileUrl.toURI());

        final URL emptySecretFileUrl = testClass.getResource(EMPTY_SECRET_FILE_PATH);
        knownEmptySecretPath = Paths.get(emptySecretFileUrl.toURI());
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(SecretFromPathReader.class,
                areImmutable(),
                provided(Path.class).isAlsoImmutable());
    }

    @Test
    public void tryToGetInstanceWithNullPath() {
        assertThatNullPointerException()
                .isThrownBy(() -> SecretFromPathReader.of(null))
                .withMessage("The Secret path must not be null!")
                .withNoCause();
    }

    @Test
    public void getPlumbusValueReturnsExpected() {
        final String secretValue = "612brapples";
        final Secret expectedSecret = Secret.newInstance(PLUMBUS_SECRET_FILE_NAME, secretValue);
        final SecretFromPathReader underTest = SecretFromPathReader.of(knownPlumbusSecretPath);

        assertThat(underTest.get()).contains(expectedSecret);
    }

    @Test
    public void getEmptyValueReturnsEmptyOptional() {
        final SecretFromPathReader underTest = SecretFromPathReader.of(knownEmptySecretPath);

        assertThat(underTest.get()).isEmpty();
    }

}