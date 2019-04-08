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
package org.eclipse.ditto.services.utils.persistence.mongo.namespace;

import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.bson.Document;

/**
 * Representation of a namespace's content in a collection. Consists of a collection name and a filter in BSON format.
 */
@Immutable
public final class MongoNamespaceSelection {

    private final String collectionName;
    private final Document filter;

    private MongoNamespaceSelection(final String collectionName, final Document filter) {
        this.collectionName = collectionName;
        this.filter = filter;
    }

    /**
     * Creates a namespace selection.
     *
     * @param collectionName name of the collection.
     * @param filter filter of documents in the namespace.
     * @return a new namespace selection.
     */
    public static MongoNamespaceSelection of(final String collectionName, final Document filter) {
        return new MongoNamespaceSelection(collectionName, new Document(filter));
    }

    /**
     * @return name of the collection.
     */
    public String getCollectionName() {
        return collectionName;
    }

    /**
     * @return filter of documents in the namespace.
     */
    public Document getFilter() {
        return new Document(filter);
    }

    /**
     * @return whether all documents in the collection belong to the namespace.
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
        final MongoNamespaceSelection that = (MongoNamespaceSelection) o;
        return Objects.equals(collectionName, that.collectionName) && Objects.equals(filter, that.filter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(collectionName, filter);
    }

    /**
     * Returns the collection name and indicates by text whether to drop or to filter.
     *
     * @return if {@link #isEntireCollection()} is {@code true}: {@code "COLLECTION NAME (to drop)"}, else
     * {@code "COLLECTION NAME (to filter)"}.
     */
    @Override
    public String toString() {
        return String.format("%s (%s)", collectionName, isEntireCollection() ? "to drop" : "to filter");
    }

}
