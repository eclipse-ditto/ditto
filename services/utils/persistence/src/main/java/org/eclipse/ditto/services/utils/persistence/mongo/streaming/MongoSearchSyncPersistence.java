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
package org.eclipse.ditto.services.utils.persistence.mongo.streaming;

import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
    private final MongoCollection<Document> lastSuccessfulSearchSyncCollection;

    /**
     * Constructor.
     *
     * @param lastSuccessfulSearchSyncCollection the collection in which the last successful sync timestamps can be
     * stored.
     * @param mat the {@link Materializer} to be used for stream
     */
    private MongoSearchSyncPersistence(final MongoCollection<Document> lastSuccessfulSearchSyncCollection,
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
        final MongoCollection<Document> lastSuccessfulSearchSyncCollection = createOrGetCappedCollection(
                clientWrapper,
                collectionName,
                MIN_CAPPED_COLLECTION_SIZE_IN_BYTES,
                BLOCKING_TIMEOUT_SECS,
                materializer);
        return new MongoSearchSyncPersistence(lastSuccessfulSearchSyncCollection, materializer);
    }


    @Override
    public Source<NotUsed, NotUsed> updateLastSuccessfulStreamEnd(final Instant timestamp) {
        final Date mongoStorableDate = Date.from(timestamp);

        final Document toStore = new Document()
                .append(FIELD_TIMESTAMP, mongoStorableDate);

        return Source.fromPublisher(lastSuccessfulSearchSyncCollection.insertOne(toStore))
                .map(success -> {
                    LOGGER.debug("Successfully inserted timestamp for search synchronization: <{}>.", timestamp);
                    return NotUsed.getInstance();
                });
    }

    @Override
    public Optional<Instant> retrieveLastSuccessfulStreamEnd() {
        final Source<Optional<Instant>, NotUsed> source = retrieveLastSuccessfulStreamEndAsync();
        final CompletionStage<Optional<Instant>> done = source.runWith(Sink.head(), mat);
        try {
            return done.toCompletableFuture().get(BLOCKING_TIMEOUT_SECS, TimeUnit.SECONDS);
        } catch (final InterruptedException | ExecutionException | TimeoutException e) {
            throw new IllegalStateException(e);
        }
    }

    private Source<Optional<Instant>, NotUsed> retrieveLastSuccessfulStreamEndAsync() {
        return Source.fromPublisher(lastSuccessfulSearchSyncCollection.find())
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
     * @param createTimeoutSeconds How long to wait for success of the create operation.
     * @param materializer The {@link akka.stream.Materializer} to be used for streams
     * @return Returns the created or retrieved collection.
     */
    private static MongoCollection<Document> createOrGetCappedCollection(
            final MongoClientWrapper clientWrapper,
            final String collectionName,
            final long cappedCollectionSizeInBytes,
            final long createTimeoutSeconds,
            final Materializer materializer) {
        createCappedCollectionIfItDoesNotExist(clientWrapper, collectionName, cappedCollectionSizeInBytes,
                createTimeoutSeconds, materializer);
        return clientWrapper
                .getDatabase()
                .getCollection(collectionName);
    }

    private static void createCappedCollectionIfItDoesNotExist(
            final MongoClientWrapper clientWrapper,
            final String collectionName,
            final long cappedCollectionSizeInBytes,
            final long createTimeoutSeconds,
            final Materializer materializer) {
        try {
            final CreateCollectionOptions collectionOptions = new CreateCollectionOptions()
                    .autoIndex(false)
                    .capped(true)
                    .sizeInBytes(cappedCollectionSizeInBytes)
                    .maxDocuments(1);
            final Publisher<Success> publisher = clientWrapper.getDatabase()
                    .createCollection(collectionName, collectionOptions);
            final Source<Success, NotUsed> source = Source.fromPublisher(publisher);
            final CompletionStage<Success> done = source.runWith(Sink.head(), materializer);
            done.toCompletableFuture().get(createTimeoutSeconds, TimeUnit.SECONDS);
            LOGGER.debug("Successfully created collection: <{}>.", collectionName);
        } catch (final InterruptedException | TimeoutException e) {
            throw new IllegalStateException(e);
        } catch (final ExecutionException e) {
            if (isCollectionAlreadyExistsError(e.getCause())) {
                LOGGER.debug("Collection already exists: <{}>.", collectionName);
            } else {
                throw new IllegalStateException(e);
            }
        }
    }

    private static boolean isCollectionAlreadyExistsError(@Nullable final Throwable t) {
        if (t instanceof MongoCommandException) {
            final MongoCommandException commandException = (MongoCommandException) t;
            return commandException.getErrorCode() == COLLECTION_ALREADY_EXISTS_ERROR_CODE;
        }
        return false;
    }
}
