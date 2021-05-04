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
package org.eclipse.ditto.internal.utils.persistence.mongo.streaming;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import javax.annotation.Nullable;

import org.bson.Document;
import org.eclipse.ditto.internal.utils.akka.streaming.TimestampPersistence;
import org.eclipse.ditto.internal.utils.persistence.mongo.DittoMongoClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.MongoCommandException;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;

import akka.Done;
import akka.NotUsed;
import akka.japi.Pair;
import akka.japi.pf.PFBuilder;
import akka.stream.Attributes;
import akka.stream.Materializer;
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

    /**
     * MongoDB error code if a collection that is being created already exists
     */
    private static final int COLLECTION_ALREADY_EXISTS_ERROR_CODE = 48;

    private static final String FIELD_TIMESTAMP = "ts";

    private static final String FIELD_TAG = "tg";

    /**
     * The logger.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(MongoTimestampPersistence.class);
    private final Source<MongoCollection, NotUsed> collectionSource;

    /**
     * Constructor.
     *
     * @param collectionSource the source of a capped collection in which timestamps are stored.
     */
    private MongoTimestampPersistence(final Source<MongoCollection, NotUsed> collectionSource) {
        this.collectionSource = collectionSource;
    }

    /**
     * Creates a new initialized instance.
     *
     * @param collectionName The name of the collection.
     * @param mongoClient the client wrapper holding the connection information.
     * @param materializer an actor materializer to materialize the restart-source of the timestamp collection.
     * @return a new initialized instance.
     */
    public static MongoTimestampPersistence initializedInstance(final String collectionName,
            final DittoMongoClient mongoClient, final Materializer materializer) {
        final Source<MongoCollection, NotUsed> collectionSource =
                createOrGetCappedCollection(mongoClient.getDefaultDatabase(), collectionName,
                        MIN_CAPPED_COLLECTION_SIZE_IN_BYTES, materializer);

        return new MongoTimestampPersistence(collectionSource);
    }

    @Override
    public Source<NotUsed, NotUsed> setTimestamp(final Instant timestamp) {
        return setTaggedTimestamp(timestamp, null).map(done -> NotUsed.getInstance());
    }

    @Override
    public Source<Done, NotUsed> setTaggedTimestamp(final Instant timestamp, @Nullable final String tag) {
        final Document toStore = new Document()
                .append(FIELD_TIMESTAMP, Date.from(timestamp))
                .append(FIELD_TAG, tag);
        return getCollection()
                .flatMapConcat(collection -> Source.fromPublisher(collection.insertOne(toStore)))
                .map(success -> {
                    LOGGER.debug("Successfully inserted <{}> tagged <{}>.", timestamp, tag);
                    return Done.done();
                });
    }

    /**
     * Get 1 collection from the source of capped collections.
     * Contains an unchecked cast due eventually to the inability to create BroadcastHub with a parametric type argument
     * {@code MongoCollection<Document>}.
     *
     * @return the underlying collection in a future for tests
     */
    @SuppressWarnings("unchecked")
    Source<MongoCollection<Document>, NotUsed> getCollection() {
        return collectionSource.take(1)
                .map(document -> (MongoCollection<Document>) document);
    }

    @Override
    public Source<Optional<Instant>, NotUsed> getTimestampAsync() {
        return getTaggedTimestamp().map(optional -> optional.map(Pair::first));
    }

    @Override
    public Source<Optional<Pair<Instant, String>>, NotUsed> getTaggedTimestamp() {
        return getCollection()
                .flatMapConcat(collection -> Source.fromPublisher(collection.find().sort(SORT_BY_ID_DESC).limit(1)))
                .flatMapConcat(doc -> {
                    final Date date = doc.getDate(FIELD_TIMESTAMP);
                    final Instant timestamp = date.toInstant();
                    final String tag = doc.getString(FIELD_TAG);
                    LOGGER.debug("Returning timestamp <{}> tagged <{}>.", timestamp, tag);
                    return Source.single(Optional.of(Pair.create(timestamp, tag)));
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
            final Materializer materializer) {

        final Source<Done, NotUsed> createCollectionSource =
                repeatableCreateCappedCollectionSource(database, collectionName, cappedCollectionSizeInBytes);

        final Source<MongoCollection, NotUsed> infiniteCollectionSource =
                createCollectionSource.map(success -> database.getCollection(collectionName))
                        .flatMapConcat(Source::repeat);

        final Source<MongoCollection, NotUsed> restartSource =
                RestartSource.withBackoff(BACKOFF_MIN, BACKOFF_MAX, 1.0, () -> infiniteCollectionSource);

        // pre-materialize source with BroadcastHub so that a successfully obtained capped collection is reused
        // until the stream fails, whereupon it gets recreated with backoff.
        return restartSource.runWith(BroadcastHub.of(MongoCollection.class, 1), materializer);
    }

    private static Source<Done, NotUsed> repeatableCreateCappedCollectionSource(
            final MongoDatabase database,
            final String collectionName,
            final long cappedCollectionSizeInBytes) {

        final CreateCollectionOptions collectionOptions = new CreateCollectionOptions()
                .capped(true)
                .sizeInBytes(cappedCollectionSizeInBytes)
                .maxDocuments(1);

        return Source.lazySource(
                () -> Source.fromPublisher(database.createCollection(collectionName, collectionOptions)))
                .mapMaterializedValue(whatever -> NotUsed.getInstance())
                .map(nullValue -> Done.done())
                .withAttributes(Attributes.inputBuffer(1, 1))
                .recoverWithRetries(1, new PFBuilder<Throwable, Source<Done, NotUsed>>()
                        .match(MongoCommandException.class,
                                MongoTimestampPersistence::isCollectionAlreadyExistsError,
                                error -> Source.single(Done.done()))
                        .build());

    }

    private static boolean isCollectionAlreadyExistsError(final MongoCommandException error) {
        return error.getErrorCode() == COLLECTION_ALREADY_EXISTS_ERROR_CODE;
    }

}
