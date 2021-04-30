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
package org.eclipse.ditto.thingsearch.service.updater.actors;

import java.time.Instant;
import java.util.Deque;
import java.util.List;
import java.util.function.Function;

import org.eclipse.ditto.json.JsonObjectBuilder;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.things.model.ThingConstants;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.internal.models.streaming.LowerBound;
import org.eclipse.ditto.thingsearch.api.commands.sudo.UpdateThing;
import org.eclipse.ditto.thingsearch.service.common.config.BackgroundSyncConfig;
import org.eclipse.ditto.thingsearch.service.common.config.DefaultBackgroundSyncConfig;
import org.eclipse.ditto.thingsearch.service.persistence.read.ThingsSearchPersistence;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.Metadata;
import org.eclipse.ditto.thingsearch.service.persistence.write.streaming.BackgroundSyncStream;
import org.eclipse.ditto.internal.utils.akka.controlflow.ResumeSource;
import org.eclipse.ditto.internal.utils.akka.streaming.TimestampPersistence;
import org.eclipse.ditto.internal.utils.health.AbstractBackgroundStreamingActorWithConfigWithStatusReport;
import org.eclipse.ditto.internal.utils.health.StatusDetailMessage;

import com.typesafe.config.Config;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.Pair;
import akka.japi.pf.ReceiveBuilder;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;

/**
 * Cluster-singleton responsible for background synchronization.
 */
public final class BackgroundSyncActor
        extends AbstractBackgroundStreamingActorWithConfigWithStatusReport<BackgroundSyncConfig> {

    private static final ThingId EMPTY_THING_ID = ThingId.of(LowerBound.emptyEntityId(ThingConstants.ENTITY_TYPE));
    /**
     * Name of the singleton coordinator.
     */
    public static final String ACTOR_NAME = "backgroundSync";

    private final ThingsMetadataSource thingsMetadataSource;
    private final ThingsSearchPersistence thingsSearchPersistence;
    private final TimestampPersistence backgroundSyncPersistence;
    private final BackgroundSyncStream backgroundSyncStream;
    private final ActorRef thingsUpdater;

    private ThingId progressPersisted = EMPTY_THING_ID;
    private ThingId progressIndexed = EMPTY_THING_ID;

    @SuppressWarnings("unused")
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
                trigger ->
                        // ignore scheduled bookmark messages when sleeping
                        log.debug("Ignoring: <{}>", trigger)
        )
                .match(ThingId.class, thingId ->
                        // got outdated progress update message after actor resumes sleeping; ignore it.
                        log.debug("Ignoring: <{}>", thingId)
                );
    }

    @Override
    protected void preEnhanceStreamingBehavior(final ReceiveBuilder streamingReceiveBuilder) {
        streamingReceiveBuilder.match(ProgressReport.class, this::setProgress)
                .matchEquals(Control.BOOKMARK_THING_ID, this::bookmarkThingId);
    }

    @Override
    protected void postEnhanceStatusReport(final JsonObjectBuilder statusReportBuilder) {
        statusReportBuilder.set("progressPersisted", progressPersisted.toString());
        statusReportBuilder.set("progressIndexed", progressIndexed.toString());
    }

    @Override
    protected BackgroundSyncConfig parseConfig(final Config config) {
        return DefaultBackgroundSyncConfig.parse(config);
    }

    @Override
    protected void streamTerminated(final Event streamTerminated) {
        super.streamTerminated(streamTerminated);
        // reset progress for the next round
        progressPersisted = EMPTY_THING_ID;
        progressIndexed = EMPTY_THING_ID;
        doBookmarkThingId("");
    }

    @Override
    protected Source<?, ?> getSource() {
        return getLowerBoundSource()
                .flatMapConcat(this::streamMetadataFromLowerBound)
                .wireTap(this::handleInconsistency);
    }

    @Override
    protected StatusDetailMessage.Level getMostSevereLevelFromEvents(final Deque<Pair<Instant, Event>> events) {
        // ignore status detail message after the second "StreamTerminated" so that only the ongoing sync and
        // the last completed sync count
        StatusDetailMessage.Level level = StatusDetailMessage.Level.DEFAULT;
        int terminationCount = 0;
        for (final var pair : events) {
            final var event = pair.second();
            final var eventLevel = event.level();
            level = level.compareTo(eventLevel) >= 0 ? level : eventLevel;
            if (event instanceof StreamTerminated && ++terminationCount > 1) {
                break;
            }
        }
        return level;
    }

    private Source<Metadata, NotUsed> streamMetadataFromLowerBound(final ThingId lowerBound) {
        final Source<Metadata, NotUsed> persistedMetadata =
                getPersistedMetadataSourceWithProgressReporting(lowerBound);
        final Source<Metadata, NotUsed> indexedMetadata = getIndexedMetadataSource(lowerBound);
        return backgroundSyncStream.filterForInconsistencies(persistedMetadata, indexedMetadata);
    }

    private void setProgress(ProgressReport progress) {
        if (progress.persisted) {
            progressPersisted = progress.thingId;
        } else {
            progressIndexed = progress.thingId;
        }
    }

    private void bookmarkThingId(final Control bookmarkRequest) {
        // bookmark the smaller ID between progressed and indexed according to background sync stream processing order
        final ThingId thingIdToBookmark = BackgroundSyncStream.compareThingIds(progressIndexed, progressPersisted) <= 0
                ? progressIndexed
                : progressPersisted;
        if (!thingIdToBookmark.equals(EMPTY_THING_ID)) {
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
        if (isInconsistentAgain(metadata)) {
            getSelf().tell(SyncEvent.inconsistencyAgain(metadata), ActorRef.noSender());
        } else {
            getSelf().tell(SyncEvent.inconsistency(metadata), ActorRef.noSender());
        }
    }

    private boolean isInconsistentAgain(final Metadata metadata) {
        // check if the previous events already contain a SyncEvent for the same thing with the same revision
        return this.getEventStream()
                .map(Pair::second)
                // only consider events in the previous run
                .dropWhile(event -> !(event instanceof StreamTerminated))
                .takeWhile(event -> !(event instanceof WokeUp))
                .filter(event -> SyncEvent.class.isAssignableFrom(event.getClass()))
                .map(event -> (SyncEvent) event)
                .anyMatch(event -> metadata.getThingId().equals(event.thingId) &&
                        metadata.getThingRevision() == event.thingRevision);
    }

    private Source<ThingId, NotUsed> getLowerBoundSource() {
        return backgroundSyncPersistence.getTaggedTimestamp()
                .map(optional -> {
                    if (optional.isPresent()) {
                        final String bookmarkedThingId = optional.get().second();
                        if (bookmarkedThingId != null && !bookmarkedThingId.isEmpty())
                            return ThingId.of(bookmarkedThingId);
                    }
                    return EMPTY_THING_ID;
                });
    }

    private Source<Metadata, NotUsed> getPersistedMetadataSourceWithProgressReporting(final ThingId lowerBound) {
        return wrapAsResumeSource(lowerBound, thingsMetadataSource::createSource)
                .wireTap(persisted ->
                        getSelf().tell(new ProgressReport(persisted.getThingId(), true), ActorRef.noSender()));
    }

    private Source<Metadata, NotUsed> getIndexedMetadataSource(final ThingId lowerBound) {
        return wrapAsResumeSource(lowerBound, thingsSearchPersistence::sudoStreamMetadata)
                .wireTap(indexed ->
                        getSelf().tell(new ProgressReport(indexed.getThingId(), false), ActorRef.noSender()));
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
        private final ThingId thingId;
        private final long thingRevision;
        private final StatusDetailMessage.Level level;

        private SyncEvent(final String description, final ThingId thingId, final long thingRevision,
                final StatusDetailMessage.Level level) {
            this.description = description;
            this.thingId = thingId;
            this.thingRevision = thingRevision;
            this.level = level;
        }

        private static Event inconsistency(final Metadata metadata) {
            return new SyncEvent("Inconsistent: " + metadata, metadata.getThingId(), metadata.getThingRevision(),
                    StatusDetailMessage.Level.DEFAULT);
        }

        private static Event inconsistencyAgain(final Metadata metadata) {
            return new SyncEvent("Inconsistent again: " + metadata, metadata.getThingId(), metadata.getThingRevision(),
                    StatusDetailMessage.Level.WARN);
        }

        @Override
        public String name() {
            return description;
        }

        @Override
        public String toString() {
            return description;
        }

        @Override
        public StatusDetailMessage.Level level() {
            return level;
        }

    }

    private static final class ProgressReport {

        private final ThingId thingId;
        private final boolean persisted;

        private ProgressReport(final ThingId thingId, final boolean persisted) {
            this.thingId = thingId;
            this.persisted = persisted;
        }
    }

    private enum Control {
        BOOKMARK_THING_ID
    }
}
