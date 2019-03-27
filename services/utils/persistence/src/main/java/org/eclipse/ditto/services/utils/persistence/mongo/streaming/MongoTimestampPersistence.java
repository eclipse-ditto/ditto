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
package org.eclipse.ditto.services.utils.persistence.mongo.streaming;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import org.bson.Document;
import org.eclipse.ditto.services.utils.akka.streaming.TimestampPersistence;
import org.eclipse.ditto.services.utils.persistence.mongo.DittoMongoClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.MongoCommandException;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import com.mongodb.reactivestreams.client.Success;

import akka.NotUsed;
import akka.japi.pf.PFBuilder;
import akka.stream.ActorMaterializer;
import akka.stream.Attributes;
import akka.stream.javadsl.BroadcastHub;
import akka.stream.javadsl.RestartSource;
import akka.stream.javadsl.Source;

/**
 * MongoDB implementation of {@link TimestampPersistence}.
 */
public final class MongoTimestampPersistence implements TimestampPersistence {

    private static final Duration BACKOFF_MIN = Duration.ofSeconds(1L);

    private static final Duration BACKOFF_MAX = Duration.ofMinutes(2L);

    private static final Document SORT_BY_ID_DESC = new Document().append("_id", -1);

    /**
     * The minimum size a capped collection claims in MongoDB.
     */
    private static final long MIN_CAPPED_COLLECTION_SIZE_IN_BYTES = 4096;

    private static final String FIELD_TIMESTAMP = "ts";
    /**
     * MongoDB error code if a collection that is being created already exists
     */
    private static final int COLLECTION_ALREADY_EXISTS_ERROR_CODE = 48;

    /**
     * The logger.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(MongoTimestampPersistence.class);
    private final Source<MongoCollection, NotUsed> lastSuccessfulSearchSyncCollection;

    /**
     * Constructor.
     *
     * @param lastSuccessfulSearchSyncCollection the collection in which the last successful sync timestamps can be
     * stored.
     */
    private MongoTimestampPersistence(final Source<MongoCollection, NotUsed> lastSuccessfulSearchSyncCollection) {

        this.lastSuccessfulSearchSyncCollection = lastSuccessfulSearchSyncCollection;
    }

    /**
     * Creates a new initialized instance.
     *
     * @param collectionName The name of the collection.
     * @param mongoClient the client wrapper holding the connection information.
     * @param materializer an actor materializer to materialize the restart-source of the sync timestamp collection.
     * @return a new initialized instance.
     */
    public static MongoTimestampPersistence initializedInstance(final String collectionName,
            final DittoMongoClient mongoClient, final ActorMaterializer materializer) {
        final Source<MongoCollection, NotUsed> lastSuccessfulSearchSyncCollection =
                createOrGetCappedCollection(mongoClient.getDefaultDatabase(), collectionName,
                        MIN_CAPPED_COLLECTION_SIZE_IN_BYTES, materializer);

        return new MongoTimestampPersistence(lastSuccessfulSearchSyncCollection);
    }

    @Override
    public Source<NotUsed, NotUsed> setTimestamp(final Instant timestamp) {
        final Date mongoStorableDate = Date.from(timestamp);

        final Document toStore = new Document().append(FIELD_TIMESTAMP, mongoStorableDate);

        return getCollection()
                .flatMapConcat(collection -> Source.fromPublisher(collection.insertOne(toStore)))
                .map(success -> {
                    LOGGER.debug("Successfully inserted timestamp for search synchronization: <{}>.", timestamp);
                    return NotUsed.getInstance();
                });
    }

    /**
     * @return the underlying collection in a future for tests
     */
    @SuppressWarnings("unchecked")
    Source<MongoCollection<Document>, NotUsed> getCollection() {
        return lastSuccessfulSearchSyncCollection.take(1)
                .map(document -> (MongoCollection<Document>) document);
    }

    @Override
    public Source<Optional<Instant>, NotUsed> getTimestampAsync() {
        return getCollection()
                .flatMapConcat(collection -> Source.fromPublisher(collection.find().sort(SORT_BY_ID_DESC).limit(1)))
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
     * @param database The database to use.
     * @param collectionName The name of the capped collection that should be created.
     * @param cappedCollectionSizeInBytes The size in bytes of the collection that should be created.
     * @param materializer The actor materializer to pre-materialize the restart source.
     * @return Returns the created or retrieved collection.
     */
    private static Source<MongoCollection, NotUsed> createOrGetCappedCollection(
            final MongoDatabase database,
            final String collectionName,
            final long cappedCollectionSizeInBytes,
            final ActorMaterializer materializer) {

        final Source<Success, NotUsed> createCollectionSource =
                repeatableCreateCappedCollectionSource(database, collectionName, cappedCollectionSizeInBytes);

        final Source<MongoCollection, NotUsed> infiniteCollectionSource =
                createCollectionSource.map(success -> database.getCollection(collectionName))
                        .flatMapConcat(Source::repeat);

        final Source<MongoCollection, NotUsed> restartSource =
                RestartSource.withBackoff(BACKOFF_MIN, BACKOFF_MAX, 1.0, () -> infiniteCollectionSource);

        return restartSource.runWith(BroadcastHub.of(MongoCollection.class, 1), materializer);
    }

    private static Source<Success, NotUsed> repeatableCreateCappedCollectionSource(
            final MongoDatabase database,
            final String collectionName,
            final long cappedCollectionSizeInBytes) {

        final CreateCollectionOptions collectionOptions = new CreateCollectionOptions()
                .capped(true)
                .sizeInBytes(cappedCollectionSizeInBytes)
                .maxDocuments(1);

        return Source.lazily(() ->
                Source.fromPublisher(
                        database.createCollection(collectionName, collectionOptions)))
                .mapMaterializedValue(whatever -> NotUsed.getInstance())
                .withAttributes(Attributes.inputBuffer(1, 1))
                .recoverWithRetries(1, new PFBuilder<Throwable, Source<Success, NotUsed>>()
                        .match(MongoCommandException.class,
                                MongoTimestampPersistence::isCollectionAlreadyExistsError,
                                error -> Source.single(Success.SUCCESS))
                        .build());

    }

    private static boolean isCollectionAlreadyExistsError(final MongoCommandException error) {
        return error.getErrorCode() == COLLECTION_ALREADY_EXISTS_ERROR_CODE;
    }

}
