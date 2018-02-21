/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.utils.persistence.mongo.indices;

import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.concurrent.Immutable;

import org.bson.BsonDocument;

/**
 * A factory for creating {@link Index} instances.
 */
@Immutable
public final class IndexFactory {

    private IndexFactory() {
        throw new AssertionError();
    }

    /**
     * IMPORTANT: per default, build the index in the background. Otherwise all read and write ops to the DB would be
     * blocked.
     */
    private static final boolean BACKGROUND_OPTION_DEFAULT = true;

    /**
     * Returns a new {@link Index} with all fields having default (i.e. ascending) index direction. When {@code unique}
     * is true, the created index will also be {@code sparse}.
     *
     * @param name the name of the index.
     * @param fields the fields which form the index.
     * @param unique whether or not the index should be unique.
     * @return the index.
     * @see #newInstanceWithCustomKeys(String, List, boolean)
     */
    public static Index newInstance(final String name, final List<String> fields, final boolean unique) {
        final List<IndexKey> keys = createDefaultKeys(requireNonNull(fields));
        return newInstanceWithCustomKeys(name, keys, unique);
    }

    /**
     * Returns a new {@link Index} with custom keys, in contrast to method {@link #newInstance(String, List, boolean)}.
     * When {@code unique} is true, the created index will also be {@code sparse}.
     *
     * @param name the name of the index.
     * @param keys the keys which form the index.
     * @param unique whether or not the index should be unique.
     * @return the index.
     * @see #newInstance(String, List, boolean)
     */
    public static Index newInstanceWithCustomKeys(final String name, final List<IndexKey> keys, final boolean
            unique) {

        final BsonDocument keysDocument = createKeysDocument(requireNonNull(keys));
        return Index.of(keysDocument, name, unique, unique, BACKGROUND_OPTION_DEFAULT);
    }

    private static BsonDocument createKeysDocument(final List<IndexKey> keys) {
        final BsonDocument keysDocument = new BsonDocument();
        for (final IndexKey key : keys) {
            keysDocument.append(key.getFieldName(), key.getBsonValue());
        }
        return keysDocument;
    }

    private static List<IndexKey> createDefaultKeys(final List<String> fieldNames) {
        return fieldNames.stream()
                .map(DefaultIndexKey::of)
                .collect(Collectors.toList());
    }

}
