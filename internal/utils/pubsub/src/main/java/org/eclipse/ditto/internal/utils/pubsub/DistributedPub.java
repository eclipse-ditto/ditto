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
package org.eclipse.ditto.internal.utils.pubsub;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.internal.utils.pubsub.extractors.AckExtractor;
import org.eclipse.ditto.internal.utils.pubsub.extractors.PubSubTopicExtractor;

import akka.actor.ActorRef;

/**
 * A jolly locale for the spreading of news.
 *
 * @param <T> type of messages.
 */
public interface DistributedPub<T> {

    /**
     * Get the publisher actor to send requests to.
     *
     * @return the publisher actor.
     */
    ActorRef getPublisher();

    /**
     * Wrap the message in an envelope to send to the publisher.
     *
     * @param message the message.
     * @param groupIndexKey the group index key used to select a subscriber from each group. Usually the entity ID
     * of a signal to ensure event order for each entity.
     * @return the wrapped message to send to the publisher.
     */
    Object wrapForPublication(T message, final CharSequence groupIndexKey);

    /**
     * Wrap the message in an envelope to send to the publisher.
     *
     * @param message the message to publish.
     * @param groupIndexKey the group index key used to select a subscriber from each group. Usually the entity ID
     * of a signal to ensure event order for each entity.
     * @param ackExtractor extractor of ack-related information from the message.
     * @return the wrapped message to send to the publisher.
     */
    <S extends T> Object wrapForPublicationWithAcks(S message, final CharSequence groupIndexKey,
            AckExtractor<S> ackExtractor);

    /**
     * Publish a message.
     *
     * @param message the message to publish.
     * @param groupIndexKey the group index key used to select a subscriber from each group. Usually the entity ID
     * of a signal to ensure event order for each entity.
     * @param sender reply address for all subscribers who receive this message.
     */
    default void publish(final T message, final CharSequence groupIndexKey, @Nullable final ActorRef sender) {
        getPublisher().tell(wrapForPublication(message, groupIndexKey), sender);
    }

    /**
     * Publish a message with acknowledgement requests.
     *
     * @param message the message to publish.
     * @param groupIndexKey the group index key used to select a subscriber from each group. Usually the entity ID
     * of a signal to ensure event order for each entity.
     * @param ackExtractor extractor of ack-related information from the message.
     * @param sender the sender of the message and the receiver of acknowledgements.
     */
    default void publishWithAcks(final T message, final CharSequence groupIndexKey, final AckExtractor<T> ackExtractor,
            @Nullable final ActorRef sender) {
        getPublisher().tell(wrapForPublicationWithAcks(message, groupIndexKey, ackExtractor), sender);
    }

    /**
     * Create a new interface of this distributed-pub with a new topic extractor.
     *
     * @param topicExtractor the previous topic extractor.
     * @return a new interface of this object.
     */
    default <S extends Signal<?>> DistributedPub<S> withTopicExtractor(final PubSubTopicExtractor<S> topicExtractor) {
        return DistributedPubWithTopicExtractor.of(this, topicExtractor);
    }

    /**
     * Create publication access from an already-started pub-supervisor and topic extractor.
     *
     * @param pubSupervisor the pub-supervisor.
     * @param topicExtractor the topic extractor.
     * @param <T> the type of messages.
     * @return the publication access.
     */
    static <T extends Signal<?>> DistributedPub<T> of(final ActorRef pubSupervisor,
            final PubSubTopicExtractor<T> topicExtractor) {
        return new DistributedPubImpl<>(pubSupervisor, topicExtractor);
    }

}
