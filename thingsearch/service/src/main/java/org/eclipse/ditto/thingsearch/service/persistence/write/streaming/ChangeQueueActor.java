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

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.thingsearch.service.persistence.write.model.Metadata;

import akka.NotUsed;
import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.japi.function.Function;
import akka.japi.pf.ReceiveBuilder;
import akka.pattern.Patterns;
import akka.stream.Attributes;
import akka.stream.javadsl.Source;

/**
 * Collects changes from ThingUpdaters and forward them downstream on demand.
 */
public final class ChangeQueueActor extends AbstractActor {

    /**
     * Name of this actor.
     */
    public static final String ACTOR_NAME = "changeQueueActor";

    private static final Duration ASK_SELF_TIMEOUT = Duration.ofSeconds(5L);

    /**
     * Caching changes of 1 Thing per key.
     * Change type values according to caching strategy;
     * for example, replace AtomicReference by a concurrent queue if changes are to be computed from events directly.
     */
    private Map<ThingId, Metadata> cache = new HashMap<>();
    private Map<ThingId, Metadata> cacheShouldAcknowledge = new HashMap<>();

    private ChangeQueueActor() {
        // prevent instantiation elsewhere
    }

    /**
     * @return Props of a ChangeQueueActor.
     */
    public static Props props() {
        return Props.create(ChangeQueueActor.class)
                .withMailbox("akka.actor.mailbox.unbounded-control-aware-queue-based");
    }

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(Metadata.class, this::enqueue)
                .match(Control.class, this::dump)
                .build();
    }

    /**
     * Enqueue a change.
     *
     * @param metadata a description of the change.
     */
    private void enqueue(final Metadata metadata) {
        if (metadata.getSenders().isEmpty()) {
            ConsistencyLag.startS1InChangeQueue(metadata);
            cache.merge(metadata.getThingId(), metadata, Metadata::append);
        } else {
            ConsistencyLag.startS1InChangeQueue(metadata);
            cacheShouldAcknowledge.merge(metadata.getThingId(), metadata, Metadata::append);
        }
    }

    /**
     * Create a source of nonempty queue snapshots such that the queue content is cleared after each snapshot.
     *
     * @param changeQueueActor reference to this actor
     * @param shouldAcknowledge defines whether for the created source the requested ack
     * {@link org.eclipse.ditto.base.model.acks.DittoAcknowledgementLabel#SEARCH_PERSISTED} was required or not.
     * @param writeInterval minimum delays between cache dumps.
     * @return source of queue snapshots.
     */
    public static Source<Map<ThingId, Metadata>, NotUsed> createSource(
            final ActorRef changeQueueActor,
            final boolean shouldAcknowledge,
            final Duration writeInterval) {

        final Source<Control, NotUsed> repeat;
        if (!writeInterval.isNegative() && !writeInterval.isZero()) {
            repeat = Source.repeat(shouldAcknowledge ? Control.DUMP_SHOULD_ACKNOWLEDGE : Control.DUMP)
                    .throttle(1, writeInterval);
        } else {
            repeat = Source.repeat(shouldAcknowledge ? Control.DUMP_SHOULD_ACKNOWLEDGE : Control.DUMP);
        }
        return repeat
                .flatMapConcat(ChangeQueueActor.askSelf(changeQueueActor))
                .filter(map -> !map.isEmpty());
    }

    private void dump(final Control dump) {
        if (dump == Control.DUMP) {
            cache.values().forEach(ConsistencyLag::startS2WaitForDemand);
            getSender().tell(cache, getSelf());
            cache = new HashMap<>();
        } else if (dump == Control.DUMP_SHOULD_ACKNOWLEDGE) {
            cacheShouldAcknowledge.values().forEach(ConsistencyLag::startS2WaitForDemand);
            getSender().tell(cacheShouldAcknowledge, getSelf());
            cacheShouldAcknowledge = new HashMap<>();
        } else {
            throw new IllegalArgumentException("Unsupported control dump message: " + dump);
        }
    }

    @SuppressWarnings("unchecked")
    private static Function<Control, Source<Map<ThingId, Metadata>, NotUsed>> askSelf(final ActorRef self) {
        return message -> Source.completionStageSource(
                        Patterns.ask(self, message, ASK_SELF_TIMEOUT)
                                .handle((result, error) -> {
                                    if (result instanceof Map) {
                                        return Source.single((Map<ThingId, Metadata>) result);
                                    } else {
                                        return Source.empty();
                                    }
                                }))
                .withAttributes(Attributes.inputBuffer(1, 1))
                .mapMaterializedValue(whatever -> NotUsed.getInstance());
    }

    // DO NOT give control messages priority over Metadata.
    // We want Metadata of each thing to stay in change queue as long as possible in order to aggregate updates
    // to reduce DB load.
    enum Control {
        DUMP,
        DUMP_SHOULD_ACKNOWLEDGE
    }
}
