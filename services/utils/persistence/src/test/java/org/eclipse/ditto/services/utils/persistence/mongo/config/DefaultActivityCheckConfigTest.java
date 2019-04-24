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
package org.eclipse.ditto.services.utils.persistence.mongo.config;

import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.time.Duration;

import org.assertj.core.api.JUnitSoftAssertions;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link org.eclipse.ditto.services.utils.persistence.mongo.config.DefaultActivityCheckConfig}.
 */
public final class DefaultActivityCheckConfigTest {

    private static Config activityCheckTestConf;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();


    @BeforeClass
    public static void initTestFixture() {
        activityCheckTestConf = ConfigFactory.load("activity-check-test");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultActivityCheckConfig.class,
                areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultActivityCheckConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void testSerializationAndDeserialization() throws IOException, ClassNotFoundException {
        final DefaultActivityCheckConfig underTest = DefaultActivityCheckConfig.of(activityCheckTestConf);

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
    public void underTestReturnsDefaultValuesIfBaseConfigWasEmpty() {
        final DefaultActivityCheckConfig underTest = DefaultActivityCheckConfig.of(ConfigFactory.empty());

        softly.assertThat(underTest.getInactiveInterval())
                .as(ActivityCheckConfig.ActivityCheckConfigValue.INACTIVE_INTERVAL.getConfigPath())
                .isEqualTo(ActivityCheckConfig.ActivityCheckConfigValue.INACTIVE_INTERVAL.getDefaultValue());
        softly.assertThat(underTest.getDeletedInterval())
                .as(ActivityCheckConfig.ActivityCheckConfigValue.DELETED_INTERVAL.getConfigPath())
                .isEqualTo(ActivityCheckConfig.ActivityCheckConfigValue.DELETED_INTERVAL.getDefaultValue());
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final DefaultActivityCheckConfig underTest = DefaultActivityCheckConfig.of(activityCheckTestConf);

        softly.assertThat(underTest.getInactiveInterval())
                .as(ActivityCheckConfig.ActivityCheckConfigValue.INACTIVE_INTERVAL.getConfigPath())
                .isEqualTo(Duration.ofDays(100L));
        softly.assertThat(underTest.getDeletedInterval())
                .as(ActivityCheckConfig.ActivityCheckConfigValue.DELETED_INTERVAL.getConfigPath())
                .isEqualTo(Duration.ofDays(100L));
    }
}
