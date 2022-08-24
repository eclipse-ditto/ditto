/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.persistence.mongo.config;

import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.assertj.core.api.JUnitSoftAssertions;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import com.mongodb.WriteConcern;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

/**
 * Unit test for {@link DefaultOptionsConfig}.
 */
public final class DefaultOptionsConfigTest {

    private static final String MONGODB_CONFIG_FILE_NAME = "mongodb_test.conf";

    private Config rawMongoDbConfig;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @Before
    public void initMongoDbConfig() {
        rawMongoDbConfig = ConfigFactory.parseResources(MONGODB_CONFIG_FILE_NAME).getConfig("mongodb");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultOptionsConfig.class,
                areImmutable(),
                provided(ReadPreference.class, ReadConcern.class, WriteConcern.class).areAlsoImmutable(),
                assumingFields("extraUriOptions")
                        .areSafelyCopiedUnmodifiableCollectionsWithImmutableElements());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultOptionsConfig.class)
                .suppress(Warning.NULL_FIELDS)
                .usingGetClass()
                .verify();
    }

    @Test
    public void toStringContainsExpected() {
        final DefaultOptionsConfig underTest = DefaultOptionsConfig.of(rawMongoDbConfig);

        softly.assertThat(underTest.toString()).contains(underTest.getClass().getSimpleName())
                .contains("sslEnabled", "readPreference", "readConcern", "writeConcern",
                        "retryWrites");
    }

    @Test
    public void defaultOptionsConfigContainsExactlyValuesOfResourceConfigFile() {
        final DefaultOptionsConfig underTest = DefaultOptionsConfig.of(rawMongoDbConfig);

        softly.assertThat(underTest.isSslEnabled()).isFalse();
        softly.assertThat(underTest.readPreference()).isEqualTo(ReadPreference.SECONDARY_PREFERRED);
        softly.assertThat(underTest.readConcern()).isEqualTo(ReadConcern.LINEARIZABLE);
        softly.assertThat(underTest.writeConcern()).isEqualTo(WriteConcern.W3);
        softly.assertThat(underTest.isRetryWrites()).isFalse();
    }

    @Test
    public void defaultOptionsConfigContainsExactlyFallBackValuesIfEmptyResourceConfigFile() {
        final DefaultOptionsConfig underTest = DefaultOptionsConfig.of(ConfigFactory.empty());

        softly.assertThat(underTest.isSslEnabled())
                .as(MongoDbConfig.OptionsConfig.OptionsConfigValue.SSL_ENABLED.getConfigPath())
                .isEqualTo(MongoDbConfig.OptionsConfig.OptionsConfigValue.SSL_ENABLED.getDefaultValue());
        softly.assertThat(underTest.readPreference())
                .as(MongoDbConfig.OptionsConfig.OptionsConfigValue.READ_PREFERENCE.getConfigPath())
                .isEqualTo(ReadPreference.ofReadPreference(
                        (String) MongoDbConfig.OptionsConfig.OptionsConfigValue.READ_PREFERENCE.getDefaultValue())
                        .orElseThrow());
        softly.assertThat(underTest.readConcern())
                .as(MongoDbConfig.OptionsConfig.OptionsConfigValue.READ_CONCERN.getConfigPath())
                .isEqualTo(ReadConcern.ofReadConcern(
                        (String) MongoDbConfig.OptionsConfig.OptionsConfigValue.READ_CONCERN.getDefaultValue())
                        .orElseThrow());
        softly.assertThat(underTest.writeConcern())
                .as(MongoDbConfig.OptionsConfig.OptionsConfigValue.WRITE_CONCERN.getConfigPath())
                .isEqualTo(WriteConcern.valueOf(
                        (String) MongoDbConfig.OptionsConfig.OptionsConfigValue.WRITE_CONCERN.getDefaultValue()));
        softly.assertThat(underTest.isRetryWrites())
                .as(MongoDbConfig.OptionsConfig.OptionsConfigValue.RETRY_WRITES.getConfigPath())
                .isEqualTo(MongoDbConfig.OptionsConfig.OptionsConfigValue.RETRY_WRITES.getDefaultValue());
    }

}
