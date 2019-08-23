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
package org.eclipse.ditto.services.models.concierge.pubsub;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.eclipse.ditto.services.models.concierge.streaming.StreamingType;
import org.eclipse.ditto.services.models.things.ThingEventPubSubFactory;
import org.eclipse.ditto.services.utils.pubsub.DistributedSub;
import org.eclipse.ditto.services.utils.pubsub.config.PubSubConfig;
import org.eclipse.ditto.signals.base.Signal;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;

/**
 * Default implementation of {@link DittoProtocolSub}.
 */
final class DittoProtocolSubImpl implements DittoProtocolSub {

    private final DistributedSub liveSignalSub;
    private final DistributedSub twinEventSub;

    private DittoProtocolSubImpl(final DistributedSub liveSignalSub,
            final DistributedSub twinEventSub) {
        this.liveSignalSub = liveSignalSub;
        this.twinEventSub = twinEventSub;
    }

    static DittoProtocolSubImpl of(final ActorSystem actorSystem) {
        final PubSubConfig config = PubSubConfig.of(actorSystem);
        final DistributedSub liveSignalSub =
                LiveSignalPubSubFactory.of(actorSystem, config, Signal.class).startDistributedSub();
        final DistributedSub twinEventSub =
                ThingEventPubSubFactory.readSubjectsOnly(actorSystem, config).startDistributedSub();
        return new DittoProtocolSubImpl(liveSignalSub, twinEventSub);
    }

    @Override
    public CompletionStage<Void> subscribe(final Collection<StreamingType> types,
            final Collection<String> topics,
            final ActorRef subscriber) {
        final CompletionStage<?> nop = CompletableFuture.completedFuture(null);
        return partitionByStreamingTypes(types,
                liveTypes -> !liveTypes.isEmpty()
                        ? liveSignalSub.subscribeWithFilterAndAck(topics, subscriber, toFilter(liveTypes))
                        : nop,
                hasTwinEvents -> hasTwinEvents
                        ? twinEventSub.subscribeWithAck(topics, subscriber)
                        : nop
        );
    }

    @Override
    public void removeSubscriber(final ActorRef subscriber) {
        liveSignalSub.removeSubscriber(subscriber);
        twinEventSub.removeSubscriber(subscriber);
    }

    @Override
    public CompletionStage<Void> updateLiveSubscriptions(final Collection<StreamingType> types,
            final Collection<String> topics,
            final ActorRef subscriber) {

        return partitionByStreamingTypes(types,
                liveTypes -> !liveTypes.isEmpty()
                        ? liveSignalSub.subscribeWithFilterAndAck(topics, subscriber, toFilter(liveTypes))
                        : liveSignalSub.unsubscribeWithAck(topics, subscriber),
                hasTwinEvents -> CompletableFuture.completedFuture(null)
        );
    }

    @Override
    public CompletionStage<Void> removeTwinSubscriber(final ActorRef subscriber, final Collection<String> topics) {
        return twinEventSub.unsubscribeWithAck(topics, subscriber).thenApply(ack -> null);
    }

    private CompletionStage<Void> partitionByStreamingTypes(final Collection<StreamingType> types,
            final Function<Set<StreamingType>, CompletionStage<?>> onLiveSignals,
            final Function<Boolean, CompletionStage<?>> onTwinEvents) {
        final Set<StreamingType> liveTypes;
        final boolean hasTwinEvents;
        if (types.isEmpty()) {
            liveTypes = Collections.emptySet();
            hasTwinEvents = false;
        } else {
            liveTypes = EnumSet.copyOf(types);
            hasTwinEvents = liveTypes.remove(StreamingType.EVENTS);
        }
        final CompletableFuture<?> liveStage = onLiveSignals.apply(liveTypes).toCompletableFuture();
        final CompletableFuture<?> twinStage = onTwinEvents.apply(hasTwinEvents).toCompletableFuture();
        return CompletableFuture.allOf(liveStage, twinStage);
    }

    private static Predicate<Collection<String>> toFilter(final Collection<StreamingType> streamingTypes) {
        final Set<String> streamingTypeTopics =
                streamingTypes.stream().map(StreamingType::getDistributedPubSubTopic).collect(Collectors.toSet());
        return topics -> topics.stream().anyMatch(streamingTypeTopics::contains);
    }

}
