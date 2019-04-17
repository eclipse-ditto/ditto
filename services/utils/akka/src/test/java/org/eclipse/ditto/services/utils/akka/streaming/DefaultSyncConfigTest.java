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
package org.eclipse.ditto.services.utils.akka.streaming;

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
import org.eclipse.ditto.services.utils.akka.streaming.SyncConfig.SyncConfigValue;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link DefaultSyncConfig}.
 */
public final class DefaultSyncConfigTest {

    private static final String CONFIG_PATH = "sync.things";

    private static Config syncTestConfig;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void initTestFixture() {
        syncTestConfig = ConfigFactory.load("sync-test");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultSyncConfig.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultSyncConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void testSerializationAndDeserialization() throws IOException, ClassNotFoundException {
        final DefaultSyncConfig underTest = DefaultSyncConfig.getInstance(syncTestConfig, CONFIG_PATH);

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
        final DefaultSyncConfig underTest = DefaultSyncConfig.getInstance(ConfigFactory.empty(), CONFIG_PATH);

        softly.assertThat(underTest.isEnabled())
                .as(SyncConfigValue.ENABLED.getConfigPath())
                .isEqualTo(SyncConfigValue.ENABLED.getDefaultValue());
        softly.assertThat(underTest.getStartOffset())
                .as(SyncConfigValue.START_OFFSET.getConfigPath())
                .isEqualTo(SyncConfigValue.START_OFFSET.getDefaultValue());
        softly.assertThat(underTest.getInitialStartOffset())
                .as(SyncConfigValue.INITIAL_START_OFFSET.getConfigPath())
                .isEqualTo(SyncConfigValue.INITIAL_START_OFFSET.getDefaultValue());
        softly.assertThat(underTest.getStreamInterval())
                .as(SyncConfigValue.STREAM_INTERVAL.getConfigPath())
                .isEqualTo(SyncConfigValue.STREAM_INTERVAL.getDefaultValue());
        softly.assertThat(underTest.getOutdatedWarningOffset())
                .as(SyncConfigValue.OUTDATED_WARNING_OFFSET.getConfigPath())
                .isEqualTo(SyncConfigValue.OUTDATED_WARNING_OFFSET.getDefaultValue());
        softly.assertThat(underTest.getOutdatedErrorOffset())
                .as(SyncConfigValue.OUTDATED_ERROR_OFFSET.getConfigPath())
                .isEqualTo(SyncConfigValue.OUTDATED_ERROR_OFFSET.getDefaultValue());
        softly.assertThat(underTest.getMaxIdleTime())
                .as(SyncConfigValue.MAX_IDLE_TIME.getConfigPath())
                .isEqualTo(SyncConfigValue.MAX_IDLE_TIME.getDefaultValue());
        softly.assertThat(underTest.getStreamingActorTimeout())
                .as(SyncConfigValue.STREAMING_ACTOR_TIMEOUT.getConfigPath())
                .isEqualTo(SyncConfigValue.STREAMING_ACTOR_TIMEOUT.getDefaultValue());
        softly.assertThat(underTest.getElementsStreamedPerBatch())
                .as(SyncConfigValue.ELEMENT_STREAM_BATCH_SIZE.getConfigPath())
                .isEqualTo(SyncConfigValue.ELEMENT_STREAM_BATCH_SIZE.getDefaultValue());
        softly.assertThat(underTest.getMinimalDelayBetweenStreams())
                .as(SyncConfigValue.MINIMAL_DELAY_BETWEEN_STREAMS.getConfigPath())
                .isEqualTo(SyncConfigValue.MINIMAL_DELAY_BETWEEN_STREAMS.getDefaultValue());
    }

    @Test
    public void gettersReturnConfiguredValues() {
        final DefaultSyncConfig underTest = DefaultSyncConfig.getInstance(syncTestConfig, CONFIG_PATH);
        final Config scopedRawConfig = syncTestConfig.getConfig(CONFIG_PATH);

        softly.assertThat(underTest.isEnabled())
                .as(SyncConfigValue.ENABLED.getConfigPath())
                .isEqualTo(scopedRawConfig.getBoolean(SyncConfigValue.ENABLED.getConfigPath()));
        softly.assertThat(underTest.getStartOffset())
                .as(SyncConfigValue.START_OFFSET.getConfigPath())
                .isEqualTo(scopedRawConfig.getDuration(SyncConfigValue.START_OFFSET.getConfigPath()));
        softly.assertThat(underTest.getInitialStartOffset())
                .as(SyncConfigValue.INITIAL_START_OFFSET.getConfigPath())
                .isEqualTo(scopedRawConfig.getDuration(SyncConfigValue.INITIAL_START_OFFSET.getConfigPath()));
        softly.assertThat(underTest.getStreamInterval())
                .as(SyncConfigValue.STREAM_INTERVAL.getConfigPath())
                .isEqualTo(scopedRawConfig.getDuration(SyncConfigValue.STREAM_INTERVAL.getConfigPath()));
        softly.assertThat(underTest.getOutdatedWarningOffset())
                .as(SyncConfigValue.OUTDATED_WARNING_OFFSET.getConfigPath())
                .isEqualTo(scopedRawConfig.getDuration(SyncConfigValue.OUTDATED_WARNING_OFFSET.getConfigPath()));
        softly.assertThat(underTest.getOutdatedErrorOffset())
                .as(SyncConfigValue.OUTDATED_ERROR_OFFSET.getConfigPath())
                .isEqualTo(scopedRawConfig.getDuration(SyncConfigValue.OUTDATED_ERROR_OFFSET.getConfigPath()));
        softly.assertThat(underTest.getMaxIdleTime())
                .as(SyncConfigValue.MAX_IDLE_TIME.getConfigPath())
                .isEqualTo(scopedRawConfig.getDuration(SyncConfigValue.MAX_IDLE_TIME.getConfigPath()));
        softly.assertThat(underTest.getStreamingActorTimeout())
                .as(SyncConfigValue.STREAMING_ACTOR_TIMEOUT.getConfigPath())
                .isEqualTo(scopedRawConfig.getDuration(SyncConfigValue.STREAMING_ACTOR_TIMEOUT.getConfigPath()));
        softly.assertThat(underTest.getElementsStreamedPerBatch())
                .as(SyncConfigValue.ELEMENT_STREAM_BATCH_SIZE.getConfigPath())
                .isEqualTo(scopedRawConfig.getInt(SyncConfigValue.ELEMENT_STREAM_BATCH_SIZE.getConfigPath()));
        softly.assertThat(underTest.getMinimalDelayBetweenStreams())
                .as(SyncConfigValue.MINIMAL_DELAY_BETWEEN_STREAMS.getConfigPath())
                .isEqualTo(scopedRawConfig.getDuration(SyncConfigValue.MINIMAL_DELAY_BETWEEN_STREAMS.getConfigPath()));
    }

}