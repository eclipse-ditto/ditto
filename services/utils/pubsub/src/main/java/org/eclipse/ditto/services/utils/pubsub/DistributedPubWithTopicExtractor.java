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

import org.eclipse.ditto.services.utils.pubsub.actors.Publisher;
import org.eclipse.ditto.services.utils.pubsub.extractors.PubSubTopicExtractor;

import akka.actor.ActorRef;

/**
 * Package-private implementation of {@link DistributedPub} for publication access from an {@link DistributedPub}
 * delegate.
 *
 * @param <T> type of messages.
 */
final class DistributedPubWithTopicExtractor<T> implements DistributedPub<T> {

    private final DistributedPub<?> delegate;
    private final PubSubTopicExtractor<T> topicExtractor;

    private DistributedPubWithTopicExtractor(final DistributedPub<?> delegate,
            final PubSubTopicExtractor<T> topicExtractor) {
        this.delegate = delegate;
        this.topicExtractor = topicExtractor;
    }

    static <T> DistributedPubWithTopicExtractor<T> of(final DistributedPub<?> delegate,
            final PubSubTopicExtractor<T> topicExtractor) {
        return new DistributedPubWithTopicExtractor<>(delegate, topicExtractor);
    }

    @Override
    public ActorRef getPublisher() {
        return delegate.getPublisher();
    }

    @Override
    public Object wrapForPublication(final T message) {
        return Publisher.Publish.of(topicExtractor.getTopics(message), message);
    }
}
