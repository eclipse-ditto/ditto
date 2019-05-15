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
 * Unit test for {@link org.eclipse.ditto.services.utils.persistence.mongo.config.DefaultSnapshotConfig}.
 */
public final class DefaultSnapshotConfigTest {

    private static Config snapshotTestConf;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void initTestFixture() {
        snapshotTestConf = ConfigFactory.load("snapshot-test");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultSnapshotConfig.class,
                areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultSnapshotConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void testSerializationAndDeserialization() throws IOException, ClassNotFoundException {
        final DefaultSnapshotConfig underTest = DefaultSnapshotConfig.of(snapshotTestConf);
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
        final DefaultSnapshotConfig underTest = DefaultSnapshotConfig.of(ConfigFactory.empty());

        softly.assertThat(underTest.getInterval())
                .as(SnapshotConfig.SnapshotConfigValue.INTERVAL.getConfigPath())
                .isEqualTo(SnapshotConfig.SnapshotConfigValue.INTERVAL.getDefaultValue());
        softly.assertThat(underTest.getThreshold())
                .as(SnapshotConfig.SnapshotConfigValue.THRESHOLD.getConfigPath())
                .isEqualTo(SnapshotConfig.SnapshotConfigValue.THRESHOLD.getDefaultValue());
        softly.assertThat(underTest.isDeleteOldSnapshot())
                .as(SnapshotConfig.SnapshotConfigValue.DELETE_OLD_SNAPSHOT.getConfigPath())
                .isEqualTo(SnapshotConfig.SnapshotConfigValue.DELETE_OLD_SNAPSHOT.getDefaultValue());
        softly.assertThat(underTest.isDeleteOldEvents())
                .as(SnapshotConfig.SnapshotConfigValue.DELETE_OLD_EVENTS.getConfigPath())
                .isEqualTo(SnapshotConfig.SnapshotConfigValue.DELETE_OLD_EVENTS.getDefaultValue());
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final DefaultSnapshotConfig underTest = DefaultSnapshotConfig.of(snapshotTestConf);

        softly.assertThat(underTest.getInterval())
                .as(SnapshotConfig.SnapshotConfigValue.INTERVAL.getConfigPath())
                .isEqualTo(Duration.ofDays(100L));
        softly.assertThat(underTest.getThreshold())
                .as(SnapshotConfig.SnapshotConfigValue.THRESHOLD.getConfigPath())
                .isEqualTo(2);
        softly.assertThat(underTest.isDeleteOldSnapshot())
                .as(SnapshotConfig.SnapshotConfigValue.DELETE_OLD_SNAPSHOT.getConfigPath())
                .isTrue();
        softly.assertThat(underTest.isDeleteOldEvents())
                .as(SnapshotConfig.SnapshotConfigValue.DELETE_OLD_EVENTS.getConfigPath())
                .isTrue();
    }
}
