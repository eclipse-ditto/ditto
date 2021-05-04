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
package org.eclipse.ditto.internal.utils.persistence.mongo.indices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Arrays;
import java.util.List;

import org.bson.BsonDocument;
import org.junit.Test;

/**
 * Unit test for {@link IndexFactory}.
 */
public class IndexFactoryTest {

    private static final String INDEX_NAME = "index_name";
    private static final boolean UNIQUE = true;
    private static final boolean BACKGROUND = true;
    private static final String FIELD_1_NAME = "field1";
    private static final String FIELD_2_NAME = "field2";

    @Test
    public void assertImmutability() {
        assertInstancesOf(IndexFactory.class, areImmutable());
    }

    @Test
    public void newInstanceWithDefaultKeys() {
        final List<String> fields = Arrays.asList(FIELD_1_NAME, FIELD_2_NAME);

        final Index actual = IndexFactory.newInstance(INDEX_NAME, fields, UNIQUE);

        final BsonDocument expectedKeys = new BsonDocument();
        expectedKeys.put(FIELD_1_NAME, IndexDirection.DEFAULT.getBsonInt());
        expectedKeys.put(FIELD_2_NAME, IndexDirection.DEFAULT.getBsonInt());
        final Index expected = Index.of(expectedKeys, INDEX_NAME, UNIQUE, UNIQUE, BACKGROUND);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void newInstanceWithCustomKeys() {
        final IndexKey key1 = DefaultIndexKey.of(FIELD_1_NAME, IndexDirection.ASCENDING);
        final IndexKey key2 = DefaultIndexKey.of(FIELD_2_NAME, IndexDirection.DESCENDING);
        final List<IndexKey> keys = Arrays.asList(key1, key2);

        final Index actual = IndexFactory.newInstanceWithCustomKeys(INDEX_NAME, keys, UNIQUE);

        final BsonDocument expectedKeys = new BsonDocument();
        expectedKeys.put(FIELD_1_NAME, IndexDirection.ASCENDING.getBsonInt());
        expectedKeys.put(FIELD_2_NAME, IndexDirection.DESCENDING.getBsonInt());
        final Index expected = Index.of(expectedKeys, INDEX_NAME, UNIQUE, UNIQUE, BACKGROUND);
        assertThat(actual).isEqualTo(expected);
    }
}
