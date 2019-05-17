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
package org.eclipse.ditto.services.thingsearch.common.config;

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

import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.services.thingsearch.common.config.UpdaterConfig.UpdaterConfigValue;
import org.eclipse.ditto.services.utils.akka.streaming.DefaultSyncConfig;
import org.eclipse.ditto.services.utils.akka.streaming.SyncConfig;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link DefaultUpdaterConfig}.
 */
public final class DefaultUpdaterConfigTest {

    private static Config updaterTestConfig;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void initTestFixture() {
        updaterTestConfig = ConfigFactory.load("updater-test");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultUpdaterConfig.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultUpdaterConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void testSerializationAndDeserialization() throws IOException, ClassNotFoundException {
        final DefaultUpdaterConfig underTest = DefaultUpdaterConfig.of(updaterTestConfig);

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
        final DefaultUpdaterConfig underTest = DefaultUpdaterConfig.of(ConfigFactory.empty());

        softly.assertThat(underTest.getMaxBulkSize())
                .as(UpdaterConfigValue.MAX_BULK_SIZE.getConfigPath())
                .isEqualTo(UpdaterConfigValue.MAX_BULK_SIZE.getDefaultValue());
        softly.assertThat(underTest.isEventProcessingActive())
                .as(UpdaterConfigValue.EVENT_PROCESSING_ACTIVE.getConfigPath())
                .isEqualTo(UpdaterConfigValue.EVENT_PROCESSING_ACTIVE.getDefaultValue());
        softly.assertThat(underTest.getMaxIdleTime())
                .as(UpdaterConfigValue.MAX_IDLE_TIME.getConfigPath())
                .isEqualTo(UpdaterConfigValue.MAX_IDLE_TIME.getDefaultValue());
        softly.assertThat(underTest.getThingsSyncConfig())
                .satisfies(thingsSyncConfig -> assertThat(thingsSyncConfig.getElementsStreamedPerBatch())
                        .as(SyncConfig.SyncConfigValue.ELEMENT_STREAM_BATCH_SIZE.getConfigPath())
                        .isEqualTo(SyncConfig.SyncConfigValue.ELEMENT_STREAM_BATCH_SIZE.getDefaultValue()));
        softly.assertThat(underTest.getPoliciesSyncConfig())
                .satisfies(policiesSyncConfig -> assertThat(policiesSyncConfig.getStreamingActorTimeout())
                        .as(SyncConfig.SyncConfigValue.STREAMING_ACTOR_TIMEOUT.getConfigPath())
                        .isEqualTo(SyncConfig.SyncConfigValue.STREAMING_ACTOR_TIMEOUT.getDefaultValue()));
    }

    @Test
    public void gettersReturnConfiguredValues() {
        final DefaultUpdaterConfig underTest = DefaultUpdaterConfig.of(updaterTestConfig);
        final Config updaterScopedRawConfig = updaterTestConfig.getConfig(DefaultUpdaterConfig.CONFIG_PATH);
        final SyncConfig thingsSyncConfig =
                DefaultSyncConfig.getInstance(updaterScopedRawConfig, DefaultUpdaterConfig.THINGS_SYNC_CONFIG_PATH);
        final SyncConfig policiesSyncConfig =
                DefaultSyncConfig.getInstance(updaterScopedRawConfig, DefaultUpdaterConfig.POLICIES_SYNC_CONFIG_PATH);

        softly.assertThat(underTest.getMaxBulkSize())
                .as(UpdaterConfigValue.MAX_BULK_SIZE.getConfigPath())
                .isEqualTo(updaterScopedRawConfig.getInt(UpdaterConfigValue.MAX_BULK_SIZE.getConfigPath()));
        softly.assertThat(underTest.isEventProcessingActive())
                .as(UpdaterConfigValue.EVENT_PROCESSING_ACTIVE.getConfigPath())
                .isEqualTo(
                        updaterScopedRawConfig.getBoolean(UpdaterConfigValue.EVENT_PROCESSING_ACTIVE.getConfigPath()));
        softly.assertThat(underTest.getMaxIdleTime())
                .as(UpdaterConfigValue.MAX_IDLE_TIME.getConfigPath())
                .isEqualTo(updaterScopedRawConfig.getDuration(UpdaterConfigValue.MAX_IDLE_TIME.getConfigPath()));
        softly.assertThat(underTest.getThingsSyncConfig())
                .as(DefaultUpdaterConfig.THINGS_SYNC_CONFIG_PATH)
                .isEqualTo(thingsSyncConfig);
        softly.assertThat(underTest.getPoliciesSyncConfig())
                .as(DefaultUpdaterConfig.POLICIES_SYNC_CONFIG_PATH)
                .isEqualTo(policiesSyncConfig);
    }

}