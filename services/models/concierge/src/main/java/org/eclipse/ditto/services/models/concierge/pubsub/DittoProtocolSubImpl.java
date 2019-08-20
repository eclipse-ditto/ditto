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
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.eclipse.ditto.services.models.concierge.streaming.StreamingType;
import org.eclipse.ditto.services.models.things.ThingEventPubSubFactory;
import org.eclipse.ditto.services.utils.pubsub.DistributedSub;
import org.eclipse.ditto.services.utils.pubsub.actors.SubUpdater;
import org.eclipse.ditto.services.utils.pubsub.config.PubSubConfig;
import org.eclipse.ditto.signals.base.Signal;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.events.base.Event;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;

/**
 * Default implementation of {@link org.eclipse.ditto.services.models.concierge.pubsub.DittoProtocolSub}.
 */
final class DittoProtocolSubImpl implements DittoProtocolSub {

    private final Map<StreamingType, DistributedSub> subMap;

    private DittoProtocolSubImpl(final Map<StreamingType, DistributedSub> subMap) {
        this.subMap = subMap;
    }

    static DittoProtocolSubImpl of(final ActorSystem actorSystem) {
        final PubSubConfig config = PubSubConfig.of(actorSystem);
        final Map<StreamingType, DistributedSub> subMap = new EnumMap<>(StreamingType.class);
        subMap.put(StreamingType.EVENTS,
                ThingEventPubSubFactory.readSubjectsOnly(actorSystem, config).startDistributedSub());
        subMap.put(StreamingType.LIVE_COMMANDS,
                SingleLiveSignalPubSubFactory.of(actorSystem, config, Command.class, StreamingType.LIVE_COMMANDS)
                        .startDistributedSub());
        subMap.put(StreamingType.LIVE_EVENTS,
                SingleLiveSignalPubSubFactory.of(actorSystem, config, Event.class, StreamingType.LIVE_EVENTS)
                        .startDistributedSub());
        subMap.put(StreamingType.MESSAGES,
                SingleLiveSignalPubSubFactory.of(actorSystem, config, Signal.class, StreamingType.MESSAGES)
                        .startDistributedSub());
        return new DittoProtocolSubImpl(subMap);
    }

    @Override
    public CompletionStage<Void> subscribe(final Collection<StreamingType> types, final Collection<String> topics,
            final ActorRef subscriber) {
        return CompletableFuture.allOf(types.stream()
                .map(subMap::get)
                .map(sub -> sub.subscribeWithAck(topics, subscriber).toCompletableFuture())
                .toArray(CompletableFuture[]::new)
        );
    }

    @Override
    public CompletionStage<SubUpdater.Acknowledgement> subscribe(final StreamingType type,
            final Collection<String> topics, final ActorRef subscriber) {
        return subMap.get(type).subscribeWithAck(topics, subscriber);
    }

    @Override
    public void removeSubscriber(final Collection<StreamingType> types, final ActorRef subscriber) {
        types.stream()
                .map(subMap::get)
                .forEach(sub -> sub.removeSubscriber(subscriber));
    }

    @Override
    public CompletionStage<SubUpdater.Acknowledgement> unsubscribe(final StreamingType type,
            final Collection<String> topics,
            final ActorRef subscriber) {
        return null;
    }
}
