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
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.junit.*;
import org.junit.contrib.java.lang.system.EnvironmentVariables;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValue;

/**
 * Unit test for {@link VcapServicesStringParser}.
 */
public final class VcapServicesStringParserTest {

    @ClassRule
    public static final EnvironmentVariables ENVIRONMENT_VARIABLES = new EnvironmentVariables();

    private static final String KNOWN_CONFIG_FILE_NAME = "/vcap_services_test.json";

    private static String knownSystemVcapServicesString;

    private VcapServicesStringParser underTest;

    @BeforeClass
    public static void initTestFixture() throws URISyntaxException {
        final URL vcapConfigFileUrl = VcapServicesStringParserTest.class.getResource(KNOWN_CONFIG_FILE_NAME);
        final Path vcapConfigFilePath = Paths.get(vcapConfigFileUrl.toURI());
        ENVIRONMENT_VARIABLES.set(VcapServicesStringSupplier.VCAP_LOCATION_ENV_VARIABLE_NAME,
                vcapConfigFilePath.toString());

        final VcapServicesStringSupplier vcapConfigStringSupplier = VcapServicesStringSupplier.getInstance().get();
        knownSystemVcapServicesString = vcapConfigStringSupplier.get().orElseThrow(IllegalStateException::new);
    }

    @Before
    public void initUnderTest() {
        underTest = VcapServicesStringParser.getInstance();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(VcapServicesStringParser.class, areImmutable());
    }

    @Test
    public void tryToParseNullString() {
        assertThatNullPointerException()
                .isThrownBy(() -> underTest.apply(null))
                .withMessage("The system VCAP services string must not be null!")
                .withNoCause();
    }

    @Test
    public void parseEmptyString() {
        final Config actual = underTest.apply("");

        assertThat(actual.isEmpty()).isTrue();
    }

    @Test
    public void getReturnsExpected() {
        // MongoDB config acts as sample.
        final JsonObject expectedMongoDbServiceConfigJsonObject = JsonObject.newBuilder()
                .set("ditto-mongodb-staging", JsonObject.newBuilder()
                        .set("binding_name", JsonValue.nullLiteral())
                        .set("credentials", JsonObject.newBuilder()
                                .set("readonly", false)
                                .set("replicaset", "stretched-0815")
                                .build())
                        .set("instance_name", "ditto-mongodb-staging")
                        .set("label", "MongoDB-Service")
                        .set("name", "ditto-mongodb-staging")
                        .set("plan", "Database on Dedicated Replica Set - Stretched")
                        .set("provider", JsonValue.nullLiteral())
                        .set("syslog_drain_url", JsonValue.nullLiteral())
                        .set("tags", JsonArray.newBuilder()
                                .add("mongodb", "mongo", "database", "db", "mongoose")
                                .build())
                        .set("volume_mounts", JsonArray.empty())
                        .build())
                .build();

        final ConfigValue expectedMongoDbServiceConfig =
                ConfigFactory.parseString(expectedMongoDbServiceConfigJsonObject.toString()).root();

        final Config actual = underTest.apply(knownSystemVcapServicesString);

        assertThat(actual.getValue("MongoDB-Service")).isEqualTo(expectedMongoDbServiceConfig);
    }

}
