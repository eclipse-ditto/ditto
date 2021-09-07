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

import java.util.Collection;

import org.bson.BsonString;
import org.bson.Document;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.type.EntityType;
import org.eclipse.ditto.things.model.ThingConstants;
import org.junit.Test;

/**
 * Tests {@link MongoPersistenceOperationsSelectionProvider}.
 */
public final class MongoPersistenceOperationsSelectionProviderTest {

    private static final String KEY_PID = "pid";

    private static final EntityType THING_TYPE = ThingConstants.ENTITY_TYPE;
    private static final String PERSISTENCE_ID_PREFIX = THING_TYPE + ":";
    private static final String METADATA_COLLECTION_NAME = "myMetadataCollection";
    private static final String JOURNAL_COLLECTION_NAME = "myJournalCollection";
    private static final String SNAPSHOT_COLLECTION_NAME = "mySnapshotCollection";

    private static final String ENTITY_NS = "my.ns";
    private static final String ENTITY_NAME = "name";

    @Test
    public void assertImmutability() {
        assertInstancesOf(MongoPersistenceOperationsSelectionProvider.class, areImmutable());
    }

    @Test
    public void selectNamespaceWhenNamespacesDisabledFails() {
        final MongoEventSourceSettings settings = MongoEventSourceSettings.of(PERSISTENCE_ID_PREFIX,
                false, METADATA_COLLECTION_NAME, JOURNAL_COLLECTION_NAME, SNAPSHOT_COLLECTION_NAME);
        final MongoPersistenceOperationsSelectionProvider underTest =
                MongoPersistenceOperationsSelectionProvider.of(settings);

        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> underTest.selectNamespace(ENTITY_NS));
    }

    @Test
    public void selectEntityWhenNamespacesDisabled() {
        final MongoEventSourceSettings settings = MongoEventSourceSettings.of(PERSISTENCE_ID_PREFIX,
                false, METADATA_COLLECTION_NAME, JOURNAL_COLLECTION_NAME, SNAPSHOT_COLLECTION_NAME);
        final MongoPersistenceOperationsSelectionProvider underTest =
                MongoPersistenceOperationsSelectionProvider.of(settings);

        final Collection<MongoPersistenceOperationsSelection> selections =
                underTest.selectEntity(EntityId.of(THING_TYPE, ENTITY_NS + ":" + ENTITY_NAME));

        final String pid = PERSISTENCE_ID_PREFIX + ENTITY_NS + ":" + ENTITY_NAME;
        final Document pidFilter = new Document().append(KEY_PID, new BsonString(pid));
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
