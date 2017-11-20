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
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoStreamModifiedEntities;
import org.eclipse.ditto.services.utils.akka.streaming.AbstractStreamSupervisor;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.cluster.pubsub.DistributedPubSub;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.japi.Creator;

/**
 * This actor is responsible for triggering a cyclic synchronization of all things which changed within a specified
 * time period.
 */
public final class ThingsStreamSupervisor extends AbstractStreamSupervisor<DistributedPubSubMediator.Send> {

    /**
     * The name of this Actor in the ActorSystem.
     */
    public static final String ACTOR_NAME = "thingsSynchronizer";
    private static final String THINGS_ACTOR_PATH = "/user/gatewayRoot/proxy";
    private final ActorRef pubSubMediator;
    private final ActorRef thingsUpdater;
    private final Duration modifiedSince;
    private final Duration modifiedOffset;

    private ThingsStreamSupervisor(final ActorRef thingsUpdater, final Duration modifiedSince,
            final Duration modifiedOffset) {
        this.modifiedSince = modifiedSince;
        this.modifiedOffset = modifiedOffset;
        this.thingsUpdater = thingsUpdater;

        pubSubMediator = DistributedPubSub.get(getContext().system()).mediator();
    }

    /**
     * Creates the props for {@code ThingsSynchronizerActor}.
     *
     * @param thingsUpdater the things updater actor
     * @param modifiedSince the duration for which the modified tags are requested
     * @param modifiedOffset the offset from which the modified tags are ignored
     * @return the props
     */
    public static Props props(final ActorRef thingsUpdater, final Duration modifiedSince,
            final Duration modifiedOffset) {
        return Props.create(ThingsStreamSupervisor.class, new Creator<ThingsStreamSupervisor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public ThingsStreamSupervisor create() throws Exception {
                return new ThingsStreamSupervisor(thingsUpdater, modifiedSince, modifiedOffset);
            }
        });
    }

    @Override
    protected Duration getPollInterval() {
        return modifiedOffset;
    }

    @Override
    protected Props getStreamForwarderProps() {
        // TODO: make max idle time configurable.
        return ThingsStreamForwarder.props(thingsUpdater, Duration.ofMinutes(1));
    }

    @Override
    protected CompletionStage<DistributedPubSubMediator.Send> newStartStreamingCommand() {
        // TODO: make streaming rate configurable.
        // TODO: compute time window from timestamp of last successful full sync.
        final int temporaryStreamingRate = 100;
        final Instant now = Instant.now();
        final Instant start = now.minus(modifiedSince);
        final Instant end = now.minus(modifiedOffset);

        final SudoStreamModifiedEntities retrieveModifiedThingTags =
                SudoStreamModifiedEntities.of(start, end, temporaryStreamingRate, DittoHeaders.empty());

        final DistributedPubSubMediator.Send sendCommand =
                new DistributedPubSubMediator.Send(THINGS_ACTOR_PATH, retrieveModifiedThingTags, true);

        return CompletableFuture.completedFuture(sendCommand);
    }

    @Override
    protected Class<DistributedPubSubMediator.Send> getCommandClass() {
        return DistributedPubSubMediator.Send.class;
    }

    @Override
    protected ActorRef getStreamingActor() {
        return pubSubMediator;
    }
}
