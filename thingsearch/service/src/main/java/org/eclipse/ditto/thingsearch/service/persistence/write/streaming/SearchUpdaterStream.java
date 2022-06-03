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

import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import org.eclipse.ditto.base.model.namespaces.NamespaceBlockedException;
import org.eclipse.ditto.internal.utils.namespaces.BlockedNamespaces;
import org.eclipse.ditto.thingsearch.service.common.config.UpdaterConfig;
import org.eclipse.ditto.thingsearch.service.updater.actors.ThingUpdater;

import com.mongodb.reactivestreams.client.MongoDatabase;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Source;

/**
 * Stream from the cache of Thing changes to the persistence of the search index.
 */
public final class SearchUpdaterStream {

    private final EnforcementFlow enforcementFlow;
    private final MongoSearchUpdaterFlow mongoSearchUpdaterFlow;
    private final BlockedNamespaces blockedNamespaces;
    private final SearchUpdateMapper searchUpdateMapper;

    private SearchUpdaterStream(final EnforcementFlow enforcementFlow,
            final MongoSearchUpdaterFlow mongoSearchUpdaterFlow,
            final BlockedNamespaces blockedNamespaces,
            final SearchUpdateMapper searchUpdateMapper) {

        this.enforcementFlow = enforcementFlow;
        this.mongoSearchUpdaterFlow = mongoSearchUpdaterFlow;
        this.blockedNamespaces = blockedNamespaces;
        this.searchUpdateMapper = searchUpdateMapper;
    }

    /**
     * Create a restart-able SearchUpdaterStream object.
     *
     * @param updaterConfig the search updater configuration settings.
     * @param actorSystem actor system to run the stream in.
     * @param thingsShard shard region proxy of things.
     * @param policiesShard shard region proxy of policies.
     * @param database MongoDB database.
     * @param searchUpdateMapper a custom listener for search updates.
     * @return a SearchUpdaterStream object.
     */
    public static SearchUpdaterStream of(final UpdaterConfig updaterConfig,
            final ActorSystem actorSystem,
            final ActorRef thingsShard,
            final ActorRef policiesShard,
            final MongoDatabase database,
            final BlockedNamespaces blockedNamespaces,
            final SearchUpdateMapper searchUpdateMapper) {

        final var streamConfig = updaterConfig.getStreamConfig();

        final var enforcementFlow =
                EnforcementFlow.of(actorSystem, streamConfig, thingsShard, policiesShard, actorSystem.getScheduler());

        final var mongoSearchUpdaterFlow =
                MongoSearchUpdaterFlow.of(database, streamConfig.getPersistenceConfig());

        return new SearchUpdaterStream(enforcementFlow, mongoSearchUpdaterFlow, blockedNamespaces, searchUpdateMapper);
    }

    /**
     * Create a flow for a thing-updater.
     *
     * @return The flow.
     */
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
