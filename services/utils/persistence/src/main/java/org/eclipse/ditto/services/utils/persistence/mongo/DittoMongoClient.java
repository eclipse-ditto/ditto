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
package org.eclipse.ditto.services.utils.persistence.mongo;

import javax.annotation.concurrent.NotThreadSafe;

import org.bson.Document;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;

/**
 * An extended implementation of {@link com.mongodb.reactivestreams.client.MongoDatabase}.
 */
@NotThreadSafe
public interface DittoMongoClient extends MongoClient {

    /**
     * Returns the default database of this client.
     *
     * @return the default database.
     */
    MongoDatabase getDefaultDatabase();

    /**
     * This is a convenience method for directly getting the collection with the given name from the default database of
     * this client.
     *
     * @param collectionName the name of the collection to return.
     * @return the collection.
     * @throws NullPointerException if {@code collectionName} is {@code null}.
     * @see com.mongodb.reactivestreams.client.MongoDatabase#getCollection(String)
     */
    MongoCollection<Document> getCollection(CharSequence collectionName);

    /**
     * Returns the the settings that this client uses additionally to the
     * {@link com.mongodb.async.client.MongoClientSettings}.
     *
     * @return the Ditto specific settings of this client.
     */
    DittoMongoClientSettings getDittoSettings();

}
