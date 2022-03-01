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
import static org.eclipse.ditto.internal.utils.config.raw.AliasesAppender.CONFIG_ALIASES_ENV_VARIABLE_NAME;
import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.junit.*;
import org.junit.contrib.java.lang.system.EnvironmentVariables;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Unit test for {@link AliasesAppender}.
 */
public final class AliasesAppenderTest {

    private static JsonObject knownAliases;

    @Rule
    public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

    @BeforeClass
    public static void initTestFixture() {
        knownAliases = JsonObject.newBuilder()
                .set("vcap.mongodb", "vcap.MongoDB-Service.ditto-mongodb-staging")
                .set("vcap.permissions", "vcap.permissions-service.ditto-permissions-staging")
                .set("vcap.usage-server", "vcap.usage-server.ditto-usage-staging")
                .build();
    }

    @Before
    public void initEnvironmentVariables() {
        environmentVariables.set(CONFIG_ALIASES_ENV_VARIABLE_NAME, knownAliases.toString());
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(AliasesAppender.class,
                areImmutable(),
                assumingFields("systemConfigAliases").areSafelyCopiedUnmodifiableCollectionsWithImmutableElements());
    }

    @Test
    public void appendAliasesConfigWhenSystemEnvironmentVariableIsNotSet() {
        environmentVariables.clear(CONFIG_ALIASES_ENV_VARIABLE_NAME);
        final Config vcapConfig = ConfigFactory.empty();

        final AliasesAppender underTest = AliasesAppender.getInstance();

        assertThat(underTest.apply(vcapConfig)).isEqualTo(vcapConfig);
    }

    @Test
    public void appendAliasesConfigWhenSystemEnvironmentVariableIsEmptyString() {
        environmentVariables.set(CONFIG_ALIASES_ENV_VARIABLE_NAME, "");
        final Config vcapConfig = ConfigFactory.empty();

        final AliasesAppender underTest = AliasesAppender.getInstance();

        assertThat(underTest.apply(vcapConfig)).isEqualTo(vcapConfig);
    }

    @Test
    public void appendAliasesWorksAsExpected() {
        final JsonObject originalConfigJson = JsonObject.newBuilder()
                .set("vcap", JsonObject.newBuilder()
                        .set("MongoDB-Service", JsonObject.newBuilder()
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
                                .build())
                        .build())
                .build();

        final Config vcapConfig = ConfigFactory.parseString(originalConfigJson.toString());

        final JsonObject aliasedConfigJson = JsonObject.newBuilder()
                .set("vcap", JsonObject.newBuilder()
                        .set("mongodb", originalConfigJson.getValue("/vcap/MongoDB-Service/ditto-mongodb-staging")
                                .orElseGet(JsonValue::nullLiteral))
                        .build())
                .build();
        final Config expected = vcapConfig.withFallback(ConfigFactory.parseString(aliasedConfigJson.toString()));

        final AliasesAppender underTest = AliasesAppender.getInstance();
        final Config actual = underTest.apply(vcapConfig);

        assertThat(actual).isEqualTo(expected);
    }

}
