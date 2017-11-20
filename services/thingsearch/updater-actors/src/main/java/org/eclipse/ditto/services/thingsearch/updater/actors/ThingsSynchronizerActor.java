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
import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoStreamModifiedEntities;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.cluster.pubsub.DistributedPubSub;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.event.DiagnosticLoggingAdapter;
import akka.event.Logging;
import akka.japi.Creator;
import akka.japi.pf.ReceiveBuilder;
import scala.concurrent.duration.FiniteDuration;

/**
 * This actor is responsible for triggering a cyclic synchronization of all things which changed within a specified
 * time period.
 */
public final class ThingsSynchronizerActor extends AbstractActor {

    /**
     * The name of this Actor in the ActorSystem.
     */
    public static final String ACTOR_NAME = "thingsSynchronizer";
    private static final String THINGS_ACTOR_PATH = "/user/gatewayRoot/proxy";
    private final ActorRef pubSubMediator;
    private final ActorRef thingsUpdater;
    private final Duration modifiedSince;
    private final Duration modifiedOffset;
    private final DiagnosticLoggingAdapter log = Logging.apply(this);

    private ThingsSynchronizerActor(final ActorRef thingsUpdater, final Duration modifiedSince,
            final Duration modifiedOffset) {
        this.modifiedSince = modifiedSince;
        this.modifiedOffset = modifiedOffset;
        this.thingsUpdater = thingsUpdater;

        pubSubMediator = DistributedPubSub.get(getContext().system()).mediator();

        getContext().system().scheduler().schedule(new FiniteDuration(10, TimeUnit.SECONDS),
                new FiniteDuration(modifiedOffset.getSeconds(), TimeUnit.SECONDS), this::retrieveLastModifiedThingTags,
                getContext().dispatcher());
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
        return Props.create(ThingsSynchronizerActor.class, new Creator<ThingsSynchronizerActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public ThingsSynchronizerActor create() throws Exception {
                return new ThingsSynchronizerActor(thingsUpdater, modifiedSince, modifiedOffset);
            }
        });
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .matchAny(m -> {
                    log.warning("Unknown message: {}", m);
                    unhandled(m);
                }).build();
    }

    private void retrieveLastModifiedThingTags() {
        // TODO: make streaming rate configurable.
        // TODO: compute time window from timestamp of last successful full sync.
        final int temporaryStreamingRate = 100;
        final Instant now = Instant.now();
        final Instant start = now.minus(modifiedSince);
        final Instant end = now.minus(modifiedOffset);

        final SudoStreamModifiedEntities retrieveModifiedThingTags =
                SudoStreamModifiedEntities.of(start, end, temporaryStreamingRate, DittoHeaders.empty());

        pubSubMediator
                .tell(new DistributedPubSubMediator.Send(THINGS_ACTOR_PATH, retrieveModifiedThingTags, true),
                        getSelf());
    }
}
