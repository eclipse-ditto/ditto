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
package org.eclipse.ditto.services.thingsearch.persistence.write.impl;

import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.LAST_SUCCESSFUL_SYNC_COLLECTION_NAME;

import java.time.Instant;
import java.util.Date;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.Nullable;

import org.bson.Document;
import org.eclipse.ditto.services.thingsearch.persistence.MongoClientWrapper;
import org.eclipse.ditto.services.thingsearch.persistence.write.ThingsSearchSyncPersistence;
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
 * MongoDB implementation of {@link ThingsSearchSyncPersistence}.
 */
public final class MongoThingsSearchSyncPersistence implements ThingsSearchSyncPersistence {

    /**
     * The minimum size a capped collection claims in MongoDB.
     */
    private static final long MIN_CAPPED_COLLECTION_SIZE_IN_BYTES = 4096;

    private static final long COLLECTION_CREATE_TIMEOUT_SECS = 20;

    private static final String FIELD_TIMESTAMP = "ts";

    /**
     * The logger.
     */
    private final LoggingAdapter log;
    private final MongoClientWrapper clientWrapper;
    private final Materializer mat;
    private final MongoCollection<Document> lastSuccessfulSearchSyncCollection;

    private volatile boolean initialized = false;

    /**
     * Constructor.
     *
     * @param clientWrapper the client wrapper holding the connection information.
     * @param log the logger to use for logging.
     * @param mat the {@link Materializer} to be used for stream
     */
    public MongoThingsSearchSyncPersistence(final MongoClientWrapper clientWrapper,
            final LoggingAdapter log, final Materializer mat) {
        this.log = log;
        this.clientWrapper = clientWrapper;
        this.mat = mat;

        lastSuccessfulSearchSyncCollection = clientWrapper.getDatabase().getCollection(
                LAST_SUCCESSFUL_SYNC_COLLECTION_NAME);
    }

    @Override
    public void init() {
        final CreateCollectionOptions collectionOptions = new CreateCollectionOptions()
                .autoIndex(false)
                .capped(true)
                .sizeInBytes(MIN_CAPPED_COLLECTION_SIZE_IN_BYTES)
                .maxDocuments(1);
        final Publisher<Success> publisher = clientWrapper.getDatabase()
                .createCollection(LAST_SUCCESSFUL_SYNC_COLLECTION_NAME, collectionOptions);
        final Source<Success, NotUsed> source = Source.fromPublisher(publisher);
        final CompletionStage<Success> done = source.runWith(Sink.head(), mat);
        try {
            done.toCompletableFuture().get(COLLECTION_CREATE_TIMEOUT_SECS, TimeUnit.SECONDS);
            log.debug("Successfully created collection: {}", LAST_SUCCESSFUL_SYNC_COLLECTION_NAME);
        } catch (final InterruptedException | TimeoutException e) {
            throw new IllegalStateException(e);
        } catch (final ExecutionException e) {
            if (!isCollectionAlreadyExistsError(e.getCause())) {
                throw new IllegalStateException(e);
            } else {
                log.debug("Collection already exists: {}", LAST_SUCCESSFUL_SYNC_COLLECTION_NAME);
            }
        }

        initialized = true;
    }

    private static boolean isCollectionAlreadyExistsError(@Nullable final Throwable t) {
        if (t instanceof MongoCommandException) {
            final MongoCommandException commandException = (MongoCommandException) t;
            final int collectionAlreadyExistsErrorCode = 48;
            if (commandException.getErrorCode() == collectionAlreadyExistsErrorCode) {
                return true;
            }
        }

        return false;
    }

    @Override
    public Source<NotUsed, NotUsed> updateLastSuccessfulSyncTimestamp(final Instant timestamp) {
        checkInitialized();

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
    public Source<Instant, NotUsed> retrieveLastSuccessfulSyncTimestamp(final Instant defaultTimestamp) {
        checkInitialized();

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

    private void checkInitialized() {
        if (!initialized) {
            throw new IllegalStateException("Not yet initialized!");
        }
    }
}
