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

import java.time.Duration;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.services.models.things.ThingTag;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoStreamModifiedEntities;
import org.eclipse.ditto.services.utils.akka.streaming.AbstractStreamSupervisor;
import org.eclipse.ditto.services.utils.akka.streaming.DefaultStreamForwarder;
import org.eclipse.ditto.services.utils.akka.streaming.StreamMetadataPersistence;
import org.eclipse.ditto.services.utils.akka.streaming.StreamTrigger;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.cluster.pubsub.DistributedPubSub;
import akka.cluster.pubsub.DistributedPubSubMediator.Send;
import akka.japi.Creator;
import akka.stream.Materializer;

/**
 * This actor is responsible for triggering a cyclic synchronization of all things which changed within a specified time
 * period.
 */
public final class ThingsStreamSupervisor extends AbstractStreamSupervisor<Send> {

    /**
     * The name of this Actor in the ActorSystem.
     */
    static final String ACTOR_NAME = "thingsStreamSupervisor";
    @SuppressWarnings("squid:S1075")
    private static final String THINGS_ACTOR_PATH = "/user/thingsRoot/persistenceQueries";
    private final ActorRef pubSubMediator;
    private final ActorRef thingsUpdater;
    private final int elementsStreamedPerSecond;
    private final Duration maxIdleTime;

    private ThingsStreamSupervisor(final ActorRef thingsUpdater, final StreamMetadataPersistence syncPersistence,
            final Materializer materializer,
            final Duration startOffset,
            final Duration streamInterval, final Duration initialStartOffset, final Duration warnOffset,
            final Duration maxIdleTime,
            final int elementsStreamedPerSecond, @Nullable final ActorRef pubSubMediator) {
        super(syncPersistence, materializer, startOffset, streamInterval, initialStartOffset, warnOffset);
        this.thingsUpdater = thingsUpdater;
        this.elementsStreamedPerSecond = elementsStreamedPerSecond;
        this.maxIdleTime = maxIdleTime;

        this.pubSubMediator =
                pubSubMediator != null ? pubSubMediator : DistributedPubSub.get(getContext().system()).mediator();
    }

    private ThingsStreamSupervisor(final ActorRef thingsUpdater, final StreamMetadataPersistence syncPersistence,
            final Materializer materializer,
            final Duration startOffset,
            final Duration streamInterval, final Duration initialStartOffset, final Duration warnOffset,
            final Duration maxIdleTime,
            final int elementsStreamedPerSecond) {
        this(thingsUpdater, syncPersistence, materializer, startOffset, streamInterval, initialStartOffset, warnOffset,
                maxIdleTime,
                elementsStreamedPerSecond, null);
    }

    /**
     * Creates the props for {@link ThingsStreamSupervisor}.
     *
     * @param thingsUpdater the things updater actor
     * @param syncPersistence the sync persistence
     * @param materializer the materializer for the akka actor system.
     * @param startOffset the offset for the start timestamp - it is needed to make sure that we don't lose events,
     * cause the timestamp of a thing-event is created before the actual insert to the DB
     * @param streamInterval this interval defines the minimum and maximum creation time of the entities to be queried
     * by the underlying stream.
     * @param initialStartOffset the duration starting from which the modified tags are requested for the first time
     * (further syncs will know the last-success timestamp)
     * @param warnOffset if a query-start is more than this offset in the past, a warning will be logged
     * @param maxIdleTime the maximum idle time of the underlying stream forwarder
     * @param elementsStreamedPerSecond the elements to be streamed per second
     * @return the props
     */
    public static Props props(final ActorRef thingsUpdater, final StreamMetadataPersistence syncPersistence,
            final Materializer materializer,
            final Duration startOffset,
            final Duration streamInterval, final Duration initialStartOffset, final Duration warnOffset,
            final Duration maxIdleTime,
            final int elementsStreamedPerSecond) {
        return Props.create(ThingsStreamSupervisor.class, new Creator<ThingsStreamSupervisor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public ThingsStreamSupervisor create() throws Exception {
                return new ThingsStreamSupervisor(thingsUpdater, syncPersistence, materializer, startOffset,
                        streamInterval, initialStartOffset, warnOffset, maxIdleTime, elementsStreamedPerSecond);
            }
        });
    }

    // for testing only
    static Props props(final ActorRef thingsUpdater, final StreamMetadataPersistence syncPersistence,
            final Materializer materializer,
            final Duration startOffset,
            final Duration streamInterval, final Duration initialStartOffset, final Duration warnOffset,
            final Duration maxIdleTime,
            final int elementsStreamedPerSecond, final ActorRef pubSubMediator) {
        return Props.create(ThingsStreamSupervisor.class, new Creator<ThingsStreamSupervisor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public ThingsStreamSupervisor create() throws Exception {
                return new ThingsStreamSupervisor(thingsUpdater, syncPersistence, materializer, startOffset,
                        streamInterval, initialStartOffset, warnOffset, maxIdleTime, elementsStreamedPerSecond,
                        pubSubMediator);
            }
        });
    }

    @Override
    protected Props getStreamForwarderProps() {
        return DefaultStreamForwarder.props(thingsUpdater, getSelf(), maxIdleTime,
                ThingTag.class, ThingTag::asIdentifierString);
    }

    @Override
    protected Send newStartStreamingCommand(final StreamTrigger streamRestrictions) {
        final SudoStreamModifiedEntities retrieveModifiedThingTags =
                SudoStreamModifiedEntities.of(streamRestrictions.getQueryStart(), streamRestrictions.getQueryEnd(),
                        elementsStreamedPerSecond, DittoHeaders.empty());

        return new Send(THINGS_ACTOR_PATH, retrieveModifiedThingTags, true);
    }

    @Override
    protected ActorRef getStreamingActor() {
        return pubSubMediator;
    }
}
