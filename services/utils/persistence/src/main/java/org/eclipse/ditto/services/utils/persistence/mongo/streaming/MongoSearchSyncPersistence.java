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
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.Nullable;

import org.bson.Document;
import org.eclipse.ditto.services.utils.akka.streaming.StreamMetadataPersistence;
import org.eclipse.ditto.services.utils.persistence.mongo.MongoClientWrapper;
import org.reactivestreams.Publisher;

import com.mongodb.MongoCommandException;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.Success;

import akka.NotUsed;
import akka.event.LoggingAdapter;
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
    private final LoggingAdapter log;
    private final Materializer mat;
    private final MongoCollection<Document> lastSuccessfulSearchSyncCollection;

    /**
     * Constructor.
     *
     * @param lastSuccessfulSearchSyncCollection the collection in which the last successful sync timestamps can be
     * stored.
     * @param log the logger to use for logging.
     * @param mat the {@link Materializer} to be used for stream
     */
    private MongoSearchSyncPersistence(final MongoCollection<Document> lastSuccessfulSearchSyncCollection,
            final LoggingAdapter log, final Materializer mat) {
        this.log = log;
        this.mat = mat;
        this.lastSuccessfulSearchSyncCollection = lastSuccessfulSearchSyncCollection;
    }

    /**
     * Creates a new initialized instance.
     *
     * @param collectionName The name of the collection.
     * @param clientWrapper the client wrapper holding the connection information.
     * @param log the logger to use for logging.
     * @param materializer the {@link Materializer} to be used for stream
     */
    public static MongoSearchSyncPersistence initializedInstance(final String collectionName,
            final MongoClientWrapper
                    clientWrapper,
            final LoggingAdapter log, final Materializer materializer) {
        final MongoCollection<Document> lastSuccessfulSearchSyncCollection = createOrGetCappedCollection(
                clientWrapper,
                collectionName,
                MIN_CAPPED_COLLECTION_SIZE_IN_BYTES,
                BLOCKING_TIMEOUT_SECS,
                materializer, log);
        return new MongoSearchSyncPersistence(lastSuccessfulSearchSyncCollection, log, materializer);
    }


    @Override
    public Source<NotUsed, NotUsed> updateLastSuccessfulStreamEnd(final Instant timestamp) {
        final Date mongoStorableDate = Date.from(timestamp);

        final Document toStore = new Document()
                .append(FIELD_TIMESTAMP, mongoStorableDate);

        return Source.fromPublisher(lastSuccessfulSearchSyncCollection.insertOne(toStore))
                .map(success -> {
                    log.debug("Successfully inserted timestamp for search synchronization: <{}>", timestamp);
                    return NotUsed.getInstance();
                });
    }

    @Override
    public Instant retrieveLastSuccessfulStreamEnd(final Instant defaultTimestamp) {
        final Source<Instant, NotUsed> source = retrieveLastSuccessfulStreamEndAsync(defaultTimestamp);
        final CompletionStage<Instant> done = source.runWith(Sink.head(), mat);
        try {
            return done.toCompletableFuture().get(BLOCKING_TIMEOUT_SECS, TimeUnit.SECONDS);
        } catch (final InterruptedException | ExecutionException | TimeoutException e) {
            throw new IllegalStateException(e);
        }
    }

    private Source<Instant, NotUsed> retrieveLastSuccessfulStreamEndAsync(final Instant defaultTimestamp) {
        return Source.fromPublisher(lastSuccessfulSearchSyncCollection.find())
                .limit(1)
                .flatMapConcat(doc -> {
                    final Date date = doc.getDate(FIELD_TIMESTAMP);
                    final Instant timestamp = date.toInstant();
                    log.debug("Returning last timestamp of search synchronization: <{}>", timestamp);
                    return Source.single(timestamp);
                })
                .orElse(Source.single(defaultTimestamp));
    }

    /**
     * Creates the capped collection {@code collectionName} using {@code clientWrapper} if it doesn't exists yet.
     *
     * @param clientWrapper The client to use.
     * @param collectionName The name of the capped collection that should be created.
     * @param cappedCollectionSizeInBytes The size in bytes of the collection that should be created.
     * @param createTimeoutSeconds How long to wait for success of the create operation.
     * @param materializer The {@link akka.stream.Materializer} to be used for streams
     * @param log The logger used to output the result of the create operation.
     * @return Returns the created or retrieved collection.
     */
    private static MongoCollection<Document> createOrGetCappedCollection(
            final MongoClientWrapper clientWrapper,
            final String collectionName,
            final long cappedCollectionSizeInBytes,
            final long createTimeoutSeconds,
            final Materializer materializer,
            final LoggingAdapter log) {
        if (tryToCreateCappedCollection(clientWrapper, collectionName, cappedCollectionSizeInBytes,
                createTimeoutSeconds,
                materializer)) {
            log.debug("Successfully created collection: {}", collectionName);
        } else {
            log.debug("Collection already exists: {}", collectionName);
        }
        return clientWrapper
                .getDatabase()
                .getCollection(collectionName);
    }

    /**
     * Tries to create the capped collection {@code collectionName} using {@code clientWrapper}. Will throw an {@link
     * java.lang.IllegalStateException} if an unexpected error happened.
     *
     * @param clientWrapper The client to use.
     * @param collectionName The name of the capped collection that should be created.
     * @param cappedCollectionSizeInBytes The size in bytes of the collection that should be created.
     * @param createTimeoutSeconds How long to wait for success of the create operation.
     * @param materializer The {@link akka.stream.Materializer} to be used for streams
     */
    private static boolean tryToCreateCappedCollection(
            final MongoClientWrapper clientWrapper,
            final String collectionName,
            final long cappedCollectionSizeInBytes,
            final long createTimeoutSeconds,
            final Materializer materializer) {
        try {
            createCappedCollection(clientWrapper, collectionName, cappedCollectionSizeInBytes, createTimeoutSeconds,
                    materializer);
            return true;
        } catch (final InterruptedException | TimeoutException e) {
            throw new IllegalStateException(e);
        } catch (final ExecutionException e) {
            if (!isCollectionAlreadyExistsError(e.getCause())) {
                throw new IllegalStateException(e);
            } else {
                return false;
            }
        }
    }

    private static void createCappedCollection(final MongoClientWrapper clientWrapper,
            final String collectionName,
            final long cappedCollectionSizeInBytes,
            final long createTimeoutSeconds,
            final Materializer materializer) throws InterruptedException, ExecutionException, TimeoutException {
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
    }

    private static boolean isCollectionAlreadyExistsError(@Nullable final Throwable t) {
        if (t instanceof MongoCommandException) {
            final MongoCommandException commandException = (MongoCommandException) t;
            return commandException.getErrorCode() == COLLECTION_ALREADY_EXISTS_ERROR_CODE;
        }
        return false;
    }
}
