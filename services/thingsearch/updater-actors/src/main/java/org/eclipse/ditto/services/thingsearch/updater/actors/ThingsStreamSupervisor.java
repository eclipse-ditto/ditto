/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.thingsearch.updater.actors;

import java.text.MessageFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoStreamModifiedEntities;
import org.eclipse.ditto.services.thingsearch.persistence.write.ThingsSearchSyncPersistence;
import org.eclipse.ditto.services.utils.akka.streaming.AbstractStreamSupervisor;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.cluster.pubsub.DistributedPubSub;
import akka.cluster.pubsub.DistributedPubSubMediator.Send;
import akka.japi.Creator;
import akka.stream.Materializer;
import akka.stream.scaladsl.Sink;
import scala.compat.java8.FutureConverters;

/**
 * This actor is responsible for triggering a cyclic synchronization of all things which changed within a specified time
 * period.
 */
public final class ThingsStreamSupervisor extends AbstractStreamSupervisor<Send> {

    /**
     * The name of this Actor in the ActorSystem.
     */
    static final String ACTOR_NAME = "thingsSynchronizer";
    @SuppressWarnings("squid:S1075")
    private static final String THINGS_ACTOR_PATH = "/user/thingsRoot/persistenceQueries";
    private final ActorRef pubSubMediator;
    private final ActorRef thingsUpdater;
    private final ThingsSearchSyncPersistence syncPersistence;
    private final Materializer materializer;
    private final Duration initialStartOffset;
    private final Duration pollInterval;
    private final Duration maxIdleTime;
    private final int elementsStreamedPerSecond;
    /* the offset for the start timestamp - needed to make sure that we don't lose events, cause the timestamp
       of a thing-event is created before the actual insert to the DB
     */
    private Duration startOffset;
    private @Nullable Instant currentStreamStartTs = null;
    private @Nullable Instant currentStreamEndTs = null;
    private @Nullable Instant lastSuccessfulStreamEndTs = null;

    private ThingsStreamSupervisor(final ActorRef thingsUpdater, final ThingsSearchSyncPersistence syncPersistence,
            final Materializer materializer,
            final Duration startOffset,
            final Duration initialStartOffset,
            final Duration pollInterval,
            final Duration maxIdleTime,
            final int elementsStreamedPerSecond) {
        this.thingsUpdater = thingsUpdater;
        this.syncPersistence = syncPersistence;
        this.materializer = materializer;

        this.startOffset = startOffset;
        this.initialStartOffset = initialStartOffset;
        this.pollInterval = pollInterval;
        this.maxIdleTime = maxIdleTime;
        this.elementsStreamedPerSecond = elementsStreamedPerSecond;

        pubSubMediator = DistributedPubSub.get(getContext().system()).mediator();
    }

    /**
     * Creates the props for {@link ThingsStreamSupervisor}.
     *
     * @param thingsUpdater the things updater actor
     * @param syncPersistence the sync persistence
     * @param materializer the materializer for the akka actor system.
     * @param startOffset the offset for the start timestamp - it is needed to make sure that we don't lose events,
     * cause the timestamp of a thing-event is created before the actual insert to the DB
     * @param initialStartOffset the duration starting from which the modified tags are requested for the first time
     * (further syncs will know the last-success timestamp)
     * @param pollInterval the duration for which the modified tags are requested (starting from last-success timestamp
     * or initialStartOffset)
     * @param maxIdleTime the maximum idle time of the underlying stream forwarder
     * @param elementsStreamedPerSecond the elements to be streamed per second
     * @return the props
     */
    public static Props props(final ActorRef thingsUpdater, final ThingsSearchSyncPersistence syncPersistence,
            final Materializer materializer,
            final Duration startOffset,
            final Duration initialStartOffset,
            final Duration pollInterval,
            final Duration maxIdleTime,
            final int elementsStreamedPerSecond) {
        return Props.create(ThingsStreamSupervisor.class, new Creator<ThingsStreamSupervisor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public ThingsStreamSupervisor create() throws Exception {
                return new ThingsStreamSupervisor(thingsUpdater, syncPersistence, materializer, startOffset,
                        initialStartOffset, pollInterval, maxIdleTime, elementsStreamedPerSecond);
            }
        });
    }

    @Override
    protected Duration getPollInterval() {
        return pollInterval;
    }

    @Override
    protected Props getStreamForwarderProps() {
        return ThingsStreamForwarder.props(thingsUpdater, getSelf(), maxIdleTime);
    }

    @Override
    protected void onSuccess() {
        if (currentStreamEndTs == null) {
            log.error("No currentStreamEndTs available, cannot update last sync timestamp.");
        } else {
            lastSuccessfulStreamEndTs = currentStreamEndTs;
            currentStreamStartTs = null;
            currentStreamEndTs = null;
            final String successMessage = MessageFormat
                    .format("Updating last sync timestamp to value: <{0}>", lastSuccessfulStreamEndTs);
            syncPersistence.updateLastSuccessfulSyncTimestamp(lastSuccessfulStreamEndTs)
                    .runWith(akka.stream.javadsl.Sink.last(), materializer)
                    .thenRun(() -> log.info(successMessage));

            // explicitly trigger streaming (independently from pollInterval) when we have to catch up..
            final Instant now = Instant.now();
            final Duration diffBetweenNowAndLastSuccessfulStreamEndTs =
                    Duration.between(lastSuccessfulStreamEndTs, now);
            if (diffBetweenNowAndLastSuccessfulStreamEndTs.compareTo(pollInterval) > 0) {
                log.warning("Difference between lastSuccessfulStreamEndTs <{}> and " +
                                " now <{}> is <{}>, which is greater than pollInterval <{}>. Explicitly trigger streaming.",
                        lastSuccessfulStreamEndTs, now, diffBetweenNowAndLastSuccessfulStreamEndTs, pollInterval);
                triggerStreaming();
            }
        }
    }

    @Override
    protected CompletionStage<Send> newStartStreamingCommand() {
        // short-cut: we do not need to access the database if last synch was successful
        if (lastSuccessfulStreamEndTs != null) {
            return CompletableFuture.completedFuture(
                    createStartStreamingCommand(lastSuccessfulStreamEndTs.minus(startOffset)));
        }

        // short-cut: if there was already a synch executed and it was not successful, re-trigger with this startTs
        if (currentStreamStartTs != null) {
            return CompletableFuture.completedFuture(createStartStreamingCommand(currentStreamStartTs));
        }

        // the initial start ts is only used when no sync has been run yet (i.e. no timestamp has been persisted)
        final Instant now = Instant.now();
        final Instant initialStartTsWithoutStandardOffset = now.minus(initialStartOffset);

        return FutureConverters.toJava(
                syncPersistence.retrieveLastSuccessfulSyncTimestamp(initialStartTsWithoutStandardOffset)
                        .map(startTsWithoutOffset -> startTsWithoutOffset.minus(startOffset))
                        .map(this::createStartStreamingCommand)
                        .runWith(Sink.<Send>head(), materializer));
    }

    private Send createStartStreamingCommand(final Instant startTs) {
        currentStreamStartTs = startTs;
        lastSuccessfulStreamEndTs = null;
        final Instant endTs = calculateEndTs(Instant.now(), startTs);
        currentStreamEndTs = endTs;

        final SudoStreamModifiedEntities retrieveModifiedThingTags =
                SudoStreamModifiedEntities.of(startTs, endTs, elementsStreamedPerSecond,
                        DittoHeaders.empty());

        return new Send(THINGS_ACTOR_PATH, retrieveModifiedThingTags, true);
    }

    private Instant calculateEndTs(final Instant now, final Instant startTs) {
        if (Duration.between(startTs, now).compareTo(pollInterval) > 0) {
            return startTs.plus(pollInterval);
        } else {
            return now;
        }
    }

    @Override
    protected Class<Send> getCommandClass() {
        return Send.class;
    }

    @Override
    protected ActorRef getStreamingActor() {
        return pubSubMediator;
    }
}
