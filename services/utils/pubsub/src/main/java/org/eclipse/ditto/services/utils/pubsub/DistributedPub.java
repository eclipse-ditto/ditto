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
package org.eclipse.ditto.services.utils.pubsub;

import org.eclipse.ditto.model.base.acks.AcknowledgementRequest;
import org.eclipse.ditto.model.base.entity.id.EntityIdWithType;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.services.utils.pubsub.extractors.PubSubTopicExtractor;

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
     * @return the wrapped message to send to the publisher.
     */
    Object wrapForPublication(T message);

    /**
     * Wrap the message in an envelope to send to the publisher.
     *
     * @param message the message to publish.
     * @param ackRequests acknowledgement requests of the message.
     * @param entityId entity ID of the message.
     * @param dittoHeaders the Ditto headers of any weak acknowledgements to send back.
     * @param sender the sender of the message and the receiver of acknowledgements.
     * @return the wrapped message to send to the publisher.
     */
    Object wrapForPublicationWithAcks(
            T message,
            java.util.Set<AcknowledgementRequest> ackRequests,
            EntityIdWithType entityId,
            DittoHeaders dittoHeaders,
            ActorRef sender);

    /**
     * Publish a message.
     *
     * @param message the message to publish.
     * @param sender reply address for all subscribers who receive this message.
     */
    default void publish(final T message, final ActorRef sender) {
        getPublisher().tell(wrapForPublication(message), sender);
    }

    /**
     * Publish a message with acknowledgement requests.
     *
     * @param message the message to publish.
     * @param ackRequests acknowledgement requests of the message.
     * @param entityId entity ID of the message.
     * @param dittoHeaders the Ditto headers of any weak acknowledgements to send back.
     * @param sender the sender of the message and the receiver of acknowledgements.
     */
    default void publishWithAcks(
            T message,
            java.util.Set<AcknowledgementRequest> ackRequests,
            EntityIdWithType entityId,
            DittoHeaders dittoHeaders,
            ActorRef sender) {

        if (ackRequests.isEmpty()) {
            publish(message, sender);
        } else {
            final Object publishWithAcks =
                    wrapForPublicationWithAcks(message, ackRequests, entityId, dittoHeaders, sender);
            getPublisher().tell(publishWithAcks, sender);
        }
    }

    /**
     * Create a new interface of this distributed-pub with a new topic extractor.
     *
     * @param topicExtractor the previous topic extractor.
     * @return a new interface of this object.
     */
    default <S> DistributedPub<S> withTopicExtractor(final PubSubTopicExtractor<S> topicExtractor) {
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
    static <T> DistributedPub<T> of(final ActorRef pubSupervisor, final PubSubTopicExtractor<T> topicExtractor) {
        return new DistributedPubImpl<>(pubSupervisor, topicExtractor);
    }
}
