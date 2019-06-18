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
import static org.eclipse.ditto.services.utils.config.raw.FileBasedConfigSupplier.HOSTING_ENV_FILE_LOCATION_ENV_VARIABLE_NAME;
import static org.eclipse.ditto.services.utils.config.raw.ServiceSpecificEnvironmentConfigSupplier.CF_VCAP_SERVICES_ENV_VARIABLE_NAME;
import static org.eclipse.ditto.services.utils.config.raw.ServiceSpecificEnvironmentConfigSupplier.HOSTING_ENVIRONMENT_ENV_VARIABLE_NAME;
import static org.eclipse.ditto.services.utils.config.raw.VcapServicesStringSupplier.VCAP_LOCATION_ENV_VARIABLE_NAME;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.eclipse.ditto.json.JsonObject;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;

import com.typesafe.config.Config;

/**
 * Unit test for {@link ServiceSpecificEnvironmentConfigSupplier}.
 */
public final class ServiceSpecificEnvironmentConfigSupplierTest {

    private static final String SERVICE_NAME = "test-service";
    private static final String KNOWN_CONFIG_FILE_NAME = "/vcap_services_test.json";

    private static Path vcapServicesFilePath;
    private static JsonObject vcapServices;

    @Rule
    public EnvironmentVariables environmentVariables = new EnvironmentVariables();

    @BeforeClass
    public static void initTestFixture() throws URISyntaxException {
        final URL vcapConfigFileUrl = VcapServicesStringSupplierTest.class.getResource(KNOWN_CONFIG_FILE_NAME);
        vcapServicesFilePath = Paths.get(vcapConfigFileUrl.toURI());
        final VcapServicesStringSupplier vcapServicesStringSplr = VcapServicesStringSupplier.of(vcapServicesFilePath);
        vcapServices = JsonObject.of(vcapServicesStringSplr.get().orElseThrow(IllegalStateException::new));
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ServiceSpecificEnvironmentConfigSupplier.class, areImmutable());
    }

    @Test
    public void hostingEnvironmentIsDevelopmentIfSystemVariableIsNotSet() {
        environmentVariables.clear(HOSTING_ENVIRONMENT_ENV_VARIABLE_NAME);
        final ServiceSpecificEnvironmentConfigSupplier underTest =
                ServiceSpecificEnvironmentConfigSupplier.of(SERVICE_NAME);

        final Config actualConfig = underTest.get();

        assertThat(actualConfig.getIsNull(HostingEnvironment.CONFIG_PATH)).isTrue();
    }

    @Test
    public void hostingEnvironmentIsCloudNativeIfVcapServicesIsSet() {
        environmentVariables.set(CF_VCAP_SERVICES_ENV_VARIABLE_NAME, vcapServices.toString());

        final ServiceSpecificEnvironmentConfigSupplier underTest =
                ServiceSpecificEnvironmentConfigSupplier.of(SERVICE_NAME);

        final Config actualConfig = underTest.get();

        assertThat(actualConfig.getIsNull(HostingEnvironment.CONFIG_PATH)).isTrue();
    }

    @Test
    public void hostingEnvironmentIsDockerIfSet() {
        environmentVariables.set(HOSTING_ENVIRONMENT_ENV_VARIABLE_NAME, "docker");

        final ServiceSpecificEnvironmentConfigSupplier underTest =
                ServiceSpecificEnvironmentConfigSupplier.of(SERVICE_NAME);

        final Config actualConfig = underTest.get();

        assertThat(actualConfig.getString(HostingEnvironment.CONFIG_PATH)).isEqualTo("docker");
    }

    @Test
    public void hostingEnvironmentIsFilebasedIfSet() {
        environmentVariables.set(HOSTING_ENVIRONMENT_ENV_VARIABLE_NAME, "filebased");
        environmentVariables.set(HOSTING_ENV_FILE_LOCATION_ENV_VARIABLE_NAME, vcapServicesFilePath.toString());
        environmentVariables.set(VCAP_LOCATION_ENV_VARIABLE_NAME, vcapServicesFilePath.toString());

        final ServiceSpecificEnvironmentConfigSupplier underTest =
                ServiceSpecificEnvironmentConfigSupplier.of(SERVICE_NAME);

        final Config actualConfig = underTest.get();

        assertThat(actualConfig.getString(HostingEnvironment.CONFIG_PATH)).isEqualTo("filebased");
    }

}