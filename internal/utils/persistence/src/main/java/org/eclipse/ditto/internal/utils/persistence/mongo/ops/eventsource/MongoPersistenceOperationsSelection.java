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

import static java.util.Objects.requireNonNull;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.bson.Document;

/**
 * Representation a selection for a MongoDB ops operation. Consists of a collection name and a filter in BSON format.
 */
@Immutable
final class MongoPersistenceOperationsSelection {

    private final String collectionName;
    private final Document filter;

    private MongoPersistenceOperationsSelection(final String collectionName, final Document filter) {
        this.collectionName = requireNonNull(collectionName);
        this.filter = filter;
    }

    /**
     * Creates a new instance.
     *
     * @param collectionName name of the collection.
     * @param filter filter of documents in the namespace.
     * @return the instance.
     */
    public static MongoPersistenceOperationsSelection of(final String collectionName, final Document filter) {
        return new MongoPersistenceOperationsSelection(collectionName, filter);
    }

    /**
     * @return name of the collection.
     */
    public String getCollectionName() {
        return collectionName;
    }

    /**
     * @return filter of documents.
     */
    public Document getFilter() {
        return new Document(filter);
    }

    /**
     * @return whether all documents of the collection are affected.
     */
    public boolean isEntireCollection() {
        return filter.isEmpty();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final MongoPersistenceOperationsSelection that = (MongoPersistenceOperationsSelection) o;
        return Objects.equals(collectionName, that.collectionName) && Objects.equals(filter, that.filter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(collectionName, filter);
    }

    /**
     * Returns the collection name and indicates by text whether the complete collection is affected or if it is
     * filtered.
     *
     * @return the string representation.
     */
    @Override
    public String toString() {
        return String.format("%s (%s)", collectionName, isEntireCollection() ? "complete" : "filtered: " + filter);
    }

}
