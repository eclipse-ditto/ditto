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
package org.eclipse.ditto.services.thingsearch.updater.config;

import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;

import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.services.thingsearch.updater.config.DeletionConfig.DeletionConfigValue;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link DefaultDeletionConfig}.
 */
public final class DefaultDeletionConfigTest {

    private static Config deletionTestConfig;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void initTestFixture() {
        deletionTestConfig = ConfigFactory.load("deletion-test");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultDeletionConfig.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultDeletionConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void testSerializationAndDeserialization() throws IOException, ClassNotFoundException {
        final DefaultDeletionConfig underTest = DefaultDeletionConfig.of(deletionTestConfig);

        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        final ObjectOutput objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
        objectOutputStream.writeObject(underTest);
        objectOutputStream.close();

        final byte[] underTestSerialized = byteArrayOutputStream.toByteArray();

        final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(underTestSerialized);
        final ObjectInput objectInputStream = new ObjectInputStream(byteArrayInputStream);
        final Object underTestDeserialized = objectInputStream.readObject();

        softly.assertThat(underTestDeserialized).isEqualTo(underTest);
    }

    @Test
    public void gettersReturnDefaultValuesIfNotConfigured() {
        final DefaultDeletionConfig underTest = DefaultDeletionConfig.of(ConfigFactory.empty());

        softly.assertThat(underTest.isEnabled())
                .as(DeletionConfigValue.ENABLED.getConfigPath())
                .isEqualTo(DeletionConfigValue.ENABLED.getDefaultValue());
        softly.assertThat(underTest.getDeletionAge())
                .as(DeletionConfigValue.DELETION_AGE.getConfigPath())
                .isEqualTo(DeletionConfigValue.DELETION_AGE.getDefaultValue());
        softly.assertThat(underTest.getRunInterval())
                .as(DeletionConfigValue.RUN_INTERVAL.getConfigPath())
                .isEqualTo(DeletionConfigValue.RUN_INTERVAL.getDefaultValue());
        softly.assertThat(underTest.getFirstIntervalHour())
                .as(DeletionConfigValue.FIRST_INTERVAL_HOUR.getConfigPath())
                .isEqualTo(DeletionConfigValue.FIRST_INTERVAL_HOUR.getDefaultValue());
    }

    @Test
    public void gettersReturnConfiguredValues() {
        final DefaultDeletionConfig underTest = DefaultDeletionConfig.of(deletionTestConfig);
        final Config deletionScopedRawConfig = deletionTestConfig.getConfig(DefaultDeletionConfig.CONFIG_PATH);

        softly.assertThat(underTest.isEnabled())
                .as(DeletionConfigValue.ENABLED.getConfigPath())
                .isEqualTo(deletionScopedRawConfig.getBoolean(DeletionConfigValue.ENABLED.getConfigPath()));
        softly.assertThat(underTest.getDeletionAge())
                .as(DeletionConfigValue.DELETION_AGE.getConfigPath())
                .isEqualTo(deletionScopedRawConfig.getDuration(DeletionConfigValue.DELETION_AGE.getConfigPath()));
        softly.assertThat(underTest.getRunInterval())
                .as(DeletionConfigValue.RUN_INTERVAL.getConfigPath())
                .isEqualTo(deletionScopedRawConfig.getDuration(DeletionConfigValue.RUN_INTERVAL.getConfigPath()));
        softly.assertThat(underTest.getFirstIntervalHour())
                .as(DeletionConfigValue.FIRST_INTERVAL_HOUR.getConfigPath())
                .isEqualTo(deletionScopedRawConfig.getInt(DeletionConfigValue.FIRST_INTERVAL_HOUR.getConfigPath()));
    }

}
