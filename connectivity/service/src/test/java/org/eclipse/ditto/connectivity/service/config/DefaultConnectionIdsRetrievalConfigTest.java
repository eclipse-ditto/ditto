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
package org.eclipse.ditto.connectivity.service.config;

import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.assertj.core.api.JUnitSoftAssertions;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link DefaultConnectionIdsRetrievalConfig}.
 */
public final class DefaultConnectionIdsRetrievalConfigTest {

    private static Config connectionIdsConf;

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @BeforeClass
    public static void initTestFixture() {
        connectionIdsConf = ConfigFactory.load("connections-ids-retrieval-test");
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DefaultConnectionIdsRetrievalConfig.class,
                areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DefaultConnectionIdsRetrievalConfig.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void underTestReturnsDefaultValuesIfBaseConfigWasEmpty() {
        final ConnectionIdsRetrievalConfig underTest = DefaultConnectionIdsRetrievalConfig.of(ConfigFactory.empty());

        softly.assertThat(underTest.getReadJournalBatchSize())
                .as(ConnectionIdsRetrievalConfig.ConnectionIdsRetrievalConfigValue.READ_JOURNAL_BATCH_SIZE.getConfigPath())
                .isEqualTo(
                        ConnectionIdsRetrievalConfig.ConnectionIdsRetrievalConfigValue.READ_JOURNAL_BATCH_SIZE.getDefaultValue());

        softly.assertThat(underTest.getReadSnapshotBatchSize())
                .as(ConnectionIdsRetrievalConfig.ConnectionIdsRetrievalConfigValue.READ_SNAPSHOT_BATCH_SIZE.getConfigPath())
                .isEqualTo(
                        ConnectionIdsRetrievalConfig.ConnectionIdsRetrievalConfigValue.READ_SNAPSHOT_BATCH_SIZE.getDefaultValue());
    }

    @Test
    public void underTestReturnsValuesOfConfigFile() {
        final ConnectionIdsRetrievalConfig underTest = DefaultConnectionIdsRetrievalConfig.of(connectionIdsConf);

        softly.assertThat(underTest.getReadJournalBatchSize())
                .as(ConnectionIdsRetrievalConfig.ConnectionIdsRetrievalConfigValue.READ_JOURNAL_BATCH_SIZE.getConfigPath())
                .isEqualTo(42);

        softly.assertThat(underTest.getReadSnapshotBatchSize())
                .as(ConnectionIdsRetrievalConfig.ConnectionIdsRetrievalConfigValue.READ_SNAPSHOT_BATCH_SIZE.getConfigPath())
                .isEqualTo(21);
    }
}
