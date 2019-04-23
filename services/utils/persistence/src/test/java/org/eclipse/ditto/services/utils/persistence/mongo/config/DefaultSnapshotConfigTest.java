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

import static org.assertj.core.api.Assertions.assertThat;
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

import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link org.eclipse.ditto.services.utils.persistence.mongo.config.DefaultSnapshotConfig}.
 */
public final class DefaultSnapshotConfigTest {

    private static Config snapshotTestConf;

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

        assertThat(underTestDeserialized).isEqualTo(underTest);
    }

    @Test
    public void underTestReturnsDefaultValuesIfBaseConfigWasEmpty() {
        final DefaultSnapshotConfig underTest = DefaultSnapshotConfig.of(ConfigFactory.empty());

        assertThat(underTest.getInterval())
                .as("getInterval")
                .isEqualTo(SnapshotConfig.SnapshotConfigValue.INTERVAL.getDefaultValue());
        assertThat(underTest.getThreshold())
                .as("getThreshold")
                .isEqualTo(SnapshotConfig.SnapshotConfigValue.THRESHOLD.getDefaultValue());
        assertThat(underTest.isDeleteOldSnapshot())
                .as("isDeleteOldSnapshot")
                .isEqualTo(SnapshotConfig.SnapshotConfigValue.DELETE_OLD_SNAPSHOT.getDefaultValue());
        assertThat(underTest.isDeleteOldEvents())
                .as("isDeleteOldEvents")
                .isEqualTo(SnapshotConfig.SnapshotConfigValue.DELETE_OLD_EVENTS.getDefaultValue());
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final DefaultSnapshotConfig underTest = DefaultSnapshotConfig.of(snapshotTestConf);

        assertThat(underTest.getInterval())
                .as("getInterval")
                .isEqualTo(Duration.ofDays(100L));
        assertThat(underTest.getThreshold())
                .as("getThreshold")
                .isEqualTo(2);
        assertThat(underTest.isDeleteOldSnapshot())
                .as("isDeleteOldSnapshot")
                .isTrue();
        assertThat(underTest.isDeleteOldEvents())
                .as("isDeleteOldEvents")
                .isTrue();
    }
}
