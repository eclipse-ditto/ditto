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
package org.eclipse.ditto.services.utils.persistence.mongo.ops.eventsource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Collection;

import org.bson.BsonRegularExpression;
import org.bson.BsonString;
import org.bson.Document;
import org.junit.Test;

/**
 * Tests {@link MongoPersistenceOperationsSelectionProvider}.
 */
public class MongoPersistenceOperationsSelectionProviderTest {

    private static final String KEY_PID = "pid";

    private static final String PERSISTENCE_ID_PREFIX = "myPidPrefix:";
    private static final String METADATA_COLLECTION_NAME = "myMetadataCollection";
    private static final String JOURNAL_COLLECTION_NAME = "myJournalCollection";
    private static final String SNAPSHOT_COLLECTION_NAME = "mySnapshotCollection";
    private static final String SUFFIX_SEPARATOR = "@";

    private static final String ENTITY_NS = "my.ns";
    private static final String ENTITY_NAME = "name";
    private static final String NS_SEPARATOR = ":";
    private static final String ENTITY_ID = ENTITY_NS + NS_SEPARATOR + ENTITY_NAME;

    private static final Document EMPTY_FILTER = new Document();

    @Test
    public void assertImmutability() {
        assertInstancesOf(MongoPersistenceOperationsSelectionProvider.class, areImmutable());
    }

    @Test
    public void selectNamespaceWhenNamespacesEnabledAndSuffixBuilderEnabled() {
        final MongoEventSourceSettings settings = MongoEventSourceSettings.of(PERSISTENCE_ID_PREFIX,
                true, METADATA_COLLECTION_NAME, JOURNAL_COLLECTION_NAME, SNAPSHOT_COLLECTION_NAME, SUFFIX_SEPARATOR);
        final MongoPersistenceOperationsSelectionProvider underTest =
                MongoPersistenceOperationsSelectionProvider.of(settings);

        final Collection<MongoPersistenceOperationsSelection> selections = underTest.selectNamespace(ENTITY_NS);

        final String pidRegex = "^" + PERSISTENCE_ID_PREFIX + ENTITY_NS + NS_SEPARATOR;
        final MongoPersistenceOperationsSelection expectedMetadataSelection =
                MongoPersistenceOperationsSelection.of(METADATA_COLLECTION_NAME,
                        new Document(KEY_PID, new BsonRegularExpression(pidRegex)));
        final MongoPersistenceOperationsSelection expectedJournalSelection =
                MongoPersistenceOperationsSelection.of(JOURNAL_COLLECTION_NAME + SUFFIX_SEPARATOR + ENTITY_NS,
                        EMPTY_FILTER);
        final MongoPersistenceOperationsSelection expectedSnapshotSelection =
                MongoPersistenceOperationsSelection.of(SNAPSHOT_COLLECTION_NAME + SUFFIX_SEPARATOR + ENTITY_NS,
                        EMPTY_FILTER);
        assertThat(selections)
                .containsExactlyInAnyOrder(expectedMetadataSelection, expectedJournalSelection,
                        expectedSnapshotSelection);
    }

    @Test
    public void selectNamespaceWhenNamespacesEnabledAndSuffixBuilderDisabled() {
        final MongoEventSourceSettings settings = MongoEventSourceSettings.of(PERSISTENCE_ID_PREFIX,
                true, METADATA_COLLECTION_NAME, JOURNAL_COLLECTION_NAME, SNAPSHOT_COLLECTION_NAME, null);
        final MongoPersistenceOperationsSelectionProvider underTest =
                MongoPersistenceOperationsSelectionProvider.of(settings);

        final Collection<MongoPersistenceOperationsSelection> selections = underTest.selectNamespace(ENTITY_NS);

        final String pidRegex = "^" + PERSISTENCE_ID_PREFIX + ENTITY_NS + NS_SEPARATOR;
        final MongoPersistenceOperationsSelection expectedMetadataSelection =
                MongoPersistenceOperationsSelection.of(METADATA_COLLECTION_NAME,
                new Document(KEY_PID, new BsonRegularExpression(pidRegex)));
        final MongoPersistenceOperationsSelection expectedJournalSelection =
                MongoPersistenceOperationsSelection.of(JOURNAL_COLLECTION_NAME,
                new Document(KEY_PID, new BsonRegularExpression(pidRegex)));
        final MongoPersistenceOperationsSelection expectedSnapshotSelection =
                MongoPersistenceOperationsSelection.of(SNAPSHOT_COLLECTION_NAME,
                new Document(KEY_PID, new BsonRegularExpression(pidRegex)));
        assertThat(selections)
                .containsExactlyInAnyOrder(expectedMetadataSelection, expectedJournalSelection,
                        expectedSnapshotSelection);
    }

    @Test
    public void selectNamespaceWhenNamespacesDisabledFails() {
        final MongoEventSourceSettings settings = MongoEventSourceSettings.of(PERSISTENCE_ID_PREFIX,
                false, METADATA_COLLECTION_NAME, JOURNAL_COLLECTION_NAME, SNAPSHOT_COLLECTION_NAME, null);
        final MongoPersistenceOperationsSelectionProvider underTest =
                MongoPersistenceOperationsSelectionProvider.of(settings);

        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> underTest.selectNamespace(ENTITY_NS));
    }

    @Test
    public void selectEntityWhenNamespacesEnabledAndSuffixBuilderEnabled() {
        final MongoEventSourceSettings settings = MongoEventSourceSettings.of(PERSISTENCE_ID_PREFIX,
                true, METADATA_COLLECTION_NAME, JOURNAL_COLLECTION_NAME, SNAPSHOT_COLLECTION_NAME, SUFFIX_SEPARATOR);
        final MongoPersistenceOperationsSelectionProvider underTest =
                MongoPersistenceOperationsSelectionProvider.of(settings);

        final Collection<MongoPersistenceOperationsSelection> selections = underTest.selectEntity(ENTITY_ID);

        final String pid = PERSISTENCE_ID_PREFIX + ENTITY_ID;
        final Document pidFilter = new Document(KEY_PID, new BsonString(pid));
        final MongoPersistenceOperationsSelection expectedMetadataSelection =
                MongoPersistenceOperationsSelection.of(METADATA_COLLECTION_NAME, pidFilter);
        final MongoPersistenceOperationsSelection expectedJournalSelection =
                MongoPersistenceOperationsSelection.of(JOURNAL_COLLECTION_NAME + SUFFIX_SEPARATOR + ENTITY_NS,
                        pidFilter);
        final MongoPersistenceOperationsSelection expectedSnapshotSelection =
                MongoPersistenceOperationsSelection.of(SNAPSHOT_COLLECTION_NAME + SUFFIX_SEPARATOR + ENTITY_NS,
                        pidFilter);
        assertThat(selections)
                .containsExactlyInAnyOrder(expectedMetadataSelection, expectedJournalSelection,
                        expectedSnapshotSelection);
    }

    @Test
    public void selectEntityWhenNamespacesEnabledAndSuffixBuilderDisabled() {
        final MongoEventSourceSettings settings = MongoEventSourceSettings.of(PERSISTENCE_ID_PREFIX,
                true, METADATA_COLLECTION_NAME, JOURNAL_COLLECTION_NAME, SNAPSHOT_COLLECTION_NAME, null);
        final MongoPersistenceOperationsSelectionProvider underTest =
                MongoPersistenceOperationsSelectionProvider.of(settings);

        final Collection<MongoPersistenceOperationsSelection> selections = underTest.selectEntity(ENTITY_ID);

        final String pid = PERSISTENCE_ID_PREFIX + ENTITY_ID;
        final Document pidFilter = new Document(KEY_PID, new BsonString(pid));
        final MongoPersistenceOperationsSelection expectedMetadataSelection =
                MongoPersistenceOperationsSelection.of(METADATA_COLLECTION_NAME, pidFilter);
        final MongoPersistenceOperationsSelection expectedJournalSelection =
                MongoPersistenceOperationsSelection.of(JOURNAL_COLLECTION_NAME, pidFilter);
        final MongoPersistenceOperationsSelection expectedSnapshotSelection =
                MongoPersistenceOperationsSelection.of(SNAPSHOT_COLLECTION_NAME, pidFilter);
        assertThat(selections)
                .containsExactlyInAnyOrder(expectedMetadataSelection, expectedJournalSelection,
                        expectedSnapshotSelection);
    }

    @Test
    public void selectEntityWithoutNamespaceWhenNamespacesEnabledFails() {
        final MongoEventSourceSettings settings = MongoEventSourceSettings.of(PERSISTENCE_ID_PREFIX,
                true, METADATA_COLLECTION_NAME, JOURNAL_COLLECTION_NAME, SNAPSHOT_COLLECTION_NAME, null);
        final MongoPersistenceOperationsSelectionProvider underTest =
                MongoPersistenceOperationsSelectionProvider.of(settings);

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> underTest.selectEntity(ENTITY_NAME));
    }

    @Test
    public void selectEntityWhenNamespacesDisabled() {
        final MongoEventSourceSettings settings = MongoEventSourceSettings.of(PERSISTENCE_ID_PREFIX,
                false, METADATA_COLLECTION_NAME, JOURNAL_COLLECTION_NAME, SNAPSHOT_COLLECTION_NAME, null);
        final MongoPersistenceOperationsSelectionProvider underTest =
                MongoPersistenceOperationsSelectionProvider.of(settings);

        final Collection<MongoPersistenceOperationsSelection> selections = underTest.selectEntity(ENTITY_NAME);

        final String pid = PERSISTENCE_ID_PREFIX + ENTITY_NAME;
        final Document pidFilter = new Document(KEY_PID, new BsonString(pid));
        final MongoPersistenceOperationsSelection expectedMetadataSelection =
                MongoPersistenceOperationsSelection.of(METADATA_COLLECTION_NAME, pidFilter);
        final MongoPersistenceOperationsSelection expectedJournalSelection =
                MongoPersistenceOperationsSelection.of(JOURNAL_COLLECTION_NAME, pidFilter);
        final MongoPersistenceOperationsSelection expectedSnapshotSelection =
                MongoPersistenceOperationsSelection.of(SNAPSHOT_COLLECTION_NAME, pidFilter);
        assertThat(selections)
                .containsExactlyInAnyOrder(expectedMetadataSelection, expectedJournalSelection,
                        expectedSnapshotSelection);
    }

}
