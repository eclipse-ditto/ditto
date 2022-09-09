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
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.function.Supplier;

import org.eclipse.ditto.json.JsonObject;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigValueType;

/**
 * Unit test for {@link VcapServicesStringToConfig}.
 */
public final class VcapServicesStringToConfigTest {

    @ClassRule
    public static final EnvironmentVariables ENVIRONMENT_VARIABLES = new EnvironmentVariables();

    private static final String KNOWN_CONFIG_FILE_NAME = "/vcap_services_test.json";

    private static String vcapServicesString;

    @BeforeClass
    public static void initTestFixture() throws URISyntaxException {
        final URL vcapConfigFileUrl = VcapServicesStringSupplierTest.class.getResource(KNOWN_CONFIG_FILE_NAME);
        final Path vcapServicesFilePath = Paths.get(vcapConfigFileUrl.toURI());
        final Supplier<Optional<String>> vcapServicesStringSplr = VcapServicesStringSupplier.of(vcapServicesFilePath);
        vcapServicesString = vcapServicesStringSplr.get().orElseThrow(IllegalStateException::new);

        final JsonObject knownAliases = JsonObject.newBuilder()
                .set("vcap.mongodb", "vcap.MongoDB-Service.ditto-mongodb-staging")
                .set("vcap.permissions", "vcap.permissions-service.ditto-permissions-staging")
                .set("vcap.usage", "vcap.usage-server.ditto-usage-staging")
                .build();
        ENVIRONMENT_VARIABLES.set(AliasesAppender.CONFIG_ALIASES_ENV_VARIABLE_NAME, knownAliases.toString());
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(VcapServicesStringToConfig.class, areImmutable());
    }

    @Test
    public void getConfigForVcapServicesStringReturnsExpected() {
        final VcapServicesStringToConfig underTest = VcapServicesStringToConfig.getInstance();

        final Config actual = underTest.apply(vcapServicesString);

        assertThat(actual.hasPath(VcapServicesStringToConfig.VCAP_CONFIG_PATH)).isTrue();
        assertThat(actual.getValue(VcapServicesStringToConfig.VCAP_CONFIG_PATH)).satisfies(vcapConfig -> {
            assertThat(vcapConfig.valueType()).isEqualTo(ConfigValueType.OBJECT);
            assertThat((ConfigObject) vcapConfig).containsOnlyKeys("MongoDB-Service", "mongodb", "permissions-service",
                    "permissions", "usage-server", "usage");
        });
    }

}
