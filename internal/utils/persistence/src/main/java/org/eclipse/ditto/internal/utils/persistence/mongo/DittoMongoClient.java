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
package org.eclipse.ditto.internal.utils.persistence.mongo;

import javax.annotation.concurrent.NotThreadSafe;

import org.bson.Document;

import com.mongodb.MongoClientSettings;
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
     * Returns the settings that this client uses additionally to the
     * {@link MongoClientSettings}.
     *
     * @return the Ditto specific settings of this client.
     */
    DittoMongoClientSettings getDittoSettings();

    /**
     * Returns the settings with which the mongo client was built.
     *
     * @return the settings of the mongo client.
     */
    MongoClientSettings getClientSettings();

    /**
     * Returns the max wire version of the MongoDB server, or 0 if the client is disconnected.
     *
     * @return the max wire version.
     */
    int getMaxWireVersion();

}
