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

import java.io.Closeable;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import org.bson.Document;
import org.eclipse.ditto.services.utils.persistence.mongo.DittoMongoClient;

import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.DeleteManyModel;
import com.mongodb.client.model.WriteModel;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;

import akka.NotUsed;
import akka.japi.pf.Match;
import akka.stream.javadsl.Source;

/**
 * MongoDB operations on the level of namespaces.
 */
public final class MongoNamespaceOps implements NamespaceOps<MongoNamespaceSelection>, Closeable {

    @Nullable private final DittoMongoClient mongoClient;
    private final MongoDatabase db;

    private MongoNamespaceOps(final MongoDatabase db) {
        mongoClient = null;
        this.db = db;
    }

    private MongoNamespaceOps(final DittoMongoClient theMongoClient) {
        mongoClient = theMongoClient;
        db = mongoClient.getDefaultDatabase();
    }

    /**
     * Create a new NamespaceOps object.
     *
     * @param db the database to operate on.
     * @return a new NamespaceOps object.
     */
    public static MongoNamespaceOps of(final MongoDatabase db) {
        return new MongoNamespaceOps(db);
    }

    /**
     * Returns an instance of {@code MongoNamespaceOps}.
     *
     * @param mongoClient provides the database to be used for operations. Will be closed with a call to {@link #close}.
     * @return the instance.
     * @throws NullPointerException if {@code mongoDbClientWrapper} is {@code null}.
     */
    public static MongoNamespaceOps of(final DittoMongoClient mongoClient) {
        return new MongoNamespaceOps(mongoClient);
    }

    @Override
    public Source<Optional<Throwable>, NotUsed> purge(final MongoNamespaceSelection selection) {
        final MongoCollection<Document> collection = db.getCollection(selection.getCollectionName());
        if (selection.isEntireCollection()) {
            return Source.fromPublisher(collection.drop())
                    .map(success -> Optional.empty());
        } else {
            // https://stackoverflow.com/a/33164008
            // claims unordered bulk ops halve MongoDB load
            final List<WriteModel<Document>> writeModel =
                    Collections.singletonList(new DeleteManyModel<>(selection.getFilter()));
            final BulkWriteOptions options = new BulkWriteOptions().ordered(false);
            return Source.fromPublisher(collection.bulkWrite(writeModel, options))
                    .map(result -> Optional.<Throwable>empty())
                    .recover(Match.<Throwable, Optional<Throwable>>matchAny(Optional::of).build());
        }
    }

    @Override
    public void close() {
        if (null != mongoClient) {
            mongoClient.close();
        }
    }

}
