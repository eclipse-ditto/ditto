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

import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.services.models.thingsearch.commands.sudo.UpdateThing;
import org.eclipse.ditto.services.thingsearch.common.config.BackgroundSyncConfig;
import org.eclipse.ditto.services.thingsearch.common.config.DefaultBackgroundSyncConfig;
import org.eclipse.ditto.services.thingsearch.persistence.read.ThingsSearchPersistence;
import org.eclipse.ditto.services.thingsearch.persistence.write.model.Metadata;
import org.eclipse.ditto.services.thingsearch.persistence.write.streaming.BackgroundSyncStream;
import org.eclipse.ditto.services.utils.akka.controlflow.ResumeSource;
import org.eclipse.ditto.services.utils.akka.streaming.TimestampPersistence;
import org.eclipse.ditto.services.utils.health.AbstractBackgroundStreamingActorWithConfigWithStatusReport;

import com.typesafe.config.Config;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;
import akka.stream.javadsl.Sink;
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
    private final TimestampPersistence backgroundSyncPersistence;
    private final BackgroundSyncStream backgroundSyncStream;
    private final ActorRef thingsUpdater;

    private ThingId thingIdToBookmark;

    private BackgroundSyncActor(final BackgroundSyncConfig backgroundSyncConfig,
            final ThingsMetadataSource thingsMetadataSource,
            final ThingsSearchPersistence thingsSearchPersistence,
            final TimestampPersistence backgroundSyncPersistence,
            final BackgroundSyncStream backgroundSyncStream,
            final ActorRef thingsUpdater) {
        super(backgroundSyncConfig);
        this.thingsMetadataSource = thingsMetadataSource;
        this.thingsSearchPersistence = thingsSearchPersistence;
        this.backgroundSyncPersistence = backgroundSyncPersistence;
        this.backgroundSyncStream = backgroundSyncStream;
        this.thingsUpdater = thingsUpdater;
        thingIdToBookmark = ThingId.dummy();

        getTimers().startPeriodicTimer(Control.BOOKMARK_THING_ID, Control.BOOKMARK_THING_ID, config.getQuietPeriod());
    }

    /**
     * Create Akka Props object for the background sync actor.
     *
     * @param config the config of the background sync actor.
     * @param pubSubMediator Akka pub-sub mediator.
     * @param thingsSearchPersistence the search persistence to access the search index.
     * @param backgroundSyncPersistence persistence for bookmarks of background sync progress.
     * @param policiesShardRegion the policies shard region to query policy revisions.
     * @param thingsUpdater the dispatcher of UpdateThing commands.
     * @return an actor to coordinate background sync.
     */
    public static Props props(final BackgroundSyncConfig config,
            final ActorRef pubSubMediator,
            final ThingsSearchPersistence thingsSearchPersistence,
            final TimestampPersistence backgroundSyncPersistence,
            final ActorRef policiesShardRegion,
            final ActorRef thingsUpdater) {

        final ThingsMetadataSource thingsMetadataSource =
                ThingsMetadataSource.of(pubSubMediator, config.getThrottleThroughput(), config.getIdleTimeout());
        final BackgroundSyncStream backgroundSyncStream =
                BackgroundSyncStream.of(policiesShardRegion, config.getPolicyAskTimeout(),
                        config.getToleranceWindow(), config.getThrottleThroughput(), config.getThrottlePeriod());

        return Props.create(BackgroundSyncActor.class, config, thingsMetadataSource, thingsSearchPersistence,
                backgroundSyncPersistence, backgroundSyncStream, thingsUpdater);
    }

    @Override
    protected void preEnhanceSleepingBehavior(final ReceiveBuilder sleepingReceiveBuilder) {
        sleepingReceiveBuilder.matchEquals(Control.BOOKMARK_THING_ID,
                trigger -> {
                    // ignore scheduled bookmark messages when sleeping
                    log.debug("Ignoring: <{}>", trigger);
                })
                .match(ThingId.class, thingId -> {
                    // got outdated progress update message after actor resumes sleeping; ignore it.
                    log.debug("Ignoring: <{}>", thingId);
                });
    }

    @Override
    protected void preEnhanceStreamingBehavior(final ReceiveBuilder streamingReceiveBuilder) {
        streamingReceiveBuilder.match(ThingId.class, thingId -> thingIdToBookmark = thingId)
                .matchEquals(Control.BOOKMARK_THING_ID, this::bookmarkThingId);
    }

    @Override
    protected void postEnhanceStatusReport(final JsonObjectBuilder statusReportBuilder) {
        statusReportBuilder.set("progress", thingIdToBookmark.toString());
    }

    @Override
    protected BackgroundSyncConfig parseConfig(final Config config) {
        return DefaultBackgroundSyncConfig.parse(config);
    }

    @Override
    protected void streamTerminated(final Event streamTerminated) {
        super.streamTerminated(streamTerminated);
        // reset progress for the next round
        thingIdToBookmark = ThingId.dummy();
        doBookmarkThingId("");
    }

    @Override
    protected Source<?, ?> getSource() {
        return getLowerBoundSource()
                .flatMapConcat(this::streamMetadataFromLowerBound)
                .wireTap(this::handleInconsistency);
    }

    private Source<Metadata, NotUsed> streamMetadataFromLowerBound(final ThingId lowerBound) {
        final Source<Metadata, NotUsed> persistedMetadata =
                getPersistedMetadataSourceWithProgressReporting(lowerBound);
        final Source<Metadata, NotUsed> indexedMetadata = getIndexedMetadataSource(lowerBound);
        return backgroundSyncStream.filterForInconsistencies(persistedMetadata, indexedMetadata);
    }

    private void bookmarkThingId(final Control bookmarkRequest) {
        if (!thingIdToBookmark.isDummy()) {
            doBookmarkThingId(thingIdToBookmark.toString());
        }
    }

    private void doBookmarkThingId(final String bookmark) {
        backgroundSyncPersistence.setTaggedTimestamp(Instant.now(), bookmark)
                .runWith(Sink.ignore(), materializer);
    }

    private void handleInconsistency(final Metadata metadata) {
        final ThingId thingId = metadata.getThingId();
        thingsUpdater.tell(UpdateThing.of(thingId, DittoHeaders.empty()), ActorRef.noSender());
        getSelf().tell(SyncEvent.inconsistency(metadata), ActorRef.noSender());
    }

    private Source<ThingId, NotUsed> getLowerBoundSource() {
        return backgroundSyncPersistence.getTaggedTimestamp()
                .map(optional -> {
                    if (optional.isPresent()) {
                        final String bookmarkedThingId = optional.get().second();
                        if (bookmarkedThingId != null && !bookmarkedThingId.isEmpty())
                            return ThingId.of(bookmarkedThingId);
                    }
                    return ThingId.dummy();
                });
    }

    private Source<Metadata, NotUsed> getPersistedMetadataSourceWithProgressReporting(final ThingId lowerBound) {
        return wrapAsResumeSource(lowerBound, thingsMetadataSource::createSource)
                .wireTap(persisted -> getSelf().tell(persisted.getThingId(), ActorRef.noSender()));
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

        @Override
        public String name() {
            return description;
        }

        @Override
        public String toString() {
            return description;
        }
    }

    private enum Control {
        BOOKMARK_THING_ID
    }
}
