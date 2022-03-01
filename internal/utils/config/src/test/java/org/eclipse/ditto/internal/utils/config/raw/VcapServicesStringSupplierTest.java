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
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.eclipse.ditto.internal.utils.config.raw.VcapServicesStringSupplier.VCAP_LOCATION_ENV_VARIABLE_NAME;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.eclipse.ditto.internal.utils.config.DittoConfigError;
import org.junit.*;
import org.junit.contrib.java.lang.system.EnvironmentVariables;

/**
 * Unit test for {@link VcapServicesStringSupplier}.
 */
public final class VcapServicesStringSupplierTest {

    private static final String KNOWN_CONFIG_FILE_NAME = "/vcap_services_test.json";

    private static Path vcapServicesFilePath;

    @Rule
    public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

    @BeforeClass
    public static void initTestFixture() throws URISyntaxException {
        final URL vcapConfigFileUrl = VcapServicesStringSupplierTest.class.getResource(KNOWN_CONFIG_FILE_NAME);
        vcapServicesFilePath = Paths.get(vcapConfigFileUrl.toURI());
    }

    @Before
    public void setVcapConfigFileEnvironmentVariable() {
        environmentVariables.set(VCAP_LOCATION_ENV_VARIABLE_NAME, vcapServicesFilePath.toString());
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(VcapServicesStringSupplier.class,
                areImmutable(),
                provided(Path.class).isAlsoImmutable());
    }

    @Test
    public void tryToGetInstanceWhenVcapLocationSystemEnvWasNotSet() {
        environmentVariables.clear(VCAP_LOCATION_ENV_VARIABLE_NAME);
        assertThat(VcapServicesStringSupplier.getInstance()).isNotPresent();
    }

    @Test
    public void tryToGetInstanceWithNullPath() {
        assertThatNullPointerException()
                .isThrownBy(() -> VcapServicesStringSupplier.of(null))
                .withMessage("The path of VCAP services (JSON) file must not be null!")
                .withNoCause();
    }

    @Test
    public void getReturnsExpected() {
        final VcapServicesStringSupplier underTest = VcapServicesStringSupplier.getInstance().get();

        assertThat(underTest.get()).hasValueSatisfying(s -> assertThat(s).contains("Krimkram"));
    }

    @Test
    public void tryToReadNonExistingFile() throws URISyntaxException {
        final String notExistingFileName = "/cake.conf";
        final URL parentDirUrl = VcapServicesStringSupplierTest.class.getResource(".");
        final Path parentDirPath = Paths.get(parentDirUrl.toURI());
        final Path notExistingFilePath = Paths.get(parentDirPath.toString(), notExistingFileName);
        environmentVariables.set(VCAP_LOCATION_ENV_VARIABLE_NAME, notExistingFilePath.toString());

        final VcapServicesStringSupplier underTest = VcapServicesStringSupplier.getInstance().get();

        assertThatExceptionOfType(DittoConfigError.class)
                .isThrownBy(underTest::get)
                .withMessage("Failed to read VCAP services config from path <%s>!", notExistingFilePath.toString())
                .withCauseInstanceOf(IOException.class);
    }

}
