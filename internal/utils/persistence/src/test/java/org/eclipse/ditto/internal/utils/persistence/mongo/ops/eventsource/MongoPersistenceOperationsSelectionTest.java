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
import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.bson.BsonString;
import org.bson.Document;
import org.junit.BeforeClass;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link MongoPersistenceOperationsSelection}.
 */
public final class MongoPersistenceOperationsSelectionTest {

    private static final String COLLECTION_NAME = "thingsMetadata";

    private static Document namespaceFilter;
    private static Document emptyFilter;

    @BeforeClass
    public static void initTestConstants() {
        namespaceFilter = new Document("_namespace", new BsonString("com.example.test"));
        emptyFilter = new Document();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(MongoPersistenceOperationsSelection.class,
                areImmutable(),
                assumingFields("filter").areNotModifiedAndDoNotEscape());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(MongoPersistenceOperationsSelection.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void getCollectionNameReturnsExpected() {
        final MongoPersistenceOperationsSelection
                underTest = MongoPersistenceOperationsSelection.of(COLLECTION_NAME, namespaceFilter);

        assertThat(underTest.getCollectionName()).isEqualTo(COLLECTION_NAME);
    }

    @Test
    public void getFilterReturnsExpected() {
        final MongoPersistenceOperationsSelection
                underTest = MongoPersistenceOperationsSelection.of(COLLECTION_NAME, namespaceFilter);

        assertThat(underTest.getFilter()).isEqualTo(namespaceFilter).isNotSameAs(namespaceFilter);
    }

    @Test
    public void emptyFilterIsEntireCollection() {
        final MongoPersistenceOperationsSelection
                underTest = MongoPersistenceOperationsSelection.of(COLLECTION_NAME, emptyFilter);

        assertThat(underTest.isEntireCollection()).isTrue();
    }

    @Test
    public void nonEmptyFilterIsNotEntireCollection() {
        final MongoPersistenceOperationsSelection
                underTest = MongoPersistenceOperationsSelection.of(COLLECTION_NAME, namespaceFilter);

        assertThat(underTest.isEntireCollection()).isFalse();
    }

    @Test
    public void toStringOfSelectionWithFilterReturnsExpected() {
        final MongoPersistenceOperationsSelection
                underTest = MongoPersistenceOperationsSelection.of(COLLECTION_NAME, namespaceFilter);

        assertThat(underTest.toString()).hasToString(COLLECTION_NAME + " (filtered: " + namespaceFilter + ")");
    }

    @Test
    public void toStringOfSelectionWithoutFilterReturnsExpected() {
        final MongoPersistenceOperationsSelection
                underTest = MongoPersistenceOperationsSelection.of(COLLECTION_NAME, emptyFilter);

        assertThat(underTest.toString()).hasToString(COLLECTION_NAME + " (complete)");
    }

}
