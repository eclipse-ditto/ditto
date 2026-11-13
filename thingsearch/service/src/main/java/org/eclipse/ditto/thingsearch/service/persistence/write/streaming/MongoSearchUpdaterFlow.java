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
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.bson.BsonDocument;
import org.eclipse.ditto.internal.utils.pekko.logging.DittoLoggerFactory;
import org.eclipse.ditto.internal.utils.pekko.logging.ThreadSafeDittoLogger;
import org.eclipse.ditto.internal.utils.metrics.DittoMetrics;
import org.eclipse.ditto.internal.utils.metrics.instruments.timer.StartedTimer;
import org.eclipse.ditto.thingsearch.persistence.api.model.AbstractWriteModel;
import org.eclipse.ditto.thingsearch.persistence.api.model.Metadata;
import org.eclipse.ditto.thingsearch.persistence.api.model.ThingDeleteModel;
import org.eclipse.ditto.thingsearch.persistence.api.model.ThingWriteModel;
import org.eclipse.ditto.thingsearch.persistence.api.write.SearchUpdaterFlow;
import org.eclipse.ditto.thingsearch.persistence.api.write.SearchWriteError;
import org.eclipse.ditto.thingsearch.persistence.api.write.SearchWriteResult;
import org.eclipse.ditto.thingsearch.persistence.api.write.UpdaterData;
import org.eclipse.ditto.thingsearch.persistence.api.write.UpdaterResult;
import org.eclipse.ditto.thingsearch.service.common.config.PersistenceStreamConfig;
import org.eclipse.ditto.thingsearch.service.persistence.PersistenceConstants;
import org.eclipse.ditto.thingsearch.service.persistence.write.mapping.SearchIndexDocumentMongoEncoder;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.WriteResultAndErrors;
import org.eclipse.ditto.thingsearch.service.updater.actors.MongoWriteModel;

import com.mongodb.ErrorCategory;
import com.mongodb.MongoBulkWriteException;
import com.mongodb.bulk.BulkWriteError;
import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.DeleteManyModel;
import com.mongodb.client.model.DeleteOneModel;
import com.mongodb.client.model.ReplaceOneModel;
import com.mongodb.client.model.UpdateManyModel;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.WriteModel;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;

import org.apache.pekko.NotUsed;
import org.apache.pekko.japi.pf.PFBuilder;
import org.apache.pekko.stream.javadsl.Flow;
import org.apache.pekko.stream.javadsl.Source;

/**
 * Mongo implementation of the backend-neutral {@link SearchUpdaterFlow} seam. Bridges the neutral write models
 * into the historical Mongo write machinery (BSON write models, {@code BsonDiff}-based incremental updates and
 * the ordered/unordered bulk write) and adapts the Mongo bulk-write result back into the neutral
 * {@link SearchWriteResult}.
 */
public final class MongoSearchUpdaterFlow implements SearchUpdaterFlow {

    private static final String TRACE_THING_BULK_UPDATE = "things_wildcard_search_thing_bulkUpdate";
    private static final String COUNT_THING_BULK_UPDATES_PER_BULK = "things_wildcard_search_thing_bulkUpdate_updates_per_bulk";
    private static final String UPDATE_TYPE_TAG = "update_type";

    private static final ThreadSafeDittoLogger LOGGER =
            DittoLoggerFactory.getThreadSafeLogger(MongoSearchUpdaterFlow.class);

    private final MongoCollection<BsonDocument> collection;
    private final int maxWireVersion;

    private MongoSearchUpdaterFlow(final MongoCollection<BsonDocument> collection,
            final PersistenceStreamConfig persistenceConfig,
            final int maxWireVersion) {

        final var writeConcern = persistenceConfig.getWithAcknowledgementsWriteConcern();
        LOGGER.info("Update writeConcern=<{}>", writeConcern);
        this.collection = collection.withWriteConcern(writeConcern);
        this.maxWireVersion = maxWireVersion;
    }

    /**
     * Create a MongoSearchUpdaterFlow object.
     *
     * @param database the MongoDB database.
     * @param persistenceConfig the persistence configuration for the search updater stream.
     * @param maxWireVersion the Mongo max wire version, used to select the incremental-update representation.
     * @return the MongoSearchUpdaterFlow object.
     */
    public static MongoSearchUpdaterFlow of(final MongoDatabase database,
            final PersistenceStreamConfig persistenceConfig,
            final int maxWireVersion) {

        return new MongoSearchUpdaterFlow(
                database.getCollection(PersistenceConstants.THINGS_COLLECTION_NAME, BsonDocument.class),
                persistenceConfig,
                maxWireVersion
        );
    }

    @Override
    public Flow<UpdaterData, UpdaterResult, NotUsed> create() {
        return Flow.<UpdaterData>create()
                .flatMapConcat(updaterData -> {
                    final var currentMongo = toMongoModel(updaterData.writeModel());
                    final var lastMongo = toMongoModel(updaterData.lastWriteModel());
                    final Optional<MongoWriteModel> mongoWriteModelOpt =
                            currentMongo.toIncrementalMongo(lastMongo, maxWireVersion);
                    if (mongoWriteModelOpt.isEmpty()) {
                        // reproduce the historical mapper skip: emit nothing and dispatch a weak acknowledgement
                        updaterData.writeModel().getMetadata().sendWeakAck(null);
                        return Source.<UpdaterResult>empty();
                    }
                    ConsistencyLag.startS5MongoBulkWrite(updaterData.writeModel().getMetadata());
                    final MongoWriteModel mongoWriteModel = mongoWriteModelOpt.orElseThrow();
                    return executeBulkWrite(List.of(mongoWriteModel))
                            .map(resultAndErrors -> new UpdaterResult(updaterData.writeModel(),
                                    toSearchWriteResult(resultAndErrors)));
                });
    }

    /**
     * Bridge a backend-neutral write model into the corresponding service-internal Mongo write model.
     */
    private static org.eclipse.ditto.thingsearch.service.persistence.write.model.AbstractWriteModel toMongoModel(
            final AbstractWriteModel neutral) {

        final Metadata metadata = neutral.getMetadata();
        if (neutral instanceof ThingDeleteModel) {
            return org.eclipse.ditto.thingsearch.service.persistence.write.model.ThingDeleteModel.of(metadata);
        } else if (neutral instanceof ThingWriteModel neutralWrite) {
            if (neutralWrite.isEmptiedOut()) {
                return org.eclipse.ditto.thingsearch.service.persistence.write.model.ThingWriteModel
                        .ofEmptiedOut(metadata);
            } else if (neutralWrite.isNoop()) {
                return org.eclipse.ditto.thingsearch.service.persistence.write.model.ThingWriteModel
                        .noopWriteModel(metadata);
            } else {
                return org.eclipse.ditto.thingsearch.service.persistence.write.model.ThingWriteModel.of(metadata,
                        SearchIndexDocumentMongoEncoder.encode(neutralWrite.getDocument()));
            }
        } else {
            throw new IllegalArgumentException("Unsupported neutral write model: " + neutral);
        }
    }

    /**
     * Adapt a Mongo {@link WriteResultAndErrors} into the backend-neutral {@link SearchWriteResult}.
     */
    static SearchWriteResult toSearchWriteResult(final WriteResultAndErrors writeResultAndErrors) {
        final Optional<Throwable> unexpectedError = writeResultAndErrors.getUnexpectedError();
        final int writeModelCount = writeResultAndErrors.getWriteModels().size();
        if (unexpectedError.isPresent()) {
            return SearchWriteResult.unexpectedError(writeModelCount, unexpectedError.get());
        }
        final BulkWriteResult bulkWriteResult = writeResultAndErrors.getBulkWriteResult();
        if (!bulkWriteResult.wasAcknowledged()) {
            return SearchWriteResult.unacknowledged(writeModelCount);
        }
        final int nonDeleteWriteModelCount = (int) writeResultAndErrors.getWriteModels().stream()
                .filter(model -> !(model.getDitto()
                        instanceof org.eclipse.ditto.thingsearch.service.persistence.write.model.ThingDeleteModel))
                .count();
        final List<SearchWriteError> errors = writeResultAndErrors.getBulkWriteErrors().stream()
                .map(error -> new SearchWriteError(error.getIndex(),
                        error.getCategory() == ErrorCategory.DUPLICATE_KEY
                                ? SearchWriteError.Category.DUPLICATE_KEY
                                : SearchWriteError.Category.OTHER,
                        error.getMessage()))
                .toList();
        return SearchWriteResult.acknowledged(writeModelCount, nonDeleteWriteModelCount,
                bulkWriteResult.getMatchedCount(), bulkWriteResult.getModifiedCount(),
                bulkWriteResult.getUpserts().size(), errors);
    }

    private Source<WriteResultAndErrors, NotUsed> executeBulkWrite(final Collection<MongoWriteModel> writeModels) {
        final String bulkWriteCorrelationId = UUID.randomUUID().toString();
        if (writeModels.isEmpty()) {
            LOGGER.withCorrelationId(bulkWriteCorrelationId).debug("Requested to make empty update");
            return Source.empty();
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.withCorrelationId(bulkWriteCorrelationId)
                    .debug("Executing BulkWrite containing <{}> things: [<thingId>:{correlationIds}:<filter>]: {}",
                            writeModels.size(),
                            writeModels.stream()
                                    .map(writeModelPair -> "<" + writeModelPair.getDitto().getMetadata().getThingId() +
                                            ">:" +
                                            writeModelPair.getDitto().getMetadata().getEventsCorrelationIds()
                                                    .stream()
                                                    .collect(Collectors.joining(",", "{", "}"))
                                            + ":<" + extractFilterBson(writeModelPair.getBson()) + ">"
                                    )
                                    .toList());

            // only log the complete MongoDB writeModels on "TRACE" as they get really big and almost crash the logging backend:
            LOGGER.withCorrelationId(bulkWriteCorrelationId)
                    .trace("Executing BulkWrite <{}>", writeModels);
        }
        final var bulkWriteTimer = startBulkWriteTimer(writeModels);
        final var bsons = writeModels.stream().map(MongoWriteModel::getBson).toList();
        return Source.fromPublisher(collection.bulkWrite(bsons, new BulkWriteOptions().ordered(false)))
                .map(bulkWriteResult -> WriteResultAndErrors.success(
                        writeModels, bulkWriteResult, bulkWriteCorrelationId))
                .recoverWithRetries(1, new PFBuilder<Throwable, Source<WriteResultAndErrors, NotUsed>>()
                        .match(MongoBulkWriteException.class, bulkWriteException ->
                                Source.single(WriteResultAndErrors.failure(
                                        writeModels, bulkWriteException, bulkWriteCorrelationId))
                        )
                        .matchAny(error ->
                                Source.single(WriteResultAndErrors.unexpectedError(
                                        writeModels, error, bulkWriteCorrelationId))
                        )
                        .build()
                )
                .map(resultAndErrors -> {
                    stopBulkWriteTimer(bulkWriteTimer);
                    writeModels.forEach(writeModel ->
                            ConsistencyLag.startS6Acknowledge(writeModel.getDitto().getMetadata())
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

    private static StartedTimer startBulkWriteTimer(final Collection<?> writeModels) {
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
