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
package org.eclipse.ditto.services.utils.pubsub.actors;

import java.util.Collection;

import org.eclipse.ditto.services.utils.metrics.DittoMetrics;
import org.eclipse.ditto.services.utils.metrics.instruments.counter.Counter;
import org.eclipse.ditto.services.utils.pubsub.PubSubTopicExtractor;
import org.eclipse.ditto.services.utils.pubsub.bloomfilter.LocalSubscriptions;
import org.eclipse.ditto.services.utils.pubsub.bloomfilter.LocalSubscriptionsReader;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.japi.pf.ReceiveBuilder;

/**
 * Actor that distributes messages to local subscribers
 *
 * @param <T> type of messages.
 */
public final class PubSubSubscriber<T> extends AbstractActor {

    private final Class<T> messageClass;
    private final PubSubTopicExtractor<T> topicExtractor;

    private LocalSubscriptionsReader localSubscriptions = LocalSubscriptions.empty();
    private Counter truePositiveCounter = DittoMetrics.counter("pubsub-true-positive");
    private Counter falsePositiveCounter = DittoMetrics.counter("pubsub-false-positive");

    private PubSubSubscriber(final Class<T> messageClass, final PubSubTopicExtractor<T> topicExtractor) {
        this.messageClass = messageClass;
        this.topicExtractor = topicExtractor;
    }

    // TODO: props

    @Override
    public Receive createReceive() {
        return ReceiveBuilder.create()
                .match(messageClass, this::broadcastToLocalSubscribers)
                .match(LocalSubscriptionsReader.class, this::updateLocalSubscriptions)
                .build();
    }

    private void broadcastToLocalSubscribers(final T message) {
        final Collection<String> topics = topicExtractor.getTopics(message);
        final Collection<ActorRef> localSubscribers = localSubscriptions.getSubscribers(topics);
        if (localSubscribers.isEmpty()) {
            falsePositiveCounter.increment();
        } else {
            truePositiveCounter.increment();
            for (final ActorRef localSubscriber : localSubscribers) {
                localSubscriber.tell(message, getSender());
            }
        }
    }

    private void updateLocalSubscriptions(final LocalSubscriptionsReader localSubscriptions) {
        this.localSubscriptions = localSubscriptions;
        getSender().tell(LocalSubscriptionsUpdated.INSTANCE, getSelf());
    }

    /**
     * Acknowledgement that this actor updated its local subscriptions.
     */
    public enum LocalSubscriptionsUpdated {
        INSTANCE
    }
}
