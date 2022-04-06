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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import org.eclipse.ditto.base.model.namespaces.NamespaceBlockedException;
import org.eclipse.ditto.internal.utils.namespaces.BlockedNamespaces;
import org.eclipse.ditto.thingsearch.service.common.config.PersistenceStreamConfig;
import org.eclipse.ditto.thingsearch.service.common.config.StreamStageConfig;
import org.eclipse.ditto.thingsearch.service.common.config.UpdaterConfig;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.AbstractWriteModel;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.Metadata;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.WriteResultAndErrors;
import org.eclipse.ditto.thingsearch.service.updater.actors.ThingUpdater;

import com.mongodb.reactivestreams.client.MongoDatabase;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.stream.Attributes;
import akka.stream.KillSwitch;
import akka.stream.KillSwitches;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Keep;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;

/**
 * Stream from the cache of Thing changes to the persistence of the search index.
 */
public final class SearchUpdaterStream {

    /**
     * Header to request this actor to perform a force-update due to a previous patch not being applied.
     */
    public static final String FORCE_UPDATE_INCORRECT_PATCH = "force-update-incorrect-patch";

    private final UpdaterConfig updaterConfig;
    private final EnforcementFlow enforcementFlow;
    private final MongoSearchUpdaterFlow mongoSearchUpdaterFlow;
    private final BulkWriteResultAckFlow bulkWriteResultAckFlow;
    private final ActorRef changeQueueActor;
    private final BlockedNamespaces blockedNamespaces;
    private final SearchUpdateMapper searchUpdateMapper;
    private final ActorSystem actorSystem;

    private SearchUpdaterStream(final UpdaterConfig updaterConfig,
            final EnforcementFlow enforcementFlow,
            final MongoSearchUpdaterFlow mongoSearchUpdaterFlow,
            final BulkWriteResultAckFlow bulkWriteResultAckFlow,
            final ActorRef changeQueueActor,
            final BlockedNamespaces blockedNamespaces,
            final SearchUpdateMapper searchUpdateMapper,
            final ActorSystem actorSystem) {

        this.updaterConfig = updaterConfig;
        this.enforcementFlow = enforcementFlow;
        this.mongoSearchUpdaterFlow = mongoSearchUpdaterFlow;
        this.bulkWriteResultAckFlow = bulkWriteResultAckFlow;
        this.changeQueueActor = changeQueueActor;
        this.blockedNamespaces = blockedNamespaces;
        this.searchUpdateMapper = searchUpdateMapper;
        this.actorSystem = actorSystem;
    }

    /**
     * Create a restart-able SearchUpdaterStream object.
     *
     * @param updaterConfig the search updater configuration settings.
     * @param actorSystem actor system to run the stream in.
     * @param thingsShard shard region proxy of things.
     * @param policiesShard shard region proxy of policies.
     * @param updaterShard shard region of search updaters.
     * @param changeQueueActor reference of the change queue actor.
     * @param database MongoDB database.
     * @param searchUpdateMapper a custom listener for search updates.
     * @return a SearchUpdaterStream object.
     */
    public static SearchUpdaterStream of(final UpdaterConfig updaterConfig,
            final ActorSystem actorSystem,
            final ActorRef thingsShard,
            final ActorRef policiesShard,
            final ActorRef updaterShard,
            final ActorRef changeQueueActor,
            final MongoDatabase database,
            final BlockedNamespaces blockedNamespaces,
            final SearchUpdateMapper searchUpdateMapper) {

        final var streamConfig = updaterConfig.getStreamConfig();

        final var enforcementFlow =
                EnforcementFlow.of(actorSystem, streamConfig, thingsShard, policiesShard, actorSystem.getScheduler());

        final var mongoSearchUpdaterFlow =
                MongoSearchUpdaterFlow.of(database, streamConfig.getPersistenceConfig(), searchUpdateMapper);

        final var bulkWriteResultAckFlow = BulkWriteResultAckFlow.of(updaterShard);

        return new SearchUpdaterStream(updaterConfig, enforcementFlow, mongoSearchUpdaterFlow, bulkWriteResultAckFlow,
                changeQueueActor, blockedNamespaces, searchUpdateMapper, actorSystem);
    }

    /**
     * Start a perpetual search updater stream killed only by the kill-switch.
     *
     * @return kill-switch to terminate the stream.
     */
    public KillSwitch start() {
        return createSource()
                .viaMat(KillSwitches.single(), Keep.right())
                .to(Sink.ignore())
                .run(actorSystem);
    }

    // TODO
    public Flow<ThingUpdater.Data, ThingUpdater.Result, NotUsed> flow() {
        final Flow<ThingUpdater.Data, ThingUpdater.Data, NotUsed> blockNamespace =
                blockNamespaceFlow(data -> data.metadata().getThingId().getNamespace());

        return Flow.<ThingUpdater.Data>create().flatMapConcat(data -> Source.single(data)
                .via(blockNamespace)
                .map(Optional::of)
                .orElse(Source.single(Optional.empty()))
                .flatMapConcat(optional -> {
                    if (optional.isPresent()) {
                        return Source.single(optional.get())
                                .via(enforcementFlow.create(searchUpdateMapper))
                                .via(mongoSearchUpdaterFlow.create());
                    } else {
                        return Source.single(asNamespaceBlockedException(data));
                    }
                }));
    }

    private Source<String, NotUsed> createSource() {
        final var streamConfig = updaterConfig.getStreamConfig();
        final StreamStageConfig retrievalConfig = streamConfig.getRetrievalConfig();
        final PersistenceStreamConfig persistenceConfig = streamConfig.getPersistenceConfig();

        final var acknowledgedSource =
                ChangeQueueActor.createSource(changeQueueActor, true, streamConfig.getWriteInterval());
        final var unacknowledgedSource =
                ChangeQueueActor.createSource(changeQueueActor, false, streamConfig.getWriteInterval());

        final var mergedSource =
                acknowledgedSource.mergePrioritized(unacknowledgedSource, 1023, 1, true);

        final Source<List<AbstractWriteModel>, NotUsed> enforcementSource = enforcementFlow.create(
                mergedSource.via(filterMapKeysByBlockedNamespaces()),
                retrievalConfig.getParallelism(),
                persistenceConfig.getMaxBulkSize()
        );

        final String logName = "SearchUpdaterStream/BulkWriteResult";
        final Source<WriteResultAndErrors, NotUsed> persistenceSource = mongoSearchUpdaterFlow.start(
                enforcementSource,
                true
        );

        return persistenceSource.via(bulkWriteResultAckFlow.start(persistenceConfig.getAckDelay()))
                .log(logName)
                .withAttributes(Attributes.logLevels(
                        Attributes.logLevelInfo(),
                        Attributes.logLevelWarning(),
                        Attributes.logLevelError()));
    }

    private Flow<Collection<Metadata>, Collection<Metadata>, NotUsed> filterMapKeysByBlockedNamespaces() {
        return Flow.<Collection<Metadata>>create()
                .<Collection<Metadata>, NotUsed>flatMapConcat(map ->
                        Source.fromIterator(map::iterator)
                                .via(blockNamespaceFlow(entry -> entry.getThingId().getNamespace()))
                                .fold(new ArrayList<>(), (accumulator, entry) -> {
                                    accumulator.add(entry);
                                    return accumulator;
                                })
                )
                .withAttributes(Attributes.inputBuffer(1, 1));
    }

    private <T> Flow<T, T, NotUsed> blockNamespaceFlow(final Function<T, String> namespaceExtractor) {
        return Flow.<T>create()
                .flatMapConcat(element -> {
                    final String namespace = namespaceExtractor.apply(element);
                    final CompletionStage<Boolean> shouldUpdate = blockedNamespaces.contains(namespace)
                            .handle((result, error) -> result == null || !result);
                    return Source.completionStage(shouldUpdate)
                            .filter(Boolean::valueOf)
                            .map(bool -> element);
                });
    }

    private static ThingUpdater.Result asNamespaceBlockedException(final ThingUpdater.Data data) {
        final var error = NamespaceBlockedException.newBuilder(data.metadata().getThingId().getNamespace()).build();
        return ThingUpdater.Result.fromError(data.metadata(), error);
    }

}
