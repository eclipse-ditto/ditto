/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.thingsearch.service.persistence.write.streaming;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.bson.BsonDocument;
import org.eclipse.ditto.internal.utils.akka.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLogger;
import org.eclipse.ditto.internal.utils.metrics.DittoMetrics;
import org.eclipse.ditto.internal.utils.metrics.instruments.timer.StartedTimer;
import org.eclipse.ditto.thingsearch.service.common.config.PersistenceStreamConfig;
import org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.AbstractWriteModel;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.WriteResultAndErrors;

import com.mongodb.MongoBulkWriteException;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.DeleteManyModel;
import com.mongodb.client.model.DeleteOneModel;
import com.mongodb.client.model.ReplaceOneModel;
import com.mongodb.client.model.UpdateManyModel;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.WriteModel;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;

import akka.NotUsed;
import akka.japi.Pair;
import akka.japi.pf.PFBuilder;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Source;
import akka.stream.javadsl.SubSource;

/**
 * Flow mapping write models to write results via the search persistence.
 */
final class MongoSearchUpdaterFlow {

    private static final String TRACE_THING_BULK_UPDATE = "things_search_thing_bulkUpdate";
    private static final String COUNT_THING_BULK_UPDATES_PER_BULK = "things_search_thing_bulkUpdate_updates_per_bulk";
    private static final String UPDATE_TYPE_TAG = "update_type";

    private static final ThreadSafeDittoLogger LOGGER =
            DittoLoggerFactory.getThreadSafeLogger(MongoSearchUpdaterFlow.class);

    /**
     * Config key of the dispatcher for the search updater.
     */
    static final String DISPATCHER_NAME = "search-updater-dispatcher";

    private final MongoCollection<BsonDocument> collection;
    private final MongoCollection<BsonDocument> collectionWithAcknowledgements;
    private final SearchUpdateMapper searchUpdateMapper;

    private MongoSearchUpdaterFlow(final MongoCollection<BsonDocument> collection,
            final PersistenceStreamConfig persistenceConfig,
            final SearchUpdateMapper searchUpdateMapper) {

        this.collection = collection;
        collectionWithAcknowledgements = collection.withWriteConcern(
                persistenceConfig.getWithAcknowledgementsWriteConcern());
        this.searchUpdateMapper = searchUpdateMapper;
    }

    /**
     * Create a MongoSearchUpdaterFlow object.
     *
     * @param database the MongoDB database.
     * @param persistenceConfig the persistence configuration for the search updater stream.
     * @param searchUpdateMapper the mapper for custom processing on search updates.
     * @return the MongoSearchUpdaterFlow object.
     */
    public static MongoSearchUpdaterFlow of(final MongoDatabase database,
            final PersistenceStreamConfig persistenceConfig,
            final SearchUpdateMapper searchUpdateMapper) {

        return new MongoSearchUpdaterFlow(
                database.getCollection(PersistenceConstants.THINGS_COLLECTION_NAME, BsonDocument.class),
                persistenceConfig,
                searchUpdateMapper);
    }

    /**
     * Create a new flow through the search persistence.
     * No logging or recovery is attempted.
     *
     * @param shouldAcknowledge whether to use a write concern to guarantee the consistency of acknowledgements.
     * {@link org.eclipse.ditto.base.model.acks.DittoAcknowledgementLabel#SEARCH_PERSISTED} was required or not.
     * @param parallelism How many write operations may run in parallel for this sink.
     * @param maxBulkSize How many writes to perform in one bulk.
     * @return the sink.
     */
    public Flow<SubSource<AbstractWriteModel, NotUsed>, WriteResultAndErrors, NotUsed> start(
            final boolean shouldAcknowledge,
            final int parallelism,
            final int maxBulkSize) {

        return Flow.<SubSource<AbstractWriteModel, NotUsed>>create()
                .flatMapConcat(source -> source.grouped(maxBulkSize)
                        .flatMapConcat(searchUpdateMapper::processWriteModels)
                        .mergeSubstreams()
                        .flatMapMerge(parallelism, writeModels -> executeBulkWrite(shouldAcknowledge, writeModels))
                        .async(MongoSearchUpdaterFlow.DISPATCHER_NAME, parallelism)
                );
    }

    private Source<WriteResultAndErrors, NotUsed> executeBulkWrite(final boolean shouldAcknowledge,
            final Collection<Pair<AbstractWriteModel, WriteModel<BsonDocument>>> pairs) {

        final MongoCollection<BsonDocument> theCollection;
        if (shouldAcknowledge) {
            theCollection = collectionWithAcknowledgements;
        } else {
            theCollection = collection;
        }
        final var abstractWriteModels = pairs.stream().map(Pair::first).toList();
        final var writeModels = pairs.stream().map(Pair::second).toList();

        final String bulkWriteCorrelationId = UUID.randomUUID().toString();
        if (writeModels.isEmpty()) {
            LOGGER.withCorrelationId(bulkWriteCorrelationId)
                    .debug("Requested to make empty update by write models <{}>", abstractWriteModels);
            for (final var abstractWriteModel : abstractWriteModels) {
                abstractWriteModel.getMetadata().sendWeakAck(null);
                abstractWriteModel.getMetadata().sendBulkWriteCompleteToOrigin(bulkWriteCorrelationId);
            }
            return Source.empty();
        }

        if (LOGGER.isDebugEnabled()) {
            LOGGER.withCorrelationId(bulkWriteCorrelationId)
                    .debug("Executing BulkWrite containing <{}> things: [<thingId>:{correlationIds}:<filter>]: {}",
                            pairs.size(),
                            pairs.stream()
                                    .map(writeModelPair -> "<" + writeModelPair.first().getMetadata().getThingId() +
                                            ">:" +
                                            writeModelPair.first().getMetadata().getEventsCorrelationIds()
                                                    .stream()
                                                    .collect(Collectors.joining(",", "{", "}"))
                                            + ":<" + extractFilterBson(writeModelPair.second()) + ">"
                                    )
                                    .toList());

            // only log the complete MongoDB writeModels on "TRACE" as they get really big and almost crash the logging backend:
            LOGGER.withCorrelationId(bulkWriteCorrelationId)
                    .trace("Executing BulkWrite <{}>", writeModels);
        }
        final var bulkWriteTimer = startBulkWriteTimer(writeModels);
        return Source.fromPublisher(theCollection.bulkWrite(writeModels, new BulkWriteOptions().ordered(false)))
                .map(bulkWriteResult -> WriteResultAndErrors.success(
                        abstractWriteModels, bulkWriteResult, bulkWriteCorrelationId))
                .recoverWithRetries(1, new PFBuilder<Throwable, Source<WriteResultAndErrors, NotUsed>>()
                        .match(MongoBulkWriteException.class, bulkWriteException ->
                                Source.single(WriteResultAndErrors.failure(
                                        abstractWriteModels, bulkWriteException, bulkWriteCorrelationId))
                        )
                        .matchAny(error ->
                                Source.single(WriteResultAndErrors.unexpectedError(
                                        abstractWriteModels, error, bulkWriteCorrelationId))
                        )
                        .build()
                )
                .map(resultAndErrors -> {
                    stopBulkWriteTimer(bulkWriteTimer);
                    abstractWriteModels.forEach(writeModel ->
                            ConsistencyLag.startS6Acknowledge(writeModel.getMetadata())
                    );
                    return resultAndErrors;
                });
    }

    private static String extractFilterBson(final WriteModel<BsonDocument> writeModel) {
        if (writeModel instanceof UpdateManyModel) {
            return ((UpdateManyModel<BsonDocument>) writeModel).getFilter().toString();
        } else if (writeModel instanceof UpdateOneModel) {
            return ((UpdateOneModel<BsonDocument>) writeModel).getFilter().toString();
        } else if (writeModel instanceof ReplaceOneModel) {
            return ((ReplaceOneModel<BsonDocument>) writeModel).getFilter().toString();
        } else if (writeModel instanceof DeleteOneModel) {
            return ((DeleteOneModel<BsonDocument>) writeModel).getFilter().toString();
        } else if (writeModel instanceof DeleteManyModel) {
            return ((DeleteManyModel<BsonDocument>) writeModel).getFilter().toString();
        }
        return "no filter";
    }

    private static StartedTimer startBulkWriteTimer(final List<?> writeModels) {
        DittoMetrics.histogram(COUNT_THING_BULK_UPDATES_PER_BULK).record((long) writeModels.size());
        return DittoMetrics.timer(TRACE_THING_BULK_UPDATE).tag(UPDATE_TYPE_TAG, "bulkUpdate").start();
    }

    private static void stopBulkWriteTimer(final StartedTimer timer) {
        try {
            timer.stop();
        } catch (final IllegalStateException e) {
            // it is okay if the timer stopped already; simply return the result.
        }
    }

}
