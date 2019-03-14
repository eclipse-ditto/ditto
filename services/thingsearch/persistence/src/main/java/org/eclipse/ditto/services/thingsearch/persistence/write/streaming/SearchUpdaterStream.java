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
package org.eclipse.ditto.services.thingsearch.persistence.write.streaming;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import org.eclipse.ditto.model.namespaces.NamespaceReader;
import org.eclipse.ditto.services.utils.namespaces.BlockedNamespaces;

import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.reactivestreams.client.MongoDatabase;
import com.typesafe.config.Config;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorRefFactory;
import akka.actor.ActorSystem;
import akka.dispatch.MessageDispatcher;
import akka.stream.ActorMaterializer;
import akka.stream.Attributes;
import akka.stream.KillSwitch;
import akka.stream.KillSwitches;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.RestartSink;
import akka.stream.javadsl.RestartSource;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;

import org.eclipse.ditto.services.thingsearch.persistence.write.model.AbstractWriteModel;

/**
 * Stream from the cache of Thing changes to the persistence of the search index.
 */
public final class SearchUpdaterStream {

    private final SearchUpdaterStreamConfigReader configReader;
    private final EnforcementFlow enforcementFlow;
    private final MongoSearchUpdaterFlow mongoSearchUpdaterFlow;
    private final ActorRef changeQueueActor;
    private final BlockedNamespaces blockedNamespaces;

    private SearchUpdaterStream(
            final SearchUpdaterStreamConfigReader configReader,
            final EnforcementFlow enforcementFlow,
            final MongoSearchUpdaterFlow mongoSearchUpdaterFlow,
            final ActorRef changeQueueActor,
            final BlockedNamespaces blockedNamespaces) {

        this.configReader = configReader;
        this.enforcementFlow = enforcementFlow;
        this.mongoSearchUpdaterFlow = mongoSearchUpdaterFlow;
        this.changeQueueActor = changeQueueActor;
        this.blockedNamespaces = blockedNamespaces;
    }

    /**
     * Create a restart-able SearchUpdaterStream object.
     *
     * @param actorSystem actor system to run the stream in.
     * @param thingsShard shard region proxy of things.
     * @param policiesShard shard region proxy of policies.
     * @param changeQueueActor reference of the change queue actor.
     * @param database MongoDB database.
     * @return a SearchUpdaterStream object.
     */
    public static SearchUpdaterStream of(final ActorSystem actorSystem,
            final ActorRef thingsShard,
            final ActorRef policiesShard,
            final ActorRef changeQueueActor,
            final MongoDatabase database,
            final BlockedNamespaces blockedNamespaces) {

        // TODO: refactor config.
        final Config config = actorSystem.settings().config();
        final boolean deleteEvent = config.getBoolean("ditto.things-search.delete.event");

        final SearchUpdaterStreamConfigReader configReader =
                SearchUpdaterStreamConfigReader.of(actorSystem.settings().config());

        final MessageDispatcher messageDispatcher = actorSystem.dispatchers().lookup(configReader.cacheDispatcher());

        final EnforcementFlow enforcementFlow =
                EnforcementFlow.of(configReader, thingsShard, policiesShard, messageDispatcher, deleteEvent);

        final MongoSearchUpdaterFlow mongoSearchUpdaterFlow = MongoSearchUpdaterFlow.of(database);

        return new SearchUpdaterStream(configReader, enforcementFlow, mongoSearchUpdaterFlow, changeQueueActor,
                blockedNamespaces);
    }

    /**
     * Start a perpetual search updater stream killed only by the kill-switch.
     *
     * @param actorRefFactory where to create actors for this stream.
     * @return kill-switch to terminate the stream.
     */
    public KillSwitch start(final ActorRefFactory actorRefFactory) {
        final Source<Source<AbstractWriteModel, NotUsed>, NotUsed> restartSource = createRestartSource();
        final Sink<Source<AbstractWriteModel, NotUsed>, NotUsed> restartSink = createRestartSink();
        final ActorMaterializer actorMaterializer = ActorMaterializer.create(actorRefFactory);
        return restartSource.viaMat(KillSwitches.single(), Keep.right())
                .toMat(restartSink, Keep.left())
                .run(actorMaterializer);
    }

    private Source<Source<AbstractWriteModel, NotUsed>, NotUsed> createRestartSource() {
        final SearchUpdaterStreamConfigReader.Retrieval retrieval = configReader.retrieval();
        final Source<Source<AbstractWriteModel, NotUsed>, NotUsed> source =
                ChangeQueueActor.createSource(changeQueueActor, configReader.writeInterval())
                        .via(filterMapKeysByBlockedNamepaces())
                        .via(enforcementFlow.create(retrieval.parallelism()).map(writeModelSource ->
                                writeModelSource.via(blockNamespaceFlow(SearchUpdaterStream::namespaceOfWriteModel))));

        return RestartSource.withBackoff(retrieval.minBackoff(), retrieval.maxBackoff(), retrieval.randomFactor(),
                () -> source);
    }

    private Sink<Source<AbstractWriteModel, NotUsed>, NotUsed> createRestartSink() {
        final SearchUpdaterStreamConfigReader.Persistence persistence = configReader.persistence();
        final int parallelism = persistence.parallelism();
        final int maxBulkSize = persistence.maxBulkSize();
        final Duration writeInterval = persistence.writeInterval();
        final Sink<Source<AbstractWriteModel, NotUsed>, NotUsed> sink =
                mongoSearchUpdaterFlow.start(parallelism, maxBulkSize, writeInterval)
                        .map(SearchUpdaterStream::logResult)
                        .log("SearchUpdaterStream/BulkWriteResult")
                        .withAttributes(Attributes.logLevels(
                                Attributes.logLevelInfo(),
                                Attributes.logLevelWarning(),
                                Attributes.logLevelError()))
                        .to(Sink.ignore());

        return RestartSink.withBackoff(persistence.minBackoff(), persistence.maxBackoff(), persistence.randomFactor(),
                () -> sink);
    }

    private <T> Flow<Map<String, T>, Map<String, T>, NotUsed> filterMapKeysByBlockedNamepaces() {
        return Flow.<Map<String, T>>create()
                .flatMapConcat(map ->
                        Source.fromIterator(map.entrySet()::iterator)
                                .via(blockNamespaceFlow(entry -> NamespaceReader.fromEntityId(entry.getKey())))
                                .fold(new HashMap<>(), (accumulator, entry) -> {
                                    accumulator.put(entry.getKey(), entry.getValue());
                                    return accumulator;
                                })
                );
    }

    private <T> Flow<T, T, NotUsed> blockNamespaceFlow(final Function<T, Optional<String>> namespaceExtractor) {
        return Flow.<T>create()
                .flatMapConcat(element ->
                        namespaceExtractor.apply(element)
                                .map(namespace -> {
                                    final CompletionStage<Boolean> shouldUpdate =
                                            blockedNamespaces.contains(namespace)
                                                    .handle((result, error) -> result == null || !result);
                                    return Source.fromCompletionStage(shouldUpdate)
                                            .filter(Boolean::valueOf)
                                            .map(bool -> element);
                                })
                                .orElseGet(() -> Source.single(element))
                );
    }

    private static Optional<String> namespaceOfWriteModel(final AbstractWriteModel writeModel) {
        return NamespaceReader.fromEntityId(writeModel.getMetadata().getThingId());
    }

    private static String logResult(final BulkWriteResult bulkWriteResult) {
        return String.format("BulkWriteResult[matched=%d,upserts=%d,inserted=%d,modified=%d,deleted=%d]",
                bulkWriteResult.getMatchedCount(),
                bulkWriteResult.getUpserts().size(),
                bulkWriteResult.getInsertedCount(),
                bulkWriteResult.getModifiedCount(),
                bulkWriteResult.getDeletedCount());
    }
}
