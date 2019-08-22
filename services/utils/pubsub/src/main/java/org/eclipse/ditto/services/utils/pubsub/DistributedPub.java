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
    Object wrapForPublication(final T message);

    /**
     * Publish a message.
     *
     * @param message the message to publish.
     * @param sender reply address for all subscribers who receive this message.
     */
    default void publish(T message, ActorRef sender) {
        getPublisher().tell(wrapForPublication(message), sender);
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
