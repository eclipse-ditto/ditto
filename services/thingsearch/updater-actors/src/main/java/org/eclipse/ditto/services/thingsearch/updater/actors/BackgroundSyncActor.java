/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.thingsearch.updater.actors;

import java.time.Instant;
import java.util.List;
import java.util.function.Function;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.services.models.thingsearch.commands.sudo.UpdateThing;
import org.eclipse.ditto.services.thingsearch.common.config.BackgroundSyncConfig;
import org.eclipse.ditto.services.thingsearch.common.config.DefaultBackgroundSyncConfig;
import org.eclipse.ditto.services.thingsearch.persistence.read.ThingsSearchPersistence;
import org.eclipse.ditto.services.thingsearch.persistence.write.model.Metadata;
import org.eclipse.ditto.services.thingsearch.persistence.write.streaming.BackgroundSyncStream;
import org.eclipse.ditto.services.utils.akka.controlflow.ResumeSource;
import org.eclipse.ditto.services.utils.health.AbstractBackgroundStreamingActorWithConfigWithStatusReport;

import com.typesafe.config.Config;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.stream.javadsl.Source;

/**
 * Cluster-singleton responsible for background synchronization.
 */
public final class BackgroundSyncActor
        extends AbstractBackgroundStreamingActorWithConfigWithStatusReport<BackgroundSyncConfig> {

    /**
     * Name of the singleton coordinator.
     */
    public static final String ACTOR_NAME = "backgroundSync";

    private final ThingsMetadataSource thingsMetadataSource;
    private final ThingsSearchPersistence thingsSearchPersistence;
    private final BackgroundSyncStream backgroundSyncStream;
    private final ActorRef thingsUpdater;

    private BackgroundSyncActor(final BackgroundSyncConfig backgroundSyncConfig,
            final ThingsMetadataSource thingsMetadataSource,
            final ThingsSearchPersistence thingsSearchPersistence,
            final BackgroundSyncStream backgroundSyncStream,
            final ActorRef thingsUpdater) {
        super(backgroundSyncConfig);
        this.thingsMetadataSource = thingsMetadataSource;
        this.thingsSearchPersistence = thingsSearchPersistence;
        this.backgroundSyncStream = backgroundSyncStream;
        this.thingsUpdater = thingsUpdater;
    }

    /**
     * Create Akka Props object for the background sync actor.
     *
     * @param config the config of the background sync actor.
     * @param pubSubMediator Akka pub-sub mediator.
     * @param thingsSearchPersistence the search persistence to access the search index.
     * @param policiesShardRegion the policies shard region to query policy revisions.
     * @param thingsUpdater the dispatcher of UpdateThing commands.
     * @return an actor to coordinate background sync.
     */
    public static Props props(final BackgroundSyncConfig config,
            final ActorRef pubSubMediator,
            final ThingsSearchPersistence thingsSearchPersistence,
            final ActorRef policiesShardRegion,
            final ActorRef thingsUpdater) {

        final ThingsMetadataSource thingsMetadataSource =
                ThingsMetadataSource.of(pubSubMediator, config.getThrottleThroughput(), config.getIdleTimeout());
        final BackgroundSyncStream backgroundSyncStream =
                BackgroundSyncStream.of(policiesShardRegion, config.getPolicyAskTimeout(), config.getToleranceWindow(),
                        config.getThrottleThroughput(), config.getThrottlePeriod());

        return Props.create(BackgroundSyncActor.class, config, thingsMetadataSource, thingsSearchPersistence,
                backgroundSyncStream, thingsUpdater);
    }

    @Override
    protected BackgroundSyncConfig parseConfig(final Config config) {
        return DefaultBackgroundSyncConfig.parse(config);
    }

    @Override
    protected Source<?, ?> getSource() {
        return getLowerBoundSource()
                .flatMapConcat(lowerBound -> {
                    final Source<Metadata, NotUsed> persistedMetadata = getPersistedMetadataSource(lowerBound);
                    final Source<Metadata, NotUsed> indexedMetadata = getIndexedMetadataSource(lowerBound);
                    return backgroundSyncStream.filterForInconsistencies(persistedMetadata, indexedMetadata);
                })
                .wireTap(this::handleInconsistency);
    }

    private void handleInconsistency(final Metadata metadata) {
        final ThingId thingId = metadata.getThingId();
        thingsUpdater.tell(UpdateThing.of(thingId, DittoHeaders.empty()), ActorRef.noSender());
        getSelf().tell(SyncEvent.inconsistency(metadata), ActorRef.noSender());
    }

    private Source<ThingId, NotUsed> getLowerBoundSource() {
        // TODO: read bookmarked lower bound. background sync stream does the bookmarking before filtering.
        // Alternatively, as a less accurate bookmark, do it after the indexed metadata stream.
        return Source.single(ThingId.dummy());
    }

    private Source<Metadata, NotUsed> getPersistedMetadataSource(final ThingId lowerBound) {
        return wrapAsResumeSource(lowerBound, thingsMetadataSource::createSource);
    }

    private Source<Metadata, NotUsed> getIndexedMetadataSource(final ThingId lowerBound) {
        return wrapAsResumeSource(lowerBound, thingsSearchPersistence::sudoStreamMetadata);
    }

    private Source<Metadata, NotUsed> wrapAsResumeSource(final ThingId lowerBound,
            final Function<ThingId, Source<Metadata, ?>> sourceCreator) {

        return ResumeSource.onFailureWithBackoff(
                config.getMinBackoff(),
                config.getMaxBackoff(),
                config.getMaxRestarts(),
                config.getRecovery(),
                lowerBound,
                sourceCreator,
                1,
                lastMetadata -> nextLowerBound(lowerBound, lastMetadata));
    }

    private static ThingId nextLowerBound(final ThingId currentLowerBound, final List<Metadata> lastMetadata) {
        if (lastMetadata.isEmpty()) {
            return currentLowerBound;
        } else {
            return lastMetadata.get(lastMetadata.size() - 1).getThingId();
        }
    }

    private static final class SyncEvent implements Event {

        private final String description;

        private SyncEvent(final String description) {
            this.description = description;
        }

        private static Event inconsistency(final Metadata metadata) {
            return new SyncEvent("Inconsistent: " + metadata);
        }

        private static Event toleranceCutOff(final Instant toleranceCutOff) {
            return new SyncEvent("ToleranceCutOff: " + toleranceCutOff);
        }

        @Override
        public String name() {
            return description;
        }

        @Override
        public String toString() {
            return description;
        }
    }
}
