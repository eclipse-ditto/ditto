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
package org.eclipse.ditto.services.utils.persistence.mongo.namespace;

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
 * Unit test for {@link org.eclipse.ditto.services.utils.persistence.mongo.namespace.MongoNamespaceSelection}.
 */
public final class MongoNamespaceSelectionTest {

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
        assertInstancesOf(MongoNamespaceSelection.class,
                areImmutable(),
                assumingFields("filter").areNotModifiedAndDoNotEscape());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(MongoNamespaceSelection.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void getCollectionNameReturnsExpected() {
        final MongoNamespaceSelection underTest = MongoNamespaceSelection.of(COLLECTION_NAME, namespaceFilter);

        assertThat(underTest.getCollectionName()).isEqualTo(COLLECTION_NAME);
    }

    @Test
    public void getFilterReturnsExpected() {
        final MongoNamespaceSelection underTest = MongoNamespaceSelection.of(COLLECTION_NAME, namespaceFilter);

        assertThat(underTest.getFilter()).isEqualTo(namespaceFilter).isNotSameAs(namespaceFilter);
    }

    @Test
    public void emptyFilterIsEntireCollection() {
        final MongoNamespaceSelection underTest = MongoNamespaceSelection.of(COLLECTION_NAME, emptyFilter);

        assertThat(underTest.isEntireCollection()).isTrue();
    }

    @Test
    public void nonEmptyFilterIsNotEntireCollection() {
        final MongoNamespaceSelection underTest = MongoNamespaceSelection.of(COLLECTION_NAME, namespaceFilter);

        assertThat(underTest.isEntireCollection()).isFalse();
    }

    @Test
    public void toStringOfSelectionWithFilterReturnsExpected() {
        final MongoNamespaceSelection underTest = MongoNamespaceSelection.of(COLLECTION_NAME, namespaceFilter);

        assertThat(underTest.toString()).isEqualTo(COLLECTION_NAME + " (to filter)");
    }

    @Test
    public void toStringOfSelectionWithoutFilterReturnsExpected() {
        final MongoNamespaceSelection underTest = MongoNamespaceSelection.of(COLLECTION_NAME, emptyFilter);

        assertThat(underTest.toString()).isEqualTo(COLLECTION_NAME + " (to drop)");
    }

}