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
package org.eclipse.ditto.services.utils.persistence.mongo.namespace;


import org.bson.Document;

/**
 * Representation of a namespace's content in a collection. Consists of a collection name and a filter in BSON format.
 */
public final class NamespaceSelection {

    private final String collectionName;
    private final Document filter;

    private NamespaceSelection(final String collectionName, final Document filter) {
        this.collectionName = collectionName;
        this.filter = filter;
    }

    /**
     * Create a namespace selection.
     *
     * @param collectionName name of the collection.
     * @param filter filter of documents in the namespace.
     * @return a new namespace selection.
     */
    public static NamespaceSelection of(final String collectionName, final Document filter) {
        return new NamespaceSelection(collectionName, filter);
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
        return filter;
    }

    /**
     * @return whether all documents in the collection belong to the namespace.
     */
    public boolean isEntireCollection() {
        return filter.isEmpty();
    }

    @Override
    public String toString() {
        return String.format("%s (%s)", collectionName, isEntireCollection() ? "to drop" : "to filter");
    }
}
