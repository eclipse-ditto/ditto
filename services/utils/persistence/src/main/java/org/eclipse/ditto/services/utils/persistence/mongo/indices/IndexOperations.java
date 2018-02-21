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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.concurrent.Immutable;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.MongoCommandException;
import com.mongodb.client.model.IndexModel;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import com.mongodb.reactivestreams.client.Success;

import akka.NotUsed;
import akka.japi.pf.PFBuilder;
import akka.stream.javadsl.Source;
import scala.PartialFunction;

/**
 * Provides index operations for MongoDB.
 */
@Immutable
public final class IndexOperations {

    private static final Logger LOGGER = LoggerFactory.getLogger(IndexOperations.class);

    private static final String DEFAULT_INDEX_NAME = "_id_";

    private final MongoDatabase db;

    private IndexOperations(final MongoDatabase db) {
        this.db = db;
    }

    /**
     * Returns a new {@link IndexOperations}.
     *
     * @param db the mongo database to use for index operations.
     * @return the index operations.
     */
    public static IndexOperations of(final MongoDatabase db) {
        requireNonNull(db);
        return new IndexOperations(db);
    }


    /**
     * Drops the specified index. Does <strong>not</strong> throw an exception if the index does not exist.
     *
     * @param collectionName the name of the collection containing the index.
     * @param indexName the name of the index.
     * @return a source which emits {@link Success}.
     */
    public Source<Success, NotUsed> dropIndex(final String collectionName, final String indexName) {
        return Source.fromPublisher(getCollection(collectionName).dropIndex(indexName))
                .recoverWith(buildDropIndexRecovery(indexName));
    }

    /**
     * Creates the specified index. Throws an exception if the index with the same name already exists.
     *
     * <p>
     * Just does nothing if another index for the same keys with conflicting options already exists. For details, see
     * the <a href="https://docs.mongodb.com/manual/reference/method/db.collection.createIndex/">MongoDB documentation</a>.
     * </p>
     *
     * @param collectionName the name of the collection containing the index.
     * @param index the index.
     * @return a source which emits {@link Success}.
     */
    public Source<Success, NotUsed> createIndex(final String collectionName, final Index index) {
        final IndexModel indexModel = index.toIndexModel();
        return Source.fromPublisher(getCollection(collectionName).createIndex(indexModel.getKeys(), indexModel
                .getOptions())).map(unused -> Success.SUCCESS);
    }

    /**
     * Gets all indices defined on the given collection, including the default index defined on "_id".
     *
     * @param collectionName the name of the collection.
     * @return a source which emits the list of found indices.
     */
    public Source<List<Index>, NotUsed> getIndices(final String collectionName) {
        return Source.fromPublisher(getCollection(collectionName).listIndexes())
                .map(Index::indexInfoOf)
                .fold(new ArrayList<Index>(), (aggregate, element) -> {
                    aggregate.add(element);
                    return aggregate;
                });
    }

    /**
     * Gets all indices defined on the given collection, excluding the default index defined on "_id".
     *
     * @param collectionName the name of the collection.
     * @return a source which emits the list of found indices.
     */
    public Source<List<Index>, NotUsed> getIndicesExceptDefaultIndex(final String collectionName) {
        return getIndices(collectionName)
                .map(indices -> indices.stream()
                        .filter(indexInfo -> !DEFAULT_INDEX_NAME.equals(indexInfo.getName()))
                        .collect(Collectors.toList()));
    }


    private MongoCollection<Document> getCollection(final String collectionName) {
        return db.getCollection(collectionName);
    }

    private static PartialFunction<Throwable, Source<Success, NotUsed>> buildDropIndexRecovery(
            final String indexDescription) {
        return new PFBuilder<Throwable, Source<Success, NotUsed>>()
                .match(MongoCommandException.class, IndexOperations::isIndexNotFound, throwable -> {
                    LOGGER.debug("Index <{}> could not be dropped because it does not exist (anymore).",
                            indexDescription);
                    return Source.single(Success.SUCCESS);
                })
                .build();
    }

    private static boolean isIndexNotFound(final MongoCommandException e) {
        return e.getErrorCode() == 27;
    }

}
