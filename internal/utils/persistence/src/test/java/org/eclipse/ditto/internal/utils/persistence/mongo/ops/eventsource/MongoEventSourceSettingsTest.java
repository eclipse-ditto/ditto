/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.persistence.mongo.ops.eventsource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.junit.Test;

import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Tests {@link MongoEventSourceSettings}.
 */
public class MongoEventSourceSettingsTest {

    private static final String PERSISTENCE_ID_PREFIX = "myPidPrefix:";
    private static final boolean SUPPORTS_NAMESPACES = true;
    private static final String METADATA_COLLECTION_NAME = "myMetadataCollection";
    private static final String JOURNAL_COLLECTION_NAME = "myJournalCollection";
    private static final String SNAPSHOT_COLLECTION_NAME = "mySnapshotCollection";

    private static final String JOURNAL_PLUGIN_ID = "my-journal-plugin-id";
    private static final String SNAPSHOT_PLUGIN_ID = "my-snapshot-plugin-id";

    @Test
    public void assertImmutability() {
        assertInstancesOf(MongoEventSourceSettings.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(MongoEventSourceSettings.class).verify();
    }

    @Test
    public void createAndCheckGetters() {
        final MongoEventSourceSettings settings = MongoEventSourceSettings.of(PERSISTENCE_ID_PREFIX,
                SUPPORTS_NAMESPACES, METADATA_COLLECTION_NAME, JOURNAL_COLLECTION_NAME, SNAPSHOT_COLLECTION_NAME);

        assertThat(settings).isNotNull();
        assertThat(settings.getPersistenceIdPrefix()).isEqualTo(PERSISTENCE_ID_PREFIX);
        assertThat(settings.isSupportsNamespaces()).isEqualTo(SUPPORTS_NAMESPACES);
        assertThat(settings.getMetadataCollectionName()).isEqualTo(METADATA_COLLECTION_NAME);
        assertThat(settings.getJournalCollectionName()).isEqualTo(JOURNAL_COLLECTION_NAME);
        assertThat(settings.getSnapshotCollectionName()).isEqualTo(SNAPSHOT_COLLECTION_NAME);
    }

    @Test
    public void fromConfigFailsWithEmptyConfig() {
        assertThatExceptionOfType(ConfigException.Missing.class).isThrownBy(() ->
                MongoEventSourceSettings.fromConfig(ConfigFactory.empty(), PERSISTENCE_ID_PREFIX, SUPPORTS_NAMESPACES,
                        JOURNAL_PLUGIN_ID, SNAPSHOT_PLUGIN_ID));
    }

}
