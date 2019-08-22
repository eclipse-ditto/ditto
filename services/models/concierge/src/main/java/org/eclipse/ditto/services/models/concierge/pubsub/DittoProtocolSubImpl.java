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
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.eclipse.ditto.services.models.concierge.streaming.StreamingType;
import org.eclipse.ditto.services.utils.pubsub.DistributedSub;
import org.eclipse.ditto.services.utils.pubsub.actors.SubUpdater;
import org.eclipse.ditto.services.utils.pubsub.config.PubSubConfig;
import org.eclipse.ditto.signals.base.Signal;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;

/**
 * Default implementation of {@link org.eclipse.ditto.services.models.concierge.pubsub.DittoProtocolSub}.
 */
final class DittoProtocolSubImpl implements DittoProtocolSub {

    private final DistributedSub distributedSub;

    private DittoProtocolSubImpl(final DistributedSub distributedSub) {
        this.distributedSub = distributedSub;
    }

    static DittoProtocolSubImpl of(final ActorSystem actorSystem) {
        final PubSubConfig config = PubSubConfig.of(actorSystem);
        final DistributedSub distributedSub =
                LiveAndTwinSignalPubSubFactory.of(actorSystem, config, Signal.class,
                        LiveAndTwinSignalPubSubFactory.topicExtractor())
                        .startDistributedSub();
        return new DittoProtocolSubImpl(distributedSub);
    }

    @Override
    public CompletionStage<SubUpdater.Acknowledgement> subscribe(final Collection<StreamingType> types,
            final Collection<String> topics,
            final ActorRef subscriber) {
        return distributedSub.subscribeWithFilterAndAck(topics, subscriber, toFilter(types));
    }

    @Override
    public void removeSubscriber(final ActorRef subscriber) {
        distributedSub.removeSubscriber(subscriber);
    }

    @Override
    public CompletionStage<SubUpdater.Acknowledgement> updateSubscription(final Collection<StreamingType> types,
            final Collection<String> topics,
            final ActorRef subscriber) {

        if (types.isEmpty()) {
            return distributedSub.unsubscribeWithAck(topics, subscriber);
        } else {
            return distributedSub.subscribeWithFilterAndAck(topics, subscriber, toFilter(types));
        }
    }

    private static Predicate<Collection<String>> toFilter(final Collection<StreamingType> streamingTypes) {
        final Set<String> streamingTypeTopics =
                streamingTypes.stream().map(StreamingType::getDistributedPubSubTopic).collect(Collectors.toSet());
        return topics -> topics.stream().anyMatch(streamingTypeTopics::contains);
    }

}
