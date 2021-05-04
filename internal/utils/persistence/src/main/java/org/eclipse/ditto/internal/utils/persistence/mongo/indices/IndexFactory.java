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

import static java.util.Objects.requireNonNull;

import java.util.Collections;
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
     * @param unique whether or not the index should be unique AND sparse.
     * @return the index.
     * @see #newInstanceWithCustomKeys(String, List, boolean)
     */
    public static Index newInstance(final String name, final List<String> fields, final boolean unique) {
        final List<IndexKey> keys = createDefaultKeys(requireNonNull(fields));
        return newInstanceWithCustomKeys(name, keys, unique);
    }

    /**
     * Returns a new {@link Index} with all fields having default (i.e. ascending) index direction.
     *
     * @param name the name of the index.
     * @param fields the fields which form the index.
     * @param unique whether or not the index should be unique.
     * @param sparse whether or not the index should be sparse.
     * @return the index.
     * @see #newInstanceWithCustomKeys(String, List, boolean)
     */
    public static Index newInstance(final String name, final List<String> fields, final boolean unique,
            final boolean sparse) {
        final BsonDocument keys = createKeysDocument(createDefaultKeys(requireNonNull(fields)));
        return Index.of(keys, name, unique, sparse, BACKGROUND_OPTION_DEFAULT);
    }

    /**
     * Returns a new {@link Index} with custom keys, in contrast to method {@link #newInstance(String, List, boolean)}.
     * When {@code unique} is true, the created index will also be {@code sparse}.
     *
     * @param name the name of the index.
     * @param keys the keys which form the index.
     * @param unique whether or not the index should be unique AND sparse.
     * @return the index.
     * @see #newInstance(String, List, boolean)
     */
    public static Index newInstanceWithCustomKeys(final String name, final List<IndexKey> keys, final boolean unique) {
        final BsonDocument keysDocument = createKeysDocument(requireNonNull(keys));
        return Index.of(keysDocument, name, unique, unique, BACKGROUND_OPTION_DEFAULT);
    }

    /**
     * Return a new {@link Index} for background deletion of documents.
     *
     * @param name the name of the index.
     * @param field the field containing the epoch timestamp for deletion.
     * @param expireAfterSeconds how many seconds the deletion threshold lies before the current epoch second.
     * @return the index.
     */
    public static Index newExpirationIndex(final String name, final String field, final long expireAfterSeconds) {
        final IndexKey key = DefaultIndexKey.of(field);
        final BsonDocument keysDocument = createKeysDocument(Collections.singletonList(key));
        return Index.of(keysDocument, name, false, true, true).withExpireAfterSeconds(expireAfterSeconds);
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
