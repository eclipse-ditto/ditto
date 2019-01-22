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
package org.eclipse.ditto.services.utils.persistence.mongo.streaming;

import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;

import org.bson.Document;
import org.eclipse.ditto.services.utils.akka.streaming.StreamMetadataPersistence;
import org.eclipse.ditto.services.utils.persistence.mongo.MongoClientWrapper;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.MongoCommandException;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.Success;

import akka.NotUsed;
import akka.stream.Materializer;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;

/**
 * MongoDB implementation of {@link StreamMetadataPersistence}.
 */
public final class MongoSearchSyncPersistence implements StreamMetadataPersistence {

    /**
     * The minimum size a capped collection claims in MongoDB.
     */
    private static final long MIN_CAPPED_COLLECTION_SIZE_IN_BYTES = 4096;

    private static final long BLOCKING_TIMEOUT_SECS = 20;

    private static final String FIELD_TIMESTAMP = "ts";
    /**
     * MongoDB error code if a collection that is being created already exists
     */
    private static final int COLLECTION_ALREADY_EXISTS_ERROR_CODE = 48;

    /**
     * The logger.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(MongoSearchSyncPersistence.class);
    private final Materializer mat;
    private final CompletionStage<MongoCollection<Document>> lastSuccessfulSearchSyncCollection;

    /**
     * Constructor.
     *
     * @param lastSuccessfulSearchSyncCollection the collection in which the last successful sync timestamps can be
     * stored.
     * @param mat the {@link Materializer} to be used for stream
     */
    private MongoSearchSyncPersistence(
            final CompletionStage<MongoCollection<Document>> lastSuccessfulSearchSyncCollection,
            final Materializer mat) {
        this.mat = mat;
        this.lastSuccessfulSearchSyncCollection = lastSuccessfulSearchSyncCollection;
    }

    /**
     * Creates a new initialized instance.
     *
     * @param collectionName The name of the collection.
     * @param clientWrapper the client wrapper holding the connection information.
     * @param materializer the {@link Materializer} to be used for stream
     * @return a new initialized instance.
     */
    public static MongoSearchSyncPersistence initializedInstance(final String collectionName,
            final MongoClientWrapper clientWrapper, final Materializer materializer) {
        final CompletionStage<MongoCollection<Document>> lastSuccessfulSearchSyncCollection =
                createOrGetCappedCollection(
                        clientWrapper,
                        collectionName,
                        MIN_CAPPED_COLLECTION_SIZE_IN_BYTES,
                        materializer);
        return new MongoSearchSyncPersistence(lastSuccessfulSearchSyncCollection, materializer);
    }

    @Override
    public Source<NotUsed, NotUsed> updateLastSuccessfulStreamEnd(final Instant timestamp) {
        final Date mongoStorableDate = Date.from(timestamp);

        final Document toStore = new Document()
                .append(FIELD_TIMESTAMP, mongoStorableDate);

        return Source.fromCompletionStage(lastSuccessfulSearchSyncCollection)
                .flatMapConcat(collection -> Source.fromPublisher(collection.insertOne(toStore)))
                .map(success -> {
                    LOGGER.debug("Successfully inserted timestamp for search synchronization: <{}>.", timestamp);
                    return NotUsed.getInstance();
                });
    }

    @Override
    public Source<Optional<Instant>, NotUsed> retrieveLastSuccessfulStreamEnd() {
        return retrieveLastSuccessfulStreamEndAsync();
    }

    /**
     * @return the underlying collection in a future for tests
     */
    CompletionStage<MongoCollection<Document>> getCollection() {
        return lastSuccessfulSearchSyncCollection;
    }

    private Source<Optional<Instant>, NotUsed> retrieveLastSuccessfulStreamEndAsync() {
        return Source.fromCompletionStage(lastSuccessfulSearchSyncCollection)
                .flatMapConcat(collection -> Source.fromPublisher(collection.find()))
                .limit(1)
                .flatMapConcat(doc -> {
                    final Date date = doc.getDate(FIELD_TIMESTAMP);
                    final Instant timestamp = date.toInstant();
                    LOGGER.debug("Returning last timestamp of search synchronization: <{}>.", timestamp);
                    return Source.single(Optional.of(timestamp));
                })
                .orElse(Source.single(Optional.empty()));
    }

    /**
     * Creates the capped collection {@code collectionName} using {@code clientWrapper} if it doesn't exists yet.
     *
     * @param clientWrapper The client to use.
     * @param collectionName The name of the capped collection that should be created.
     * @param cappedCollectionSizeInBytes The size in bytes of the collection that should be created.
     * @param materializer The actor materializer to be used for streams
     * @return Returns the created or retrieved collection.
     */
    private static CompletionStage<MongoCollection<Document>> createOrGetCappedCollection(
            final MongoClientWrapper clientWrapper,
            final String collectionName,
            final long cappedCollectionSizeInBytes,
            final Materializer materializer) {
        final CompletionStage<Success> createCollectionFuture =
                createCappedCollectionIfItDoesNotExist(clientWrapper, collectionName, cappedCollectionSizeInBytes,
                        materializer);
        return createCollectionFuture.thenApply(success -> clientWrapper.getDatabase().getCollection(collectionName));
    }

    private static CompletionStage<Success> createCappedCollectionIfItDoesNotExist(
            final MongoClientWrapper clientWrapper,
            final String collectionName,
            final long cappedCollectionSizeInBytes,
            final Materializer materializer) {

        final CreateCollectionOptions collectionOptions = new CreateCollectionOptions()
                .capped(true)
                .sizeInBytes(cappedCollectionSizeInBytes)
                .maxDocuments(1);
        final Publisher<Success> publisher = clientWrapper.getDatabase()
                .createCollection(collectionName, collectionOptions);
        final Source<Success, NotUsed> source = Source.fromPublisher(publisher);
        return source.runWith(Sink.head(), materializer)
                .handle((result, error) -> {
                    if (error == null) {
                        LOGGER.debug("Successfully created collection: <{}>.", collectionName);
                    } else if (isCollectionAlreadyExistsError(error)) {
                        LOGGER.debug("Collection already exists: <{}>.", collectionName);
                    } else {
                        throw new IllegalStateException(error);
                    }
                    return result;
                });
    }

    private static boolean isCollectionAlreadyExistsError(@Nullable final Throwable t) {
        if (t instanceof MongoCommandException) {
            final MongoCommandException commandException = (MongoCommandException) t;
            return commandException.getErrorCode() == COLLECTION_ALREADY_EXISTS_ERROR_CODE;
        }
        return false;
    }
}
