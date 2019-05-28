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
package org.eclipse.ditto.services.thingsearch.persistence.write.streaming;

import static org.eclipse.ditto.services.thingsearch.persistence.PersistenceConstants.THINGS_COLLECTION_NAME;

import java.time.Duration;
import java.util.List;

import org.bson.Document;
import org.eclipse.ditto.services.utils.metrics.DittoMetrics;
import org.eclipse.ditto.services.utils.metrics.instruments.timer.StartedTimer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.MongoBulkWriteException;
import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.WriteModel;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;

import akka.NotUsed;
import akka.japi.Pair;
import akka.japi.pf.PFBuilder;
import akka.stream.Attributes;
import akka.stream.DelayOverflowStrategy;
import akka.stream.FanInShape2;
import akka.stream.FlowShape;
import akka.stream.Graph;
import akka.stream.UniformFanOutShape;
import akka.stream.javadsl.Broadcast;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.GraphDSL;
import akka.stream.javadsl.Source;
import akka.stream.javadsl.Zip;
import kamon.Kamon;

import org.eclipse.ditto.services.thingsearch.persistence.write.model.AbstractWriteModel;

/**
 * Flow mapping write models to write results via the search persistence.
 */
final class MongoSearchUpdaterFlow {

    private static final String TRACE_THING_BULK_UPDATE = "things_search_thing_bulkUpdate";
    private static final String COUNT_THING_BULK_UPDATES_PER_BULK = "things_search_thing_bulkUpdate_updates_per_bulk";
    private static final String UPDATE_TYPE_TAG = "update_type";

    private Logger log = LoggerFactory.getLogger(MongoSearchUpdaterFlow.class);

    private final MongoCollection<Document> collection;

    private MongoSearchUpdaterFlow(final MongoCollection<Document> collection) {
        this.collection = collection;
    }

    /**
     * Create a MongoSearchUpdaterFlow object.
     *
     * @param database the MongoDB database.
     * @return the MongoSearchUpdaterFlow object.
     */
    public static MongoSearchUpdaterFlow of(final MongoDatabase database) {
        return new MongoSearchUpdaterFlow(database.getCollection(THINGS_COLLECTION_NAME));
    }


    /**
     * Create a new flow through the search persistence.
     * No logging or recovery is attempted.
     *
     * @param parallelism How many write operations may run in parallel for this sink.
     * @param maxBulkSize How many writes to perform in one bulk.
     * @param writeInterval Delay between bulk operation requests. MongoDB backpressure is insufficient.
     * @return the sink.
     */
    public Flow<Source<AbstractWriteModel, NotUsed>, BulkWriteResult, NotUsed> start(final int parallelism,
            final int maxBulkSize,
            final Duration writeInterval) {

        final Flow<Source<AbstractWriteModel, NotUsed>, List<WriteModel<Document>>, NotUsed> batchFlow =
                Flow.<Source<AbstractWriteModel, NotUsed>>create()
                        .flatMapConcat(source -> source.map(AbstractWriteModel::toMongo).grouped(maxBulkSize));

        final Flow<List<WriteModel<Document>>, List<WriteModel<Document>>, NotUsed> throttleFlow;
        if (Duration.ZERO.minus(writeInterval).isNegative()) {
            throttleFlow = Flow.<List<WriteModel<Document>>>create()
                    .delay(writeInterval, DelayOverflowStrategy.backpressure());
        } else {
            throttleFlow = Flow.create();
        }

        final Flow<List<WriteModel<Document>>, BulkWriteResult, NotUsed> writeFlow =
                throttleFlow.flatMapMerge(parallelism, this::executeBulkWrite)
                        // never initiate more than "parallelism" writes against the persistence
                        .withAttributes(Attributes.inputBuffer(parallelism, parallelism));

        final Flow<List<WriteModel<Document>>, StartedTimer, NotUsed> startTimerFlow = createStartTimerFlow();
        final Flow<Pair<BulkWriteResult, StartedTimer>, BulkWriteResult, NotUsed> stopTimerFlow = createStopTimerFlow();

        return Flow.fromGraph(assembleFlows(batchFlow, writeFlow, startTimerFlow, stopTimerFlow));
    }

    private Source<BulkWriteResult, NotUsed> executeBulkWrite(final List<WriteModel<Document>> writeModel) {
        return Source.fromPublisher(collection.bulkWrite(writeModel, new BulkWriteOptions().ordered(false)))
                .recoverWithRetries(1, new PFBuilder<Throwable, Source<BulkWriteResult, NotUsed>>()
                        .match(MongoBulkWriteException.class, bulkWriteException -> {
                            log.info("Got MongoBulkWriteException; may ignore if all are duplicate key errors:",
                                    bulkWriteException);
                            return Source.single(bulkWriteException.getWriteResult());
                        })
                        .matchAny(error -> {
                            log.error("Unexpected error", error);
                            return Source.failed(error);
                        })
                        .build());

    }

    private static Flow<List<WriteModel<Document>>, StartedTimer, NotUsed> createStartTimerFlow() {
        return Flow.fromFunction(writeModels -> {
            Kamon.histogram(COUNT_THING_BULK_UPDATES_PER_BULK).record(writeModels.size());
            return DittoMetrics.expiringTimer(TRACE_THING_BULK_UPDATE).tag(UPDATE_TYPE_TAG, "bulkUpdate").build();
        });
    }

    private static Flow<Pair<BulkWriteResult, StartedTimer>, BulkWriteResult, NotUsed> createStopTimerFlow() {
        return Flow.fromFunction(pair -> {
            try {
                pair.second().stop();
            } catch (final IllegalStateException e) {
                // it is okay if the timer stopped already; simply return the result.
            }
            return pair.first();
        });
    }

    @SuppressWarnings("unchecked") // java 8 can't handle graph DSL types
    private static <A, B, C, D> Graph<FlowShape<A, C>, NotUsed> assembleFlows(
            final Flow<A, B, NotUsed> stage1Flow,
            final Flow<B, C, NotUsed> stage2Flow,
            final Flow<B, D, NotUsed> sideChannelFlow,
            final Flow<Pair<C, D>, C, NotUsed> resultProcessorFlow) {

        return GraphDSL.create(builder -> {
            final FlowShape<A, B> stage1 = builder.add(stage1Flow);
            final FlowShape<B, C> stage2 = builder.add(stage2Flow);
            final FlowShape<B, D> sideChannel = builder.add(sideChannelFlow);
            final FlowShape<Pair<C, D>, C> resultProcessor = builder.add(resultProcessorFlow);

            final UniformFanOutShape<B, B> broadcast = builder.add(Broadcast.create(2));
            final FanInShape2<C, D, Pair<C, D>> zip = builder.add(Zip.create());

            builder.from(stage1.out()).toInlet(broadcast.in());
            builder.from(broadcast.out(0)).toInlet(stage2.in());
            builder.from(broadcast.out(1)).toInlet(sideChannel.in());
            builder.from(stage2.out()).toInlet(zip.in0());
            builder.from(sideChannel.out()).toInlet(zip.in1());
            builder.from(zip.out()).toInlet(resultProcessor.in());

            return FlowShape.of(stage1.in(), resultProcessor.out());
        });
    }
}
